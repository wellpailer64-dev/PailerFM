package com.pailer.localtune.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

class NewsBulletinRepository(private val context: Context) {
    suspend fun loadBulletins(): List<String> = withContext(Dispatchers.IO) {
        loadStories().mapIndexed { index, headline ->
            "Boletim ${index + 1}. ${headline.title}. Fonte: ${headline.source}."
        }
    }

    suspend fun loadStories(): List<NewsStory> = withContext(Dispatchers.IO) {
        // Busca os feeds em paralelo: sequencial custaria a soma dos timeouts (ate ~27s com
        // 6 feeds), enquanto em paralelo custa no maximo um timeout (4,5s) mesmo se todos
        // falharem ao mesmo tempo.
        val perFeed = coroutineScope {
            FEEDS.map { feed -> async { runCatching { fetchFeed(feed) }.getOrDefault(emptyList()) } }
                .awaitAll()
        }
        // Intercalado por feed (round-robin) em vez de flatMap+take: com flatMap, o primeiro feed
        // da lista sozinho já preenchia boa parte do NEWS_LIMIT antes dos outros entrarem, entao
        // o boletim saia dominado por uma unica categoria/fonte.
        interleave(perFeed)
            .distinctBy { it.title.lowercase() }
            .take(NEWS_LIMIT)
            .map { headline -> NewsStory(title = headline.title, source = headline.source, summary = headline.summary) }
    }

    private fun interleave(lists: List<List<Headline>>): List<Headline> {
        val result = mutableListOf<Headline>()
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

    private fun fetchFeed(feed: NewsFeed): List<Headline> {
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PailerPlayer/0.1")
        }

        return connection.inputStream.use { stream ->
            val parser = Xml.newPullParser().apply {
                setInput(stream, null)
            }
            parseHeadlines(parser, feed.source)
        }.also {
            connection.disconnect()
        }
    }

    private fun parseHeadlines(parser: XmlPullParser, source: String): List<Headline> {
        val headlines = mutableListOf<Headline>()
        var inItem = false
        var inTitle = false
        var inSummary = false
        var currentTitle = StringBuilder()
        var currentSummary = StringBuilder()

        while (parser.eventType != XmlPullParser.END_DOCUMENT && headlines.size < ITEMS_PER_FEED) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        inItem = true
                        currentTitle = StringBuilder()
                        currentSummary = StringBuilder()
                    }
                    if (inItem && tag == "title") inTitle = true
                    // RSS usa "description" (as vezes "content:encoded" tambem, ignorado aqui de
                    // proposito - normalmente e o corpo inteiro em HTML); Atom usa "summary".
                    if (inItem && (tag == "description" || tag == "summary")) inSummary = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem && inTitle) currentTitle.append(parser.text)
                    if (inItem && inSummary) currentSummary.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "title") inTitle = false
                    if (tag == "description" || tag == "summary") inSummary = false
                    if (tag == "item" || tag == "entry") {
                        inItem = false
                        val title = currentTitle.toString().cleanHeadline()
                        if (title.isNotBlank()) {
                            val summary = currentSummary.toString().cleanHeadline().limitChars(SUMMARY_MAX_CHARS)
                            headlines += Headline(title, source, summary)
                        }
                    }
                }
                else -> Unit
            }
            parser.next()
        }
        return headlines
    }

    private fun String.cleanHeadline(): String =
        replace(Regex("<[^>]*>"), " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "e")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            // Slug de nome de arquivo de imagem (ex.: "SI_jingle-campanha-eleitoral_site",
            // "ME_pepino-do-mar_site") grudado sem espaco no fim do titulo/resumo de algumas
            // materias da Super (historia/mundo-estranho) - visto em campo colado direto apos
            // a pontuacao final do titulo (06/09/2026). O <title> da RSS em si esta limpo (
            // conferido buscando o feed direto), entao a causa exata fica em aberto; removido
            // aqui como rede de seguranca porque o padrao (sigla maiuscula + slug com hifen +
            // "_site") e especifico o bastante pra nao arriscar cortar texto legitimo.
            .replace(Regex("[A-Z]{2,6}_[a-z0-9]+(?:-[a-z0-9]+)*_site\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.limitChars(limit: Int): String =
        if (length <= limit) this else substring(0, limit).trim().trimEnd(',', ';', ':') + "..."

    private data class NewsFeed(val source: String, val url: String)

    private data class Headline(val title: String, val source: String, val summary: String = "")

    private companion object {
        const val NETWORK_TIMEOUT_MS = 4500
        const val ITEMS_PER_FEED = 4
        // 2 por feed - sobe junto com o numero de FEEDS abaixo (11 feeds x 2 = 22) pra cada
        // categoria continuar garantindo espaco nas 2 primeiras rodadas do round-robin de
        // interleave() em vez de so as primeiras da lista conseguirem uma 2a materia (pedido do
        // usuario 04/09/2026, historico; reafirmado 10/09/2026 ao pedir mais variedade - ver
        // ADR-021).
        const val NEWS_LIMIT = 22
        const val SUMMARY_MAX_CHARS = 220

        // 11 feeds (10/09/2026, pedido do usuario: "pelo menos 10 variedades... tirando futebol,
        // mas curiosidades, games, tecnologia, historia, geopolitica, musica"). Os 8 originais
        // (04/09/2026: historia/bizarro/cientifico/mundo/Brasil/tecnologia) ganharam 3 categorias
        // novas - games, geopolitica, musica - todas testadas manualmente via curl antes de
        // entrar (mesmo criterio ja usado nos feeds antigos: HTTP 200 + <item>/<entry> reais no
        // corpo, nao pagina de erro/SPA vazia):
        // - Games: Canaltech Games (canaltech.com.br/rss/games/) - 50 itens reais confirmados.
        //   Olhar Digital ja e tecnologia geral, Canaltech cobre o angulo de jogos que faltava.
        // - Geopolitica: Veja Mundo (veja.abril.com.br/mundo/feed/) - 20 itens reais confirmados,
        //   cobertura internacional/geopolitica solida. Testado tambem DW Brasil (erro 500),
        //   CNN Brasil /mundo/feed (404) e Poder360 /mundo/feed (canal vazio, 0 itens) - nenhum
        //   dos 3 entrou.
        // - Musica: Rolling Stone Brasil (rollingstone.com.br/feed/) - 10 itens reais confirmados,
        //   editorial 100% musica/cultura pop (nao mistura com tecnologia como o feed de musica
        //   do Canaltech, que tambem existe e funciona mas foi preterido por redundancia).
        // NENHUM feed de futebol/esportes entrou de proposito (pedido explicito do usuario) - "g1
        // Brasil" e noticia geral, nao esportiva (G1 mantem esporte/futebol num site separado,
        // ge.globo.com, fora desta lista).
        val FEEDS = listOf(
            NewsFeed("g1 Mundo", "https://g1.globo.com/rss/g1/mundo"),
            NewsFeed("g1 Ciencia e saude", "https://g1.globo.com/rss/g1/ciencia-e-saude"),
            NewsFeed("g1 Brasil", "https://g1.globo.com/rss/g1/brasil"),
            NewsFeed("g1 Planeta Bizarro", "https://g1.globo.com/rss/g1/planeta-bizarro"),
            NewsFeed("Super Historia", "https://super.abril.com.br/historia/feed/"),
            NewsFeed("Super Mundo Estranho", "https://super.abril.com.br/mundo-estranho/feed/"),
            NewsFeed("Olhar Digital", "https://olhardigital.com.br/feed/"),
            NewsFeed("BBC Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml"),
            NewsFeed("Canaltech Games", "https://canaltech.com.br/rss/games/"),
            NewsFeed("Veja Mundo", "https://veja.abril.com.br/mundo/feed/"),
            NewsFeed("Rolling Stone Brasil", "https://rollingstone.com.br/feed/"),
        )
    }
}
