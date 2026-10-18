package com.retro.pixelplayer.skin

import android.content.Context
import org.json.JSONObject

data class SkinElement(
    val id: String,
    val name: String,
    val sprite: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val relX: Float,
    val relY: Float,
    val relWidth: Float,
    val relHeight: Float
)

data class ViewportSkin(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val elements: Map<String, SkinElement>
) {
    fun getElement(id: String): SkinElement? = elements[id]
}

data class SkinData(
    val skinName: String,
    val portrait: ViewportSkin,
    val landscape: ViewportSkin
)

object SkinManager {
    private var cachedSkin: SkinData? = null

    fun loadSkin(context: Context?): SkinData {
        cachedSkin?.let { return it }

        if (context != null) {
            try {
                val jsonString = context.assets.open("default_skin.json").bufferedReader().use { it.readText() }
                val parsed = parseSkinJson(jsonString)
                if (parsed != null) {
                    cachedSkin = parsed
                    return parsed
                }
            } catch (e: Exception) {
                // Fallback to defaults
            }
        }

        val fallback = createFallbackSkin()
        cachedSkin = fallback
        return fallback
    }

    private fun parseSkinJson(jsonStr: String): SkinData? {
        return try {
            val root = JSONObject(jsonStr)
            val name = root.optString("skinName", "Sunset Pixels Walkman Classic")
            val viewports = root.getJSONObject("viewports")

            val portraitVp = parseViewport(viewports.getJSONObject("portrait"))
            val landscapeVp = parseViewport(viewports.getJSONObject("landscape"))

            SkinData(name, portraitVp, landscapeVp)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseViewport(obj: JSONObject): ViewportSkin {
        val cw = obj.optDouble("canvasWidth", 412.0).toFloat()
        val ch = obj.optDouble("canvasHeight", 915.0).toFloat()
        val elementsArray = obj.getJSONArray("elements")
        val map = mutableMapOf<String, SkinElement>()

        for (i in 0 until elementsArray.length()) {
            val el = elementsArray.getJSONObject(i)
            val id = el.getString("id")
            val item = SkinElement(
                id = id,
                name = el.optString("name", id),
                sprite = el.optString("sprite", ""),
                x = el.optDouble("x", 0.0).toFloat(),
                y = el.optDouble("y", 0.0).toFloat(),
                width = el.optDouble("width", 50.0).toFloat(),
                height = el.optDouble("height", 50.0).toFloat(),
                relX = el.optDouble("relX", 0.0).toFloat(),
                relY = el.optDouble("relY", 0.0).toFloat(),
                relWidth = el.optDouble("relWidth", 0.1).toFloat(),
                relHeight = el.optDouble("relHeight", 0.1).toFloat()
            )
            map[id] = item
        }

        return ViewportSkin(cw, ch, map)
    }

    private fun createFallbackSkin(): SkinData {
        // Safe hardcoded defaults matching sunset-pixels-skin.json
        val portraitMap = mapOf(
            "cassette_door" to SkinElement("cassette_door", "Cassette Door", "chassis_tape_door_frame.png", 22f, 119f, 360f, 220f, 0.0534f, 0.1301f, 0.8738f, 0.2404f),
            "lcd_display" to SkinElement("lcd_display", "LCD Display", "chassis_lcd_frame.png", 22f, 391f, 360f, 180f, 0.0534f, 0.4273f, 0.8738f, 0.1967f),
            "switch_shuffle" to SkinElement("switch_shuffle", "Shuffle Switch", "switch_shuffle_off.png", 53f, 596f, 64f, 32f, 0.1286f, 0.6514f, 0.1553f, 0.0350f),
            "switch_repeat" to SkinElement("switch_repeat", "Repeat Switch", "switch_repeat_off.png", 297f, 599f, 64f, 32f, 0.7209f, 0.6546f, 0.1553f, 0.0350f),
            "btn_skip_prev" to SkinElement("btn_skip_prev", "Prev Track", "btn_skip_prev_unpressed.png", 29f, 661f, 48f, 48f, 0.0704f, 0.7224f, 0.1165f, 0.0525f),
            "btn_stop" to SkinElement("btn_stop", "Stop", "btn_stop_unpressed.png", 317f, 661f, 48f, 48f, 0.7694f, 0.7224f, 0.1165f, 0.0525f),
            "speaker_vents" to SkinElement("speaker_vents", "Speaker Vents", "chassis_speaker_vents.png", 26f, 771f, 360f, 42f, 0.0631f, 0.8426f, 0.8738f, 0.0459f)
        )

        val landscapeMap = mapOf(
            "cassette_door" to SkinElement("cassette_door", "Cassette Door", "chassis_tape_door_frame.png", 35f, 45f, 480f, 290f, 0.0383f, 0.1092f, 0.5246f, 0.7039f),
            "lcd_display" to SkinElement("lcd_display", "LCD Display", "chassis_lcd_frame.png", 530f, 64f, 340f, 170f, 0.5792f, 0.1553f, 0.3716f, 0.4126f),
            "speaker_vents" to SkinElement("speaker_vents", "Speaker Vents", "chassis_speaker_vents.png", 110f, 351f, 340f, 36f, 0.1202f, 0.8519f, 0.3716f, 0.0874f),
            "switch_shuffle" to SkinElement("switch_shuffle", "Shuffle Switch", "switch_shuffle_off.png", 609f, 350f, 60f, 30f, 0.6656f, 0.8495f, 0.0656f, 0.0728f),
            "switch_repeat" to SkinElement("switch_repeat", "Repeat Switch", "switch_repeat_off.png", 749f, 350f, 60f, 30f, 0.8186f, 0.8495f, 0.0656f, 0.0728f),
            "btn_skip_prev" to SkinElement("btn_skip_prev", "Prev Track", "btn_skip_prev_unpressed.png", 532f, 272f, 48f, 48f, 0.5814f, 0.6602f, 0.0525f, 0.1165f),
            "btn_stop" to SkinElement("btn_stop", "Stop", "btn_stop_unpressed.png", 826f, 272f, 48f, 48f, 0.9027f, 0.6602f, 0.0525f, 0.1165f)
        )

        return SkinData("Sunset Pixels Walkman Classic", ViewportSkin(412f, 915f, portraitMap), ViewportSkin(915f, 412f, landscapeMap))
    }
}
