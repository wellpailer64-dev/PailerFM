package com.pailer.localtune.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Genero + ano de lancamento sugeridos numa MESMA busca (pedido do usuario 15/09/2026: "podemos
// fazer essa busca junto com o genero" pro ano).
data class AlbumMetadataSuggestion(val genre: String?, val year: Int?)

// Fonte trocada de iTunes pra Deezer (pedido do usuario 15/09/2026: "geralmente ele manda umas
// sugestões meio fracas, teria outra opção?") - Deezer ja e usado sem chave nenhuma pra foto de
// artista (ver MusicLibraryRepository.searchArtistPhotoCandidates), entao reaproveita a mesma
// fonte sem exigir cadastro/token do usuario. A busca aceita filtros artist:"..." album:"..."
// (mais precisa que o "termo solto" do iTunes), e o recurso completo do album
// (GET /album/{id}) devolve genres.data[] (pode vir mais de um genero de verdade nativamente,
// nao so 1 campo) e release_date - so busca esse detalhe do PRIMEIRO resultado que realmente bate
// com artista E album (ver artistMatches/albumMatches), nunca de um resultado nao relacionado.
class AlbumGenreSuggestionRepository {
    suspend fun suggestGenre(album: LocalAlbum, availableGenres: List<String>): String? =
        suggestMetadata(album, availableGenres).genre

    // Pode devolver MAIS DE UM genero junto (pedido do usuario 15/09/2026: "pode ser mais de um
    // genero") - junta o(s) genero(s) nativos do album no Deezer (genres.data[]) com a curadoria
    // manual de KNOWN_ARTIST_HINTS quando existir pro artista, sempre batendo contra o NOSSO
    // catalogo (GENRE_TAG_CATALOG via availableGenres) e juntando no formato "A; B" (mesmo
    // separador de sempre, ver GENRE_TAG_SEPARATOR/joinGenreTags). So cai pro genero cru
    // (nao-canonico) se nada bater com o catalogo - ainda melhor que ficar sem nada.
    suspend fun suggestMetadata(album: LocalAlbum, availableGenres: List<String>): AlbumMetadataSuggestion =
        withContext(Dispatchers.IO) {
            val knownGenres = knownArtistHint(album.artist, availableGenres)

            // ACHADO AO VIVO 15/09/2026: a sintaxe de filtro avancado do Deezer
            // (artist:".." album:"..") devolve ZERO resultados quando combinada (testado direto
            // na API) - so texto solto "titulo artista" funciona (mesmo formato usado antes pro
            // iTunes). O filtro artistMatches/albumMatches abaixo continua garantindo que so um
            // resultado de verdade relacionado contribui genero/ano.
            val primaryArtist = album.artist.substringBefore(",").trim()
            val query = "${album.title} $primaryArtist".trim()
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchJson = getJson("$DEEZER_BASE/search/album?q=$encodedQuery&limit=5")
            val results = searchJson?.optJSONArray("data")

            val albumKey = normalizeGenre(album.title)
            val artistKey = normalizeGenre(primaryArtist)

            var matchedAlbumId: Long? = null
            if (results != null) {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val itemArtistKey = normalizeGenre(item.optJSONObject("artist")?.optString("name").orEmpty())
                    val itemAlbumKey = normalizeGenre(item.optString("title"))
                    val artistMatches = artistKey.isNotBlank() &&
                        (itemArtistKey.contains(artistKey) || artistKey.contains(itemArtistKey))
                    val albumMatches = albumKey.isNotBlank() &&
                        (itemAlbumKey.contains(albumKey) || albumKey.contains(itemAlbumKey))
                    if (artistMatches && albumMatches) {
                        matchedAlbumId = item.optLong("id").takeIf { it > 0 }
                        break
                    }
                }
            }

            var year: Int? = null
            val rawGenres = mutableListOf<String>()
            if (matchedAlbumId != null) {
                val detail = getJson("$DEEZER_BASE/album/$matchedAlbumId")
                if (detail != null) {
                    detail.optString("release_date").take(4).toIntOrNull()?.let { year = it }
                    val genresArray = detail.optJSONObject("genres")?.optJSONArray("data")
                    if (genresArray != null) {
                        for (i in 0 until genresArray.length()) {
                            genresArray.optJSONObject(i)?.optString("name")?.trim()
                                ?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
                                ?.let { rawGenres.add(it) }
                        }
                    }
                }
            }

            val matchedGenres = rawGenres.mapNotNull { g -> matchAvailableGenre(g, availableGenres) }
            val combinedGenres = (knownGenres + matchedGenres).distinct()
            val genre = if (combinedGenres.isNotEmpty()) {
                joinGenreTags(combinedGenres)
            } else {
                rawGenres.firstOrNull()
            }

            AlbumMetadataSuggestion(genre = genre, year = year)
        }

    private fun getJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return runCatching {
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun matchAvailableGenre(genre: String, availableGenres: List<String>): String? {
        val normalized = normalizeGenre(genre)
        val mapped = DEEZER_GENRE_MAP[normalized]
        return availableGenres.firstOrNull { it.equals(genre, ignoreCase = true) }
            ?: mapped?.let { target -> availableGenres.firstOrNull { it.equals(target, ignoreCase = true) } }
            ?: availableGenres.firstOrNull { normalizeGenre(it) == normalized }
            ?: availableGenres.firstOrNull { option ->
                val optionKey = normalizeGenre(option)
                optionKey.contains(normalized) || normalized.contains(optionKey)
            }
    }

    // Curadoria manual por artista (mais confiavel que a fonte externa pra esses casos especificos,
    // ex.: Deftones e Metal E Nu Metal ao mesmo tempo - achado ao vivo 15/09/2026, feedback do
    // usuario). Cada artista pode apontar pra mais de uma tag do catalogo de uma vez.
    private fun knownArtistHint(artist: String, availableGenres: List<String>): List<String> {
        val normalizedArtist = artist.lowercase()
        val targets = KNOWN_ARTIST_HINTS.firstOrNull { (token, _) -> normalizedArtist.contains(token) }?.second
            ?: return emptyList()
        return targets.map { target -> availableGenres.firstOrNull { it.equals(target, ignoreCase = true) } ?: target }
    }

    private fun normalizeGenre(value: String): String =
        value.lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private companion object {
        const val DEEZER_BASE = "https://api.deezer.com"
        const val USER_AGENT = "PailerPlayer/0.1 (personal-local-android-app)"

        // Taxonomia de generos "de topo" do proprio Deezer (~20 categorias amplas) mapeada pro
        // nosso catalogo (GENRE_TAG_CATALOG) so onde o nome difere - muita coisa (Rock, Metal,
        // Pop, Jazz, Alternative, Electro, Classical, Reggae...) ja bate direto por igualdade
        // exata ou pelo fallback de "contains" em matchAvailableGenre, sem precisar de entrada
        // aqui.
        val DEEZER_GENRE_MAP = mapOf(
            "rap hip hop" to "Hip-Hop",
            "reggae dancehall" to "Reggae",
            "soundtracks" to "Soundtrack",
            "films games" to "Soundtrack",
            "latin music" to "Latin",
            "african music" to "World Music",
            "asian music" to "World Music",
            "indie pop rock" to "Indie",
            "r and b" to "Soul / funk",
        )

        val KNOWN_ARTIST_HINTS = listOf(
            "radiohead" to listOf("Alternative"),
            "deftones" to listOf("Metal", "Nu Metal"),
            "korn" to listOf("Nu Metal"),
            "limp bizkit" to listOf("Nu Metal"),
            "slipknot" to listOf("Nu Metal"),
            "linkin park" to listOf("Nu Metal"),
            "system of a down" to listOf("Nu Metal"),
            "racionais" to listOf("Rap nacional"),
            "fbc" to listOf("Rap nacional"),
            "sabotage" to listOf("Rap nacional"),
            "facção central" to listOf("Rap nacional"),
            "faccao central" to listOf("Rap nacional"),
            "mac demarco" to listOf("Indie / psicodelico"),
            "tame impala" to listOf("Indie / psicodelico"),
            "joy division" to listOf("Post-punk / cold wave"),
            "the cure" to listOf("Post-punk / cold wave"),
            "daft punk" to listOf("Eletronica / house"),
            "aphex twin" to listOf("Eletronica / house"),
        )
    }
}
