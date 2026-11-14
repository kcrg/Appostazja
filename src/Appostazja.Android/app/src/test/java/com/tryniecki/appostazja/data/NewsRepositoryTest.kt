package com.tryniecki.appostazja.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Instant

class NewsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val feedBytes = javaClass.getResourceAsStream("/news/aktualnosci.atom")!!.readBytes()
    private val feedUrl = "https://mapaapostazji.pl/aktualnosci.atom".toHttpUrl()

    @Test
    fun `the website's feed reads as articles, newest first`() {
        val articles = parseAtomFeed(feedBytes.inputStream(), feedUrl)

        assertEquals(listOf("urn:mapaapostazji:news:pinezki", "urn:mapaapostazji:news:start"), articles.map { it.id })
        val newest = articles.first()
        assertEquals("Pinezki na mapie to teraz wykresy!", newest.title)
        assertEquals("https://mapaapostazji.pl/aktualnosci/pinezki.html", newest.url)
        assertEquals(Instant.parse("2026-10-21T20:37:00Z"), newest.published)
        assertEquals(true, newest.summaryHtml.startsWith("<p>Mapa otrzymała"))
        assertEquals(true, newest.contentHtml.length > newest.summaryHtml.length)
    }

    @Test
    fun `entries without a link are skipped and missing parts are filled from the rest`() {
        val feed = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Bez linku</title><id>a</id></entry>
              <entry>
                <title>Tylko treść</title>
                <link href="wpisy/b.html"/>
                <updated>2026-01-02T10:00:00+01:00</updated>
                <content type="html">&lt;p&gt;Treść&lt;/p&gt;</content>
                <source><title>Inny feed</title></source>
              </entry>
              <entry><title>Bez daty</title><link rel="alternate" href="https://example.com/c"/><link rel="self" href="x"/></entry>
            </feed>
        """.trimIndent()

        val articles = parseAtomFeed(feed.byteInputStream(), feedUrl)

        assertEquals(listOf("Tylko treść", "Bez daty"), articles.map { it.title })
        val onlyContent = articles.first()
        assertEquals("https://mapaapostazji.pl/wpisy/b.html", onlyContent.id)
        assertEquals(Instant.parse("2026-01-02T09:00:00Z"), onlyContent.published)
        assertEquals("<p>Treść</p>", onlyContent.summaryHtml)
        assertNull(articles.last().published)
        assertEquals("https://example.com/c", articles.last().url)
    }

    @Test
    fun `links in an article resolve against its own page`() {
        val article = parseAtomFeed(feedBytes.inputStream(), feedUrl).first()

        assertEquals("https://mapaapostazji.pl/aktualnosci.xml", resolveArticleLink(article, "../aktualnosci.xml"))
        assertEquals("https://example.com/", resolveArticleLink(article, "https://example.com/"))
        assertEquals("mailto:kontakt@example.com", resolveArticleLink(article, "mailto:kontakt@example.com"))
    }

    @Test
    fun `an unchanged feed is revalidated and a lost connection falls back to the last download`() = runBlocking {
        var clock = Instant.parse("2026-11-15T12:00:00Z")
        val server = FeedServer(feedBytes)
        val repository = NewsRepository(server.url, client(), now = { clock }, warn = { _, _ -> })

        assertEquals(2, repository.articles().size)
        assertEquals(1, server.requests)

        // Within the memory window the screens share the articles without asking again.
        clock = clock.plusSeconds(60)
        repository.articles()
        assertEquals(1, server.requests)

        clock = clock.plusSeconds(3600)
        assertEquals(2, repository.articles().size)
        assertEquals(listOf(null, "\"v1\""), server.ifNoneMatch)

        server.stop()
        clock = clock.plusSeconds(3600)
        assertEquals("Pinezki na mapie to teraz wykresy!", repository.articles().first().title)
    }

    @Test
    fun `without a connection or a cached copy the load fails, and a later one can succeed`() = runBlocking {
        val server = FeedServer(feedBytes, status = 503)
        val repository = NewsRepository(server.url, client(), warn = { _, _ -> })

        assertThrows(IOException::class.java) { runBlocking { repository.articles() } }

        server.status = 200
        assertEquals(2, repository.articles().size)
        server.stop()
    }

    private fun client() = OkHttpClient.Builder().cache(Cache(folder.newFolder("http"), 1024 * 1024)).build()

    @After
    fun stopServers() = servers.forEach { it.stop() }

    private val servers = mutableListOf<FeedServer>()

    /** A real HTTP server, so the OkHttp cache takes part as it does on the phone. */
    private inner class FeedServer(body: ByteArray, var status: Int = 200) {
        var requests = 0
        val ifNoneMatch = mutableListOf<String?>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/aktualnosci.atom") { exchange ->
                requests++
                val tag = exchange.requestHeaders.getFirst("If-None-Match")
                ifNoneMatch += tag
                exchange.responseHeaders.add("ETag", "\"v1\"")
                exchange.responseHeaders.add("Last-Modified", "Sat, 14 Nov 2026 19:33:36 GMT")
                when {
                    status != 200 -> exchange.sendResponseHeaders(status, -1)
                    tag == "\"v1\"" -> exchange.sendResponseHeaders(304, -1)
                    else -> {
                        exchange.responseHeaders.add("Content-Type", "application/atom+xml")
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                }
                exchange.close()
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}/aktualnosci.atom".toHttpUrl()

        init {
            servers += this
        }

        fun stop() = server.stop(0)
    }
}
