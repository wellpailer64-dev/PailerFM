package com.pailer.localtune.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
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
        val perFeed = FEEDS.map { feed ->
            runCatching { fetchFeed(feed) }.getOrDefault(emptyList())
        }
        // Intercalado por feed (round-robin) em vez de flatMap+take: com flatMap, o primeiro feed
        // da lista sozinho já preenchia boa parte do NEWS_LIMIT antes dos outros entrarem, entao
        // o boletim saia dominado por uma unica categoria.
        interleave(perFeed)
            .distinctBy { it.title.lowercase() }
            .take(NEWS_LIMIT)
            .map { headline -> NewsStory(title = headline.title, source = headline.source) }
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

        while (parser.eventType != XmlPullParser.END_DOCUMENT && headlines.size < ITEMS_PER_FEED) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") inItem = true
                    if (inItem && tag == "title") inTitle = true
                }
                XmlPullParser.TEXT -> {
                    if (inItem && inTitle) {
                        val title = parser.text.cleanHeadline()
                        if (title.isNotBlank()) headlines += Headline(title, source)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "title") inTitle = false
                    if (tag == "item" || tag == "entry") inItem = false
                }
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
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class NewsFeed(val source: String, val url: String)

    private data class Headline(val title: String, val source: String)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 4500
        const val ITEMS_PER_FEED = 4
        const val NEWS_LIMIT = 8

        val FEEDS = listOf(
            NewsFeed("g1 Tecnologia", "https://g1.globo.com/rss/g1/tecnologia"),
            NewsFeed("g1 Mundo", "https://g1.globo.com/rss/g1/mundo"),
            NewsFeed("g1 Ciencia e saude", "https://g1.globo.com/rss/g1/ciencia-e-saude"),
            NewsFeed("g1 Pop e Arte", "https://g1.globo.com/rss/g1/pop-arte"),
        )
    }
}
