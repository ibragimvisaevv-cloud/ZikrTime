package com.zikrtime.app

import com.zikrtime.app.data.Net
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetTest {
    private fun withResponses(responses: List<String>, block: (String) -> Unit): List<String> {
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5000
        val executor = Executors.newSingleThreadExecutor()
        val requests = executor.submit<List<String>> {
            responses.map { response ->
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = buildList {
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            add(line)
                        }
                    }.joinToString("\n")
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    headers
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}/")
            return requests.get(5, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    @Test fun conditionalRequestsPreserveBodyAndValidator() {
        val ok = "HTTP/1.1 200 OK\r\nETag: \"v1\"\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"
        val unchanged = "HTTP/1.1 304 Not Modified\r\nConnection: close\r\n\r\n"
        val requests = withResponses(listOf(ok, unchanged, ok)) { url ->
            val first = Net.getIfChanged(url, null)!!
            assertEquals("\"v1\"", first.etag)
            assertEquals("{}", first.text)
            assertNull(Net.getIfChanged(url, first.etag))
            // After rejected JSON the caller retains its old validator and receives the body again.
            assertNotNull(Net.getIfChanged(url, "\"older\""))
        }
        assertFalse(requests[0].contains("If-None-Match", ignoreCase = true))
        assertTrue(requests[1].contains("If-None-Match: \"v1\"", ignoreCase = true))
        assertTrue(requests[2].contains("If-None-Match: \"older\"", ignoreCase = true))
    }

    @Test fun unsolicitedNotModifiedIsAnError() {
        withResponses(listOf("HTTP/1.1 304 Not Modified\r\nConnection: close\r\n\r\n")) { url ->
            assertTrue(runCatching { Net.getIfChanged(url, null) }.isFailure)
        }
    }
}
