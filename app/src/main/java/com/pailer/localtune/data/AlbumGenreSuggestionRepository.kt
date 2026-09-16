package com.pailer.localtune.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Genero + ano de lancamento sugeridos numa MESMA busca (pedido do usuario 15/09/2026: "podemos
// fazer essa busca junto com o genero" pro ano) - o iTunes ja devolve releaseDate junto do
// primaryGenreName no mesmo item, entao nao ha motivo pra bater na API 2x por album.
data class AlbumMetadataSuggestion(val genre: String?, val year: Int?)

class AlbumGenreSuggestionRepository {
    suspend fun suggestGenre(album: LocalAlbum, availableGenres: List<String>): String? =
        suggestMetadata(album, availableGenres).genre

    suspend fun suggestMetadata(album: LocalAlbum, availableGenres: List<String>): AlbumMetadataSuggestion =
        withContext(Dispatchers.IO) {
            val knownGenre = knownArtistHint(album.artist, availableGenres)

            val query = "${album.title} ${album.artist.substringBefore(",")}".trim()
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val json = getJson("$ITUNES_BASE/search?term=$encodedQuery&media=music&entity=album&limit=5&country=BR")
            val results = json?.optJSONArray("results")

            var year: Int? = null
            val genres = buildList {
                if (results != null) {
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        val genre = item.optString("primaryGenreName").trim()
                        if (genre.isNotBlank()) add(genre)
                        if (year == null) {
                            item.optString("releaseDate").take(4).toIntOrNull()?.let { year = it }
                        }
                    }
                }
            }

            val genre = knownGenre
                ?: genres.firstNotNullOfOrNull { g -> matchAvailableGenre(g, availableGenres) }
                ?: genres.firstOrNull()

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
        val mapped = ITUNES_GENRE_MAP[normalized]
        return availableGenres.firstOrNull { it.equals(genre, ignoreCase = true) }
            ?: mapped?.let { target -> availableGenres.firstOrNull { it.equals(target, ignoreCase = true) } }
            ?: availableGenres.firstOrNull { normalizeGenre(it) == normalized }
            ?: availableGenres.firstOrNull { option ->
                val optionKey = normalizeGenre(option)
                optionKey.contains(normalized) || normalized.contains(optionKey)
            }
    }

    private fun knownArtistHint(artist: String, availableGenres: List<String>): String? {
        val normalizedArtist = artist.lowercase()
        val target = KNOWN_ARTIST_HINTS.firstOrNull { (token, _) -> normalizedArtist.contains(token) }?.second
            ?: return null
        return availableGenres.firstOrNull { it.equals(target, ignoreCase = true) } ?: target
    }

    private fun normalizeGenre(value: String): String =
        value.lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private companion object {
        const val ITUNES_BASE = "https://itunes.apple.com"
        const val USER_AGENT = "PailerPlayer/0.1 (personal-local-android-app)"

        val ITUNES_GENRE_MAP = mapOf(
            "alternative" to "Alternative",
            "rock" to "Rock",
            "hip hop rap" to "Hip-Hop/Rap",
            "rap" to "Rap nacional",
            "brazilian" to "MPB",
            "mpb" to "MPB",
            "electronic" to "Eletronica / house",
            "dance" to "Eletronica / house",
            "metal" to "Metal",
            "nu metal" to "Nu Metal",
            "thrash metal" to "Thrash Metal",
            "heavy metal" to "Metal",
            "jazz" to "Jazz",
            "r b soul" to "Soul / funk",
            "soul" to "Soul / funk",
            "pop" to "Pop",
        )

        val KNOWN_ARTIST_HINTS = listOf(
            "radiohead" to "Alternative",
            "deftones" to "Metal",
            "korn" to "Nu Metal",
            "limp bizkit" to "Nu Metal",
            "slipknot" to "Nu Metal",
            "linkin park" to "Nu Metal",
            "system of a down" to "Nu Metal",
            "racionais" to "Rap nacional",
            "fbc" to "Rap nacional",
            "sabotage" to "Rap nacional",
            "facção central" to "Rap nacional",
            "faccao central" to "Rap nacional",
            "mac demarco" to "Indie / psicodelico",
            "tame impala" to "Indie / psicodelico",
            "joy division" to "Post-punk / cold wave",
            "the cure" to "Post-punk / cold wave",
            "daft punk" to "Eletronica / house",
            "aphex twin" to "Eletronica / house",
        )
    }
}
