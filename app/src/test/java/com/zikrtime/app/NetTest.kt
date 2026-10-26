package com.zikrtime.app

import com.sun.net.httpserver.HttpServer
import com.zikrtime.app.data.Net
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

/** Validators are returned to the parser, never written to Store on an IO thread. */
class NetTest {
    @Test fun conditionalRequestsPreserveBodyAndValidator() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestHeaders.getFirst("If-None-Match") == "\"v1\"") {
                exchange.sendResponseHeaders(304, -1)
            } else {
                val body = """{"places":[]}""".toByteArray()
                exchange.responseHeaders.add("ETag", "\"v1\"")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            val first = Net.getIfChanged(url, null)!!
            assertEquals("\"v1\"", first.etag)
            assertEquals("""{"places":[]}""", first.text)
            assertNull(Net.getIfChanged(url, first.etag))
            assertNotNull(Net.getIfChanged(url, "\"older\""))
        } finally { server.stop(0) }
    }
    @Test fun unsolicitedNotModifiedIsAnError() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { it.sendResponseHeaders(304, -1); it.close() }
        server.start()
        try {
            assertTrue(runCatching { Net.getIfChanged("http://127.0.0.1:${server.address.port}/", null) }.isFailure)
        } finally { server.stop(0) }
    }
}
