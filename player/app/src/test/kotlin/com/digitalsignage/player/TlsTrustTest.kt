package com.digitalsignage.player

import com.digitalsignage.player.data.TlsTrust
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regressão: Android 5.0–7.0 não conhece a ISRG Root X1 (Let's Encrypt) e não conseguiria falar com o site de
 * downloads. O Player acrescenta essa raiz às do sistema, sem aceitar mais nada além dela.
 */
class TlsTrustTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    private fun serve(certificates: HandshakeCertificates) {
        server.useHttps(certificates.sslSocketFactory(), false)
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
    }

    private fun serverCertificates(root: HeldCertificate, name: String = "localhost"): HandshakeCertificates {
        val leaf = HeldCertificate.Builder().commonName(name).addSubjectAlternativeName("localhost").signedBy(root).build()
        return HandshakeCertificates.Builder().heldCertificate(leaf, root.certificate).build()
    }

    private fun client(extraRoots: List<java.security.cert.X509Certificate>?): OkHttpClient {
        val builder = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
        return (if (extraRoots == null) builder else TlsTrust.apply(builder, extraRoots)).build()
    }

    private fun get(client: OkHttpClient): String =
        client.newCall(Request.Builder().url(server.url("/").newBuilder().host("localhost").build()).build()).execute().use { it.body!!.string() }

    @Test
    fun `the bundled extra root is the real ISRG Root X1`() {
        val root = TlsTrust.extraRoots().single()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(root.encoded).joinToString(":") { "%02X".format(it) }

        assertTrue(root.subjectX500Principal.name.contains("ISRG Root X1"))
        assertEquals("96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6", sha256)
    }

    @Test
    fun `a server whose chain ends only in the extra root is accepted`() {
        val root = HeldCertificate.Builder().certificateAuthority(0).commonName("Raiz de teste").build()
        serve(serverCertificates(root))

        // Sem a âncora extra o cliente padrão recusa (é o que acontece no Android antigo com Let's Encrypt)...
        try {
            get(client(null))
            fail("o cliente padrão não deveria confiar na raiz de teste")
        } catch (expected: SSLHandshakeException) {
            // ok
        }

        // ...e com ela a conexão funciona.
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", get(client(listOf(root.certificate))))
    }

    /** Segurança: acrescentar uma raiz não pode abrir a porta para certificados de outra autoridade. */
    @Test
    fun `a server from an unrelated authority is still rejected`() {
        val trusted = HeldCertificate.Builder().certificateAuthority(0).commonName("Raiz confiável").build()
        val stranger = HeldCertificate.Builder().certificateAuthority(0).commonName("Raiz desconhecida").build()
        serve(serverCertificates(stranger))

        try {
            get(client(listOf(trusted.certificate)))
            fail("um certificado de autoridade desconhecida foi aceito")
        } catch (expected: SSLHandshakeException) {
            // ok
        }
    }

    @Test
    fun `a self signed server certificate is rejected even with the extra root present`() {
        val selfSigned = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        serve(HandshakeCertificates.Builder().heldCertificate(selfSigned).build())

        try {
            get(client(TlsTrust.extraRoots()))
            fail("certificado autoassinado foi aceito")
        } catch (expected: SSLHandshakeException) {
            // ok
        }
    }

    @Test
    fun `the merged trust manager keeps the system anchors and adds the extra one`() {
        val manager = TlsTrust.trustManager()
        val issuers = manager.acceptedIssuers.map { it.subjectX500Principal.name }

        assertTrue("as raízes do sistema precisam continuar valendo", manager.acceptedIssuers.size > 1)
        assertTrue(issuers.any { it.contains("ISRG Root X1") })
        assertFalse(issuers.isEmpty())
    }

}
