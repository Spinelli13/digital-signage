package com.digitalsignage.player

import com.digitalsignage.player.update.ApkDownloader
import com.digitalsignage.player.update.Release
import com.digitalsignage.player.update.ReleaseJson
import com.digitalsignage.player.update.UpdateCheck
import com.digitalsignage.player.update.UpdateChecker
import com.digitalsignage.player.update.UpdateIntegrityException
import com.digitalsignage.player.update.UpdateWindow
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val apkBytes = "conteudo-do-apk-novo".toByteArray()

    private fun manifest(
        versionCode: Long = 4,
        minSdk: Int = 21,
        sha: String = sha256(apkBytes),
        size: Long = apkBytes.size.toLong(),
    ) = """{"versionCode":$versionCode,"versionName":"0.$versionCode.0","apkUrl":"player-$versionCode.apk","sha256":"$sha","size":$size,"minSdk":$minSdk,"notes":"Correções"}"""

    private fun manifestUrl() = server.url("/latest.json").toString()

    // --- manifesto ---

    @Test
    fun `resolves a relative apk url next to the manifest`() {
        val release = ReleaseJson.parse(manifest(), "https://downloads.example/latest.json")

        assertEquals("https://downloads.example/player-4.apk", release.apkUrl)
        assertEquals("0.4.0", release.versionName)
        assertEquals("Correções", release.notes)
    }

    @Test
    fun `keeps an absolute apk url`() {
        val raw = manifest().replace("player-4.apk", "https://cdn.example/x.apk")

        assertEquals("https://cdn.example/x.apk", ReleaseJson.parse(raw, "https://downloads.example/latest.json").apkUrl)
    }

    @Test
    fun `rejects manifests without a valid checksum, version or url`() {
        val broken = listOf(
            manifest(sha = "123"),
            manifest(versionCode = 0),
            """{"versionCode":4,"sha256":"${sha256(apkBytes)}"}""",
            "isto não é json",
        )
        for (bad in broken) {
            try {
                ReleaseJson.parse(bad, "https://x/latest.json")
                fail("deveria rejeitar: $bad")
            } catch (expected: IllegalArgumentException) {
                // esperado
            }
        }
    }

    // --- verificação de versão ---

    @Test
    fun `offers a newer version`() = runBlocking {
        server.enqueue(MockResponse().setBody(manifest(versionCode = 4)))

        val result = UpdateChecker(deviceSdk = 34).check(manifestUrl(), currentVersionCode = 3)

        assertTrue(result is UpdateCheck.Available)
        assertEquals(4L, (result as UpdateCheck.Available).release.versionCode)
    }

    /** Regressão de segurança: nunca "atualizar" para a mesma versão ou para uma mais antiga (rollback). */
    @Test
    fun `does not offer the same or an older version`() = runBlocking {
        server.enqueue(MockResponse().setBody(manifest(versionCode = 3)))
        server.enqueue(MockResponse().setBody(manifest(versionCode = 2)))
        val checker = UpdateChecker(deviceSdk = 34)

        assertEquals(UpdateCheck.UpToDate, checker.check(manifestUrl(), 3))
        assertEquals(UpdateCheck.UpToDate, checker.check(manifestUrl(), 3))
    }

    @Test
    fun `reports when the device android is too old for the new version`() = runBlocking {
        server.enqueue(MockResponse().setBody(manifest(versionCode = 4, minSdk = 30)))

        val result = UpdateChecker(deviceSdk = 28).check(manifestUrl(), 3)

        assertTrue(result is UpdateCheck.Incompatible)
    }

    @Test
    fun `a server error is an IOException, not a crash`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            UpdateChecker(deviceSdk = 34).check(manifestUrl(), 3)
            fail("deveria lançar")
        } catch (expected: IOException) {
            // esperado
        }
    }

    @Test
    fun `the manifest request carries no device credentials`() = runBlocking {
        server.enqueue(MockResponse().setBody(manifest()))

        UpdateChecker(deviceSdk = 34).check(manifestUrl(), 3)

        val request = server.takeRequest()
        assertNull(request.getHeader("X-Device-Token"))
        assertNull(request.getHeader("Authorization"))
    }

    // --- download do APK ---

    private fun release(sha: String = sha256(apkBytes), size: Long = apkBytes.size.toLong()) =
        Release(4, "0.4.0", server.url("/player-4.apk").toString(), sha, size, 21, null)

    @Test
    fun `downloads the apk when checksum and size match`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))
        val target = File(folder.root, "updates/player-4.apk")

        val file = ApkDownloader().download(release(), target)

        assertEquals(apkBytes.toList(), file.readBytes().toList())
        assertFalse("não deve sobrar .part", File(target.path + ".part").exists())
    }

    /** Regressão de segurança: APK adulterado ou corrompido nunca vira arquivo pronto para instalar. */
    @Test
    fun `rejects a tampered apk and leaves nothing behind`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk-adulterado"))
        val target = File(folder.root, "updates/player-4.apk")

        try {
            ApkDownloader().download(release(), target)
            fail("deveria rejeitar")
        } catch (expected: UpdateIntegrityException) {
            // esperado
        }

        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun `rejects an apk whose size differs from the manifest even if the hash matches`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))
        val target = File(folder.root, "updates/player-4.apk")

        try {
            ApkDownloader().download(release(size = 999), target)
            fail("deveria rejeitar")
        } catch (expected: UpdateIntegrityException) {
            // esperado
        }

        assertFalse(target.exists())
    }

    @Test
    fun `a failed download keeps no partial file`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val target = File(folder.root, "updates/player-4.apk")

        try {
            ApkDownloader().download(release(), target)
            fail("deveria lançar")
        } catch (expected: IOException) {
            // esperado
        }

        assertFalse(File(target.path + ".part").exists())
    }

    // --- janela de horário ---

    private fun at(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `no window means any time`() {
        assertTrue(UpdateWindow.isOpen(null, at(15)))
        assertTrue(UpdateWindow.isOpen("", at(15)))
        assertTrue(UpdateWindow.isOpen("lixo", at(15)))
    }

    @Test
    fun `a same-day window opens at the start and closes at the end`() {
        assertFalse(UpdateWindow.isOpen("02:00-05:00", at(1, 59)))
        assertTrue(UpdateWindow.isOpen("02:00-05:00", at(2)))
        assertTrue(UpdateWindow.isOpen("02:00-05:00", at(4, 59)))
        assertFalse(UpdateWindow.isOpen("02:00-05:00", at(5)))
    }

    @Test
    fun `a window that crosses midnight works`() {
        assertTrue(UpdateWindow.isOpen("22:00-06:00", at(23)))
        assertTrue(UpdateWindow.isOpen("22:00-06:00", at(3)))
        assertFalse(UpdateWindow.isOpen("22:00-06:00", at(12)))
    }

    @Test
    fun `rejects impossible times`() {
        assertNull(UpdateWindow.parse("25:00-06:00"))
        assertNull(UpdateWindow.parse("02:61-05:00"))
        assertNull(UpdateWindow.parse("02:00"))
    }

}
