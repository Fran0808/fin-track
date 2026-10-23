package com.financemanager.listener

import com.financemanager.listener.network.InsecureTransportException
import com.financemanager.listener.network.TransportPolicy
import com.financemanager.listener.network.ApiClient
import com.financemanager.listener.worker.SyncFailure
import com.financemanager.listener.worker.syncProblem
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import java.io.IOException

class TransportPolicyTest {
    @Test fun productionRejectsAnUntrustedTlsCertificate() {
        val directory = Files.createTempDirectory("fintrack-synthetic-tls-")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val password = "synthetic-keystore-password"
            val store = directory.resolve("synthetic.p12")
            val keytool = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool")
            val process = ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "synthetic",
                "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost", "-validity", "1",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", password, "-keypass", password, "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.txt").toFile()).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor()
                fail("Synthetic TLS certificate generation timed out")
            }
            assertEquals(0, process.exitValue())
            val keys = KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(store).use { load(it, password.toCharArray()) }
            }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keys, password.toCharArray())
            }
            val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
            (tls.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket).use { server ->
                server.soTimeout = 3_000
                val handshake = executor.submit {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 3_000
                        try { socket.startHandshake() } catch (_: IOException) { /* Client rejects the synthetic certificate. */ }
                    }
                }
                assertThrows(SSLException::class.java) {
                    TransportPolicy.client(false).newCall(Request.Builder()
                        .url("https://127.0.0.1:${server.localPort}/")
                        .header("X-Device-Token", "synthetic-device-token").build()).execute().close()
                }
                handshake.get(5, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test fun actualBuildVariantEnforcesItsTransportPolicyEvenWithACachedService() {
        try {
            assertNotNull(ApiClient.getService("https://example.test/"))
            if (BuildConfig.DEBUG) assertNotNull(ApiClient.getService("http://127.0.0.1:8080/"))
            else assertThrows(InsecureTransportException::class.java) {
                ApiClient.getService("http://127.0.0.1:8080/")
            }
        } finally { ApiClient.resetService() }
    }
    @Test fun productionRequiresHttpsWhileDevelopmentSupportsLocalHttp() {
        assertEquals("https://example.test/", TransportPolicy.validate("https://example.test", false))
        assertThrows(InsecureTransportException::class.java) {
            TransportPolicy.validate("http://192.0.2.10:8080", false)
        }
        assertEquals("http://192.0.2.10:8080/", TransportPolicy.validate("http://192.0.2.10:8080", true))
        for (unsafe in listOf("file:///tmp", "https://user:synthetic@example.test", "https://example.test?token=synthetic")) {
            assertThrows(Exception::class.java) { TransportPolicy.validate(unsafe, false) }
        }
    }

    @Test fun productionBlocksHttpBeforeOpeningASocketOrSendingCredentials() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 300
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/")
                .header("X-Device-Token", "synthetic-device-token").build()
            assertThrows(InsecureTransportException::class.java) {
                TransportPolicy.client(false).newCall(request).execute().close()
            }
            assertThrows(SocketTimeoutException::class.java) { server.accept().close() }
        }
    }

    @Test fun developmentCanReachALoopbackHttpServer() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 2_000
                val received = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val reader = socket.getInputStream().bufferedReader()
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines.add(line)
                        }
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
                        lines.joinToString("\n")
                    }
                }
                TransportPolicy.client(true).newCall(Request.Builder()
                    .url("http://127.0.0.1:${server.localPort}/")
                    .header("X-Device-Token", "synthetic-device-token").build()).execute().use {
                    assertEquals(200, it.code())
                    assertEquals("OK", it.body()?.string())
                }
                assertTrue(received.get(3, TimeUnit.SECONDS).contains("X-Device-Token: synthetic-device-token"))
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun redirectsAreReturnedInsteadOfForwardingDeviceTokens() {
        for (allowHttp in listOf(false, true)) {
            val client = TransportPolicy.client(allowHttp)
            assertFalse(client.followRedirects())
            assertFalse(client.followSslRedirects())
            var calls = 0
            val fake = client.newBuilder().addInterceptor { chain ->
                calls++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302)
                    .message("Synthetic redirect").header("Location", "http://other.example.test/")
                    .body(ResponseBody.create(null, "")).build()
            }.build()
            fake.newCall(Request.Builder().url("https://example.test/")
                .header("X-Device-Token", "synthetic-device-token").build()).execute().use {
                assertEquals(302, it.code())
            }
            assertEquals(1, calls)
        }
    }

    @Test fun insecureDestinationHasASanitizedActionableDiagnostic() {
        val problem = syncProblem(InsecureTransportException())
        assertEquals(SyncFailure.HTTPS_REQUIRED, problem.failure)
        assertTrue(problem.message.contains("HTTPS"))
        assertFalse(problem.message.contains("synthetic-device-token"))
    }
}
