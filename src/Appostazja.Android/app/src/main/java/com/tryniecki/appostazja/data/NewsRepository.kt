package com.tryniecki.appostazja.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** A news entry from the Mapa Apostazji Atom feed. The HTML is as the feed gives it, links may be relative. */
data class NewsArticle(
    val id: String,
    val title: String,
    /** The article on the website; relative links in [contentHtml] resolve against it. */
    val url: String,
    val published: Instant?,
    val summaryHtml: String,
    val contentHtml: String,
)

/**
 * News read straight from the Mapa Apostazji Atom feed. Every load revalidates the feed with the website (it sends
 * an ETag, so an unchanged feed costs a 304); without a connection the last downloaded feed is used.
 */
class NewsRepository internal constructor(
    private val feedUrl: HttpUrl,
    private val client: OkHttpClient,
    private val now: () -> Instant = Instant::now,
    private val warn: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
) {
    constructor(feedUrl: String, cacheDirectory: File) : this(
        feedUrl.toHttpUrl(),
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(cacheDirectory, 1L * 1024 * 1024))
            .build(),
    )

    private val mutex = Mutex()
    @Volatile
    private var loaded: List<NewsArticle>? = null
    private var loadedAt: Instant = Instant.MIN

    /**
     * The articles, newest first. Kept in memory for a while, so moving between the news screens does not ask the
     * website again. Throws [IOException] when the feed can neither be downloaded nor read from the cache.
     */
    suspend fun articles(): List<NewsArticle> = mutex.withLock {
        loaded?.takeIf { Duration.between(loadedAt, now()) < MEMORY_TTL }?.let { return it }
        val articles = withContext(Dispatchers.IO) { download() }
        loaded = articles
        loadedAt = now()
        articles
    }

    suspend fun article(id: String): NewsArticle? = articles().firstOrNull { it.id == id }

    /** The articles last loaded, however old, for a screen to start from before [articles] answers. */
    val cachedArticles: List<NewsArticle>? get() = loaded

    fun cachedArticle(id: String): NewsArticle? = loaded?.firstOrNull { it.id == id }

    private fun download(): List<NewsArticle> {
        val fresh = try {
            // max-age=0 revalidates the cached copy (a conditional request); no-cache would skip it altogether.
            fetch(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
        } catch (error: IOException) {
            warn("News feed request failed, trying the cached copy", error)
            null
        }
        return fresh
            ?: fetch(CacheControl.FORCE_CACHE)
            ?: throw IOException("News feed unavailable")
    }

    /** Null when there is no usable response: an HTTP error, or nothing cached for [CacheControl.FORCE_CACHE]. */
    private fun fetch(cacheControl: CacheControl): List<NewsArticle>? {
        val request = Request.Builder().url(feedUrl).cacheControl(cacheControl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            // Read to the end before parsing: the cache keeps only a body that was read completely.
            return parseAtomFeed(response.body.bytes().inputStream(), feedUrl)
        }
    }

    internal companion object {
        const val TAG = "NewsRepository"
        val MEMORY_TTL: Duration = Duration.ofMinutes(15)
    }
}

private const val ATOM = "http://www.w3.org/2005/Atom"

/**
 * Reads an Atom feed. Entries without a title or a link are skipped, the rest are sorted newest first. Throws
 * [IOException] for a document that is not XML.
 */
internal fun parseAtomFeed(input: InputStream, feedUrl: HttpUrl): List<NewsArticle> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Not every parser knows the feature; Android's never loads external entities anyway.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    val document = try {
        factory.newDocumentBuilder().parse(input)
    } catch (error: org.xml.sax.SAXException) {
        throw IOException("News feed is not valid XML", error)
    }

    val entries = document.getElementsByTagNameNS(ATOM, "entry")
    return (0 until entries.length)
        .mapNotNull { index -> (entries.item(index) as Element).toArticle(feedUrl) }
        .sortedByDescending { it.published }
}

private fun Element.toArticle(feedUrl: HttpUrl): NewsArticle? {
    val title = child("title")?.textContent?.trim().orEmpty()
    val href = children("link")
        .firstOrNull { it.getAttribute("rel").let { rel -> rel.isEmpty() || rel == "alternate" } }
        ?.getAttribute("href")
    val url = href?.let { feedUrl.resolve(it.trim()) }?.toString()
    if (title.isEmpty() || url == null) return null

    val summary = child("summary")?.textContent?.trim().orEmpty()
    val content = child("content")?.textContent?.trim().orEmpty()
    return NewsArticle(
        id = child("id")?.textContent?.trim()?.ifEmpty { null } ?: url,
        title = title,
        url = url,
        published = (child("published") ?: child("updated"))?.textContent?.let(::parseAtomDate),
        summaryHtml = summary.ifEmpty { content },
        contentHtml = content.ifEmpty { summary },
    )
}

private fun Element.children(name: String): List<Element> {
    val nodes = getElementsByTagNameNS(ATOM, name)
    // Direct children only: an entry's own <title>, not one nested in its <source>.
    return (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.parentNode === this }
}

private fun Element.child(name: String): Element? = children(name).firstOrNull()

private fun parseAtomDate(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value.trim()).toInstant() }.getOrNull()

/**
 * Where a link in an article points: relative links resolve against the article's own page. Links that are not
 * web addresses (mailto:) are returned as they are.
 */
internal fun resolveArticleLink(article: NewsArticle, href: String): String =
    article.url.toHttpUrlOrNull()?.resolve(href.trim())?.toString() ?: href
