package com.digitalsignage.player

import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.ConfigZone
import com.digitalsignage.player.data.Json
import com.digitalsignage.player.data.OfflinePlan
import com.digitalsignage.player.data.PlanPlaylist
import com.digitalsignage.player.data.PlanSegment
import com.digitalsignage.player.data.PlayerConfig
import com.digitalsignage.player.plan.OfflinePlanner
import com.digitalsignage.player.sync.ConfigSync
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val imgMain = "principal".toByteArray()
private val imgSide = "lateral".toByteArray()
private val imgTall = "em-pe".toByteArray()

private fun landscape(vararg main: com.digitalsignage.player.data.ConfigItem) = listOf(
    ConfigZone(1, "Principal", 0, 0, 75, 100, true, main.toList()),
    ConfigZone(2, "Lateral", 75, 0, 25, 100, false, listOf(itemFor(2, imgSide)), fit = "COVER"),
)

private fun portrait(vararg main: com.digitalsignage.player.data.ConfigItem) = listOf(
    ConfigZone(3, "Principal", 0, 0, 100, 75, true, main.toList()),
    ConfigZone(4, "Rodapé", 0, 75, 100, 25, false, listOf(itemFor(3, imgTall)), fit = "COVER"),
)

private fun config(portraitZones: List<ConfigZone> = portrait(itemFor(1, imgMain))) =
    PlayerConfig(1, 5, 1, listOf(itemFor(1, imgMain)), landscape(itemFor(1, imgMain)), portraitZones = portraitZones)

class ResponsiveLayoutTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the server config brings both versions and the fit of each zone`() {
        val parsed = Json.parseConfig(rawJson(config()))

        assertEquals(2, parsed.zones.size)
        assertEquals(2, parsed.portraitZones.size)
        assertEquals("CONTAIN", parsed.zones[0].fit)
        assertEquals("COVER", parsed.zones[1].fit)
        assertTrue(parsed.zones[1].covers)
        assertEquals("Rodapé", parsed.portraitZones[1].name)
        assertEquals(config(), parsed)
    }

    @Test
    fun `an old server without the new fields still parses`() {
        val parsed = Json.parseConfig("""{"deviceId":1,"playlistId":5,"configVersion":1,"items":[],"zones":[{"id":1,"name":"Principal","x":0,"y":0,"width":100,"height":100,"main":true,"items":[]}]}""")

        assertTrue(parsed.portraitZones.isEmpty())
        assertEquals("CONTAIN", parsed.zones[0].fit)
        assertFalse(parsed.zones[0].covers)
    }

    @Test
    fun `an unknown fit falls back to showing the whole media`() {
        val parsed = Json.parseConfig("""{"deviceId":1,"playlistId":5,"configVersion":1,"items":[],"zones":[{"id":1,"name":"P","x":0,"y":0,"width":100,"height":100,"main":true,"fit":"ESTICAR","items":[]}]}""")

        assertEquals("CONTAIN", parsed.zones[0].fit)
    }

    @Test
    fun `a portrait screen uses the portrait version when the layout has one, a landscape screen never does`() {
        val config = config()

        assertEquals(config.portraitZones, config.zonesFor(portrait = true))
        assertEquals(config.zones, config.zonesFor(portrait = false))
    }

    @Test
    fun `without a portrait version a portrait screen keeps the horizontal zones, and without layout it is full screen`() {
        val noPortrait = config(portraitZones = emptyList())
        assertEquals(noPortrait.zones, noPortrait.zonesFor(portrait = true))

        val plain = PlayerConfig(1, 5, 1, listOf(itemFor(1, imgMain)))
        assertEquals(1, plain.zonesFor(portrait = true).size)
        assertTrue(plain.zonesFor(portrait = true)[0].isFullScreen)
    }

    @Test
    fun `media that only the portrait version uses is downloaded and kept`() = runTest {
        val api = FakePlayerApi()
        val downloader = FakeDownloader()
        listOf(itemFor(1, imgMain), itemFor(2, imgSide), itemFor(3, imgTall)).forEach { downloader.files[it.url] = if (it.mediaId == 1L) imgMain else if (it.mediaId == 2L) imgSide else imgTall }
        api.configResponse = { config() }
        val cache = MediaCache(File(folder.root, "media"))

        ConfigSync(api, downloader, cache, InMemoryStore()).sync("tok")

        assertTrue(downloader.requested.contains("/api/v1/media/3/file"))
        assertTrue(cache.isCached(itemFor(3, imgTall)))
    }

    @Test
    fun `offline, the campaign content goes to the main zone of both versions`() {
        val promo = itemFor(9, "promo".toByteArray())
        val plan = OfflinePlan(
            1, 0, 1_000,
            listOf(PlanSegment(0, 500, null, null, 5, 1), PlanSegment(500, 1_000, 7, "Promo", 6, 2)),
            listOf(PlanPlaylist(5, listOf(itemFor(1, imgMain))), PlanPlaylist(6, listOf(promo))),
        )
        val base = config().copy(plan = plan)

        val during = OfflinePlanner.configAt(base, 600)!!

        assertEquals(listOf(9L), during.zones.first { it.main }.items.map { it.mediaId })
        assertEquals(listOf(9L), during.portraitZones.first { it.main }.items.map { it.mediaId })
        // as zonas fixas não mudam, nas duas versões
        assertEquals(listOf(2L), during.zones.first { !it.main }.items.map { it.mediaId })
        assertEquals(listOf(3L), during.portraitZones.first { !it.main }.items.map { it.mediaId })
        assertEquals("COVER", during.portraitZones.first { !it.main }.fit)
    }
}
