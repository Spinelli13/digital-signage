package com.digitalsignage.player

import com.digitalsignage.player.data.Json
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
    fun `parses the heartbeat answer`() {
        val desired = Json.parseDesired("""{"playlistId":4,"configVersion":9,"serverTime":"2026-10-19T12:00:00Z"}""")

        assertEquals(4L, desired.playlistId)
        assertEquals(9L, desired.configVersion)

        val none = Json.parseDesired("""{"playlistId":null,"configVersion":0,"serverTime":"x"}""")
        assertNull(none.playlistId)
    }

}
