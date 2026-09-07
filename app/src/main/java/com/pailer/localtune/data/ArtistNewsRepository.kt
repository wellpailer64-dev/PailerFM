package com.pailer.localtune.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.format.DateTimeFormatter

data class ArtistNewsCard(
    val artistName: String,
    val headline: String,
    val source: String,
    val link: String,
    val imageUrl: String,
    // Epoch millis do <pubDate> do RSS, 0 se o item nao trouxe data (ver parseItems) - a UI so
    // mostra a data quando esse valor e > 0 (ArtistNewsCardView).
    val publishedAtMillis: Long = 0L,
)

// Noticias/curiosidades sobre os artistas favoritados do usuario, pra sessao "arraste pra
// revelar" da Home (ver LocalTuneApp.kt) - pedido do usuario 06/09/2026. Reusa o mesmo padrao ja
// validado em NewsBulletinRepository (HttpURLConnection + XmlPullParser puro, sem OkHttp/Retrofit
// no projeto), consultando o Bing News RSS POR NOME DE ARTISTA (busca por termo sem precisar de
// chave de API). Bing em vez de Google News: o Google News RSS nao traz imagem nenhuma por
// materia (so titulo/link/fonte) e o link dele e um redirect ofuscado que precisa de JS pra
// resolver a URL de verdade - o Bing ja devolve a imagem de capa escolhida pela materia
// (News:Image) E a URL real do artigo direto no parametro "url" do link, sem precisar seguir
// redirect nenhum.
class ArtistNewsRepository(private val context: Context) {
    suspend fun loadNewsForArtists(artistNames: List<String>): List<ArtistNewsCard> = withContext(Dispatchers.IO) {
        val names = artistNames.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return@withContext emptyList()
        // Um feed por artista, em paralelo - sequencial custaria a soma dos timeouts.
        val perArtist = coroutineScope {
            names.map { name -> async { runCatching { fetchArtistNews(name) }.getOrDefault(emptyList()) } }
                .awaitAll()
        }
        // Round-robin (1 de cada artista por vez) em vez de flatMap+take, senao o primeiro
        // artista da lista sozinho preencheria o limite antes dos outros entrarem.
        val distinct = interleave(perArtist).distinctBy { it.headline.lowercase() }
        // Prioriza materias com imagem de capa de verdade (pedido do usuario) - nem toda materia
        // tem uma no Bing, entao ITEMS_PER_ARTIST busca mais candidatas por artista do que o
        // NEWS_LIMIT final, e aqui so completa com as sem imagem se sobrar espaco.
        val picked = (distinct.filter { it.imageUrl.isNotBlank() } + distinct.filter { it.imageUrl.isBlank() })
            .take(NEWS_LIMIT)
        // Traduz so as manchetes escolhidas (nao as descartadas) - pedido do usuario 07/09/2026:
        // artista estrangeiro geralmente tem materia em ingles/outra lingua. Em paralelo, cada
        // chamada e independente.
        coroutineScope {
            picked.map { card -> async { card.copy(headline = translateToPortuguese(card.headline)) } }
                .awaitAll()
        }
    }

    // Traducao automatica pro portugues via endpoint publico do Google Translate (mesmo estilo das
    // outras integracoes do app: HttpURLConnection puro, sem SDK/chave de API - ver
    // NewsBulletinRepository). So traduz se a lingua detectada NAO for portugues (senao devolve o
    // texto original, evitando reescrever uma manchete que ja esta certa por causa de uma
    // retraducao imperfeita).
    private fun translateToPortuguese(text: String): String {
        if (text.isBlank()) return text
        return runCatching {
            val query = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt&dt=t&q=$query"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (PailerPlayer/0.1)")
            }
            val body = connection.inputStream.use { it.bufferedReader().readText() }
            connection.disconnect()
            val json = JSONArray(body)
            val detectedLang = json.optString(2, "")
            if (detectedLang.startsWith("pt")) return@runCatching text
            val segments = json.getJSONArray(0)
            val translated = StringBuilder()
            for (i in 0 until segments.length()) {
                translated.append(segments.getJSONArray(i).getString(0))
            }
            translated.toString().trim().ifBlank { text }
        }.getOrDefault(text)
    }

    private fun fetchArtistNews(artistName: String): List<ArtistNewsCard> {
        val query = URLEncoder.encode(artistName, "UTF-8")
        val url = "https://www.bing.com/news/search?q=$query&format=RSS&setmkt=pt-BR"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (PailerPlayer/0.1)")
        }

        return connection.inputStream.use { stream ->
            val parser = Xml.newPullParser().apply {
                setInput(stream, null)
            }
            parseItems(parser, artistName)
        }.also {
            connection.disconnect()
        }
    }

    // Bing News RSS: <item><title>Manchete</title>
    // <link>bing.com/news/apiclick.aspx?...&url=<URL-REAL-codificada>&...</link>
    // <News:Source>Fonte</News:Source><News:Image>capa.jpg</News:Image>...</item> - sem namespace
    // processing (mesmo padrao de NewsBulletinRepository), entao os nomes de tag vem com o prefixo
    // literal "News:" mesmo, comparado em minusculo abaixo.
    private fun parseItems(parser: XmlPullParser, artistName: String): List<ArtistNewsCard> {
        val items = mutableListOf<ArtistNewsCard>()
        var inItem = false
        var inTitle = false
        var inLink = false
        var inSource = false
        var inImage = false
        var inPubDate = false
        var currentTitle = StringBuilder()
        var currentLink = StringBuilder()
        var currentSource = StringBuilder()
        var currentImage = StringBuilder()
        var currentPubDate = StringBuilder()

        while (parser.eventType != XmlPullParser.END_DOCUMENT && items.size < ITEMS_PER_ARTIST) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "item") {
                        inItem = true
                        currentTitle = StringBuilder()
                        currentLink = StringBuilder()
                        currentSource = StringBuilder()
                        currentImage = StringBuilder()
                        currentPubDate = StringBuilder()
                    }
                    if (inItem && tag == "title") inTitle = true
                    if (inItem && tag == "link") inLink = true
                    if (inItem && (tag == "news:source" || tag == "source")) inSource = true
                    if (inItem && (tag == "news:image" || tag == "image")) inImage = true
                    if (inItem && tag == "pubdate") inPubDate = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem && inTitle) currentTitle.append(parser.text)
                    if (inItem && inLink) currentLink.append(parser.text)
                    if (inItem && inSource) currentSource.append(parser.text)
                    if (inItem && inImage) currentImage.append(parser.text)
                    if (inItem && inPubDate) currentPubDate.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "title") inTitle = false
                    if (tag == "link") inLink = false
                    if (tag == "news:source" || tag == "source") inSource = false
                    if (tag == "news:image" || tag == "image") inImage = false
                    if (tag == "pubdate") inPubDate = false
                    if (tag == "item") {
                        inItem = false
                        val title = currentTitle.toString().cleanText()
                        val source = currentSource.toString().cleanText()
                        val publishedAtMillis = parsePubDate(currentPubDate.toString().cleanText())
                        // News:Image do Bing vem em "http://" puro - apps com targetSdk 28+
                        // bloqueiam trafego HTTP sem criptografia por padrao (sem
                        // usesCleartextTraffic no manifesto), entao a imagem falhava silenciosa
                        // e caia no placeholder. O mesmo host serve https:// no mesmo path.
                        // Sem parametro de tamanho, o Bing devolve uma miniatura de 100x100 (baixa
                        // qualidade demais pra ocupar a largura toda do card) - w/qlt pedem a
                        // mesma imagem numa resolucao maior, mesmo endpoint.
                        val image = currentImage.toString().cleanText()
                            .replaceFirst("http://", "https://")
                            .let { if (it.isBlank()) it else "$it&w=720&qlt=90" }
                        val rawLink = currentLink.toString().cleanText()
                        // O link de verdade da materia vem no parametro "url" do redirect do Bing
                        // (apiclick.aspx?...&url=<real>&...) - Uri.getQueryParameter ja devolve
                        // decodificado, sem precisar seguir redirect nenhum.
                        val link = runCatching { Uri.parse(rawLink).getQueryParameter("url") }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: rawLink
                        if (title.isNotBlank() && link.isNotBlank()) {
                            items += ArtistNewsCard(
                                artistName = artistName,
                                headline = title,
                                source = source.ifBlank { "Bing Noticias" },
                                link = link,
                                imageUrl = image,
                                publishedAtMillis = publishedAtMillis,
                            )
                        }
                    }
                }
                else -> Unit
            }
            parser.next()
        }
        return items
    }

    // <pubDate> do RSS vem no formato RFC 822 ("Wed, 06 Sep 2026 10:00:00 GMT"), o mesmo que
    // DateTimeFormatter.RFC_1123_DATE_TIME entende direto - sem SimpleDateFormat manual. Devolve
    // 0 se o item nao trouxe pubDate ou o formato vier fora do esperado (a UI simplesmente omite
    // a data nesse caso, ver ArtistNewsCardView).
    private fun parsePubDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        return runCatching {
            java.time.ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun interleave(lists: List<List<ArtistNewsCard>>): List<ArtistNewsCard> {
        val result = mutableListOf<ArtistNewsCard>()
        var index = 0
        while (result.size < lists.sumOf { it.size }) {
            var addedAny = false
            for (list in lists) {
                if (index < list.size) {
                    result += list[index]
                    addedAny = true
                }
            }
            if (!addedAny) break
            index++
        }
        return result
    }

    private fun String.cleanText(): String =
        replace(Regex("<[^>]*>"), " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        const val NETWORK_TIMEOUT_MS = 4500
        const val ITEMS_PER_ARTIST = 5
        const val NEWS_LIMIT = 10
    }
}
