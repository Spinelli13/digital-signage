package com.digitalsignage.player

import com.digitalsignage.player.diagnostics.Diagnostics
import com.digitalsignage.player.diagnostics.DiagnosticsInput
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O relatório de diagnóstico: o que está certo, o que é problema e o texto curto que vai para o suporte. */
class DiagnosticsTest {

    private val zone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val now = 1_793_890_800_000L // 2026-11-05 12:00 em Brasília

    private fun healthy() = DiagnosticsInput(
        version = "0.14.70", model = "TCL 55S546", androidRelease = "11", ramMb = 1_800, serverUrl = "https://servidor.test",
        internet = true, contactAgoSec = 12, freeMb = 3_100, cacheMb = 412, lastSyncAtMs = now - 7 * 60_000, nowMs = now,
        missingFiles = 0, missingMb = 0, bootOk = true, overlayOk = true, installOk = true,
    )

    @Test
    fun `a healthy TV has no issues and the report fits in a short text`() {
        val report = Diagnostics.build(healthy(), zone)

        assertEquals(0, report.issues)
        val text = report.text()
        assertTrue(text, text.lineSequence().count() <= 12)
        assertTrue(text.contains("Vyzzu Player 0.14.70 | Android 11 | TCL 55S546 | RAM 1800 MB"))
        assertTrue(text.contains("Servidor: https://servidor.test"))
        assertTrue(text.contains("OK Internet: conectada"))
        assertTrue(text.contains("OK Servidor: respondeu há 12 s"))
        assertTrue(text.contains("OK Última sincronização: 05/11 11:53 (há 7 min)"))
        assertTrue(text.contains("OK A baixar: nada"))
        assertFalse(text.contains("!!"))
    }

    @Test
    fun `every kind of problem is flagged and counted`() {
        val sick = healthy().copy(
            internet = false, contactAgoSec = 600, freeMb = 80, missingFiles = 3, missingMb = 140,
            bootOk = false, overlayOk = false, installOk = false, lastSyncAtMs = 0,
        )

        val report = Diagnostics.build(sick, zone)

        // internet, servidor, espaço, a baixar e as três permissões; a "última sincronização" é só informação
        assertEquals(7, report.issues)
        val text = report.text()
        assertTrue(text.contains("!! Internet: SEM CONEXÃO"))
        assertTrue(text.contains("!! Servidor: SEM RESPOSTA há 10 min"))
        assertTrue(text.contains("!! Espaço livre: 80 MB (conteúdo guardado: 412 MB) POUCO ESPAÇO"))
        assertTrue(text.contains("OK Última sincronização: nenhuma desde que o Player abriu"))
        assertTrue(text.contains("!! A baixar: FALTAM 3 arquivo(s), 140 MB"))
        assertTrue(text.contains("!! Sobrepor outros apps: FALTA"))
        assertTrue(text.contains("!! Instalar apps (atualização): FALTA"))
    }

    @Test
    fun `a server that never answered is told apart from one that stopped answering`() {
        val never = Diagnostics.build(healthy().copy(contactAgoSec = null), zone)
        val stale = Diagnostics.build(healthy().copy(contactAgoSec = Diagnostics.CONTACT_STALE_SEC + 1), zone)
        val fresh = Diagnostics.build(healthy().copy(contactAgoSec = Diagnostics.CONTACT_STALE_SEC), zone)

        assertTrue(never.text().contains("!! Servidor: NÃO RESPONDEU desde que o Player abriu"))
        assertTrue(stale.text().contains("!! Servidor: SEM RESPOSTA"))
        assertEquals(0, fresh.issues)
    }

    @Test
    fun `the free space limit is exact`() {
        assertEquals(0, Diagnostics.build(healthy().copy(freeMb = Diagnostics.LOW_SPACE_MB), zone).issues)
        assertEquals(1, Diagnostics.build(healthy().copy(freeMb = Diagnostics.LOW_SPACE_MB - 1), zone).issues)
    }

    @Test
    fun `durations read like a person would say them`() {
        assertEquals("0 s", Diagnostics.duration(-5))
        assertEquals("45 s", Diagnostics.duration(45))
        assertEquals("12 min", Diagnostics.duration(720))
        assertEquals("3 h 20 min", Diagnostics.duration(12_000))
        assertEquals("2 dia(s)", Diagnostics.duration(2 * 86_400 + 10))
    }
}
