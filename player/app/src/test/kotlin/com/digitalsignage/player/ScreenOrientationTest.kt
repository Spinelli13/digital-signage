package com.digitalsignage.player

import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.OfflinePlan
import com.digitalsignage.player.data.PlanPlaylist
import com.digitalsignage.player.data.PlanSegment
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.data.ScreenOrientation
import com.digitalsignage.player.plan.OfflinePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOrientationTest {

    @Test
    fun `on a landscape display a TV installed in portrait turns the content so it stands up for who looks`() {
        // topo da TV para a direita: o conteúdo gira 90° para a esquerda (270° no sentido horário)
        assertEquals(270, ScreenOrientation.rotationDegrees("PORTRAIT_TOP_RIGHT", displayPortrait = false))
        assertEquals(90, ScreenOrientation.rotationDegrees("PORTRAIT_TOP_LEFT", displayPortrait = false))
        assertEquals(180, ScreenOrientation.rotationDegrees("LANDSCAPE_FLIPPED", displayPortrait = false))
        assertEquals(0, ScreenOrientation.rotationDegrees("LANDSCAPE", displayPortrait = false))
        assertEquals(0, ScreenOrientation.rotationDegrees("AUTO", displayPortrait = false))
    }

    @Test
    fun `when the device already delivers a portrait screen nothing is turned for portrait`() {
        assertEquals(0, ScreenOrientation.rotationDegrees("PORTRAIT_TOP_RIGHT", displayPortrait = true))
        assertEquals(0, ScreenOrientation.rotationDegrees("PORTRAIT_TOP_LEFT", displayPortrait = true))
        assertEquals(0, ScreenOrientation.rotationDegrees("AUTO", displayPortrait = true))
        // pedir horizontal numa tela que já é em pé obriga a girar
        assertEquals(90, ScreenOrientation.rotationDegrees("LANDSCAPE", displayPortrait = true))
        assertEquals(270, ScreenOrientation.rotationDegrees("LANDSCAPE_FLIPPED", displayPortrait = true))
    }

    @Test
    fun `the portrait version of a layout is used when the TV is installed in portrait, whatever the display says`() {
        assertTrue(ScreenOrientation.contentPortrait("PORTRAIT_TOP_RIGHT", displayPortrait = false))
        assertTrue(ScreenOrientation.contentPortrait("PORTRAIT_TOP_LEFT", displayPortrait = false))
        assertFalse(ScreenOrientation.contentPortrait("LANDSCAPE", displayPortrait = true))
        assertFalse(ScreenOrientation.contentPortrait("LANDSCAPE_FLIPPED", displayPortrait = true))
        // automático: segue o aparelho, como sempre
        assertTrue(ScreenOrientation.contentPortrait("AUTO", displayPortrait = true))
        assertFalse(ScreenOrientation.contentPortrait("AUTO", displayPortrait = false))
    }

    @Test
    fun `only quarter turns swap width and height`() {
        assertTrue(ScreenOrientation.swapsAxes(90))
        assertTrue(ScreenOrientation.swapsAxes(270))
        assertFalse(ScreenOrientation.swapsAxes(0))
        assertFalse(ScreenOrientation.swapsAxes(180))
    }

    @Test
    fun `an unknown value from a newer server falls back to automatic`() {
        assertEquals("AUTO", ScreenOrientation.normalize("DIAGONAL"))
        assertEquals("AUTO", ScreenOrientation.normalize(null))
        assertEquals(0, ScreenOrientation.rotationDegrees("DIAGONAL", displayPortrait = false))
        val parsed = Json.parseConfig("""{"deviceId":1,"playlistId":5,"configVersion":1,"items":[],"orientation":"DIAGONAL"}""")
        assertEquals("AUTO", parsed.orientation)
    }

    @Test
    fun `the orientation comes with the configuration and an old server without it means automatic`() {
        val config = PlayerConfig(1, 5, 1, listOf(itemFor(1, "a".toByteArray())), orientation = "PORTRAIT_TOP_RIGHT")

        assertEquals("PORTRAIT_TOP_RIGHT", Json.parseConfig(rawJson(config)).orientation)
        assertEquals(config, Json.parseConfig(rawJson(config)))
        assertEquals("AUTO", Json.parseConfig("""{"deviceId":1,"playlistId":5,"configVersion":1,"items":[]}""").orientation)
    }

    @Test
    fun `the offline agenda keeps the orientation of the TV`() {
        val promo = itemFor(9, "promo".toByteArray())
        val plan = OfflinePlan(
            1, 0, 1_000,
            listOf(PlanSegment(0, 500, null, null, 5, 1), PlanSegment(500, 1_000, 7, "Promo", 6, 2)),
            listOf(PlanPlaylist(5, listOf(itemFor(1, "a".toByteArray()))), PlanPlaylist(6, listOf(promo))),
        )
        val base = PlayerConfig(1, 5, 1, listOf(itemFor(1, "a".toByteArray())), plan = plan, orientation = "PORTRAIT_TOP_LEFT")

        val during = OfflinePlanner.configAt(base, 600)!!

        assertEquals(listOf(9L), during.items.map { it.mediaId })
        assertEquals("PORTRAIT_TOP_LEFT", during.orientation)
    }
}
