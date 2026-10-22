package com.digitalsignage.player

import com.digitalsignage.player.data.Json
import com.digitalsignage.player.telemetry.HeartbeatReport
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.PlayCount
import com.digitalsignage.player.telemetry.ReportedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun `parses the config exactly as the backend sends it`() {
        val raw = """
            {"deviceId":7,"playlistId":3,"configVersion":5,"items":[
              {"mediaId":11,"name":"b.mp4","type":"VIDEO","url":"/api/v1/media/11/file","checksum":"abc","fileSize":2048,"position":1,"durationSeconds":30},
              {"mediaId":10,"name":"a.png","type":"IMAGE","url":"/api/v1/media/10/file","checksum":"def","fileSize":1024,"position":0,"durationSeconds":10}
            ]}
        """.trimIndent()

        val config = Json.parseConfig(raw)

        assertEquals(3L, config.playlistId)
        assertEquals(5L, config.configVersion)
        // ordenado por position, independentemente da ordem do JSON
        assertEquals(listOf(10L, 11L), config.items.map { it.mediaId })
        assertTrue(config.items[1].isVideo)
        assertFalse(config.items[0].isVideo)
        assertEquals(30, config.items[1].durationSeconds)
    }

    @Test
    fun `device without playlist has null playlistId and no items`() {
        val config = Json.parseConfig("""{"deviceId":1,"playlistId":null,"configVersion":0,"items":[]}""")

        assertNull(config.playlistId)
        assertEquals(0L, config.configVersion)
        assertTrue(config.items.isEmpty())
    }

    @Test
    fun `json null is a real null, not the text null`() {
        val status = Json.parseStatus("""{"deviceId":1,"status":"ACTIVE","activationCode":null,"deviceName":"TV"}""")

        assertNull(status.activationCode)
        assertTrue(status.isActive)
    }

    @Test
    fun `parses a pending status with its activation code`() {
        val status = Json.parseStatus("""{"deviceId":2,"status":"PENDING","activationCode":"K7M2QX","deviceName":"Novo dispositivo"}""")

        assertTrue(status.isPending)
        assertEquals("K7M2QX", status.activationCode)
    }

    @Test
    fun `parses the address the pairing QR Code leads to, and works without it (older server)`() {
        val with = Json.parseStatus("""{"deviceId":2,"status":"PENDING","activationCode":"K7M2QX","deviceName":"x","pairingUrl":"https://painel.test/parear/K7M2QX"}""")
        val without = Json.parseStatus("""{"deviceId":2,"status":"PENDING","activationCode":"K7M2QX","deviceName":"x","pairingUrl":null}""")
        val legacy = Json.parseStatus("""{"deviceId":2,"status":"PENDING","activationCode":"K7M2QX","deviceName":"x"}""")

        assertEquals("https://painel.test/parear/K7M2QX", with.pairingUrl)
        assertNull(without.pairingUrl)
        assertNull(legacy.pairingUrl)
    }

    @Test
    fun `parses the heartbeat answer`() {
        val desired = Json.parseDesired("""{"playlistId":4,"configVersion":9,"serverTime":"2026-10-19T12:00:00Z"}""")

        assertEquals(4L, desired.playlistId)
        assertEquals(9L, desired.configVersion)

        val none = Json.parseDesired("""{"playlistId":null,"configVersion":0,"serverTime":"x"}""")
        assertNull(none.playlistId)
    }

    @Test
    fun `an uneventful heartbeat body is exactly what older servers expect`() {
        val body = Json.heartbeatBody("0.5.1", 12, HeartbeatReport.EMPTY)

        assertEquals(setOf("playerVersion", "configVersion"), body.keys().asSequence().toSet())
        assertEquals("0.5.1", body.getString("playerVersion"))
        assertEquals(12L, body.getLong("configVersion"))
    }

    @Test
    fun `the heartbeat body carries the media on screen, the events with UTC times and the plays`() {
        val report = HeartbeatReport(
            currentMediaId = 7,
            events = listOf(ReportedEvent(1, Level.ERROR, "Falha ao baixar a mídia", 1_793_880_000_000L)),
            plays = listOf(PlayCount(7, 3, 30)),
        )

        val body = Json.heartbeatBody("0.5.1", 12, report)

        assertEquals(7L, body.getLong("currentMediaId"))
        val event = body.getJSONArray("events").getJSONObject(0)
        assertEquals("ERROR", event.getString("level"))
        assertEquals("Falha ao baixar a mídia", event.getString("message"))
        assertEquals("2026-11-05T12:00:00Z", event.getString("at"))
        val play = body.getJSONArray("plays").getJSONObject(0)
        assertEquals(7L, play.getLong("mediaId"))
        assertEquals(3, play.getInt("count"))
        assertEquals(30L, play.getLong("seconds"))
    }

    @Test
    fun `formats instants as UTC regardless of the device time zone`() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Sao_Paulo"))
            assertEquals("1970-01-01T00:00:00Z", Json.isoUtc(0))
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `reads the update check token and treats its absence as none`() {
        val asked = Json.parseDesired("""{"playlistId":4,"configVersion":9,"serverTime":"x","updateCheckToken":1789000000123}""")
        assertEquals(1_789_000_000_123L, asked.updateCheckToken)

        // Servidor antigo (sem o campo): o Player continua funcionando.
        val old = Json.parseDesired("""{"playlistId":4,"configVersion":9,"serverTime":"x"}""")
        assertEquals(0L, old.updateCheckToken)
    }

}
