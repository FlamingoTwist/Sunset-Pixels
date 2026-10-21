package com.retro.pixelplayer.skin

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
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
                val xmlSkin = loadSkinFromXml(context)
                if (xmlSkin != null) {
                    cachedSkin = xmlSkin
                    return xmlSkin
                }
            } catch (e: Exception) {
                // Fallback to json or hardcoded
            }

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

    fun loadSkinFromXml(context: Context): SkinData? {
        return try {
            val portraitVp = parseLayout(context, Configuration.ORIENTATION_PORTRAIT, 412f, 915f)
            val landscapeVp = parseLayout(context, Configuration.ORIENTATION_LANDSCAPE, 915f, 412f)
            if (portraitVp.elements.isEmpty() && landscapeVp.elements.isEmpty()) {
                null
            } else {
                SkinData("Android Studio XML Layout", portraitVp, landscapeVp)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLayout(
        context: Context,
        orientation: Int,
        fallbackCanvasW: Float,
        fallbackCanvasH: Float
    ): ViewportSkin {
        val config = Configuration(context.resources.configuration).apply {
            this.orientation = orientation
        }
        val orientContext = context.createConfigurationContext(config)
        val density = orientContext.resources.displayMetrics.density

        val displayMetrics = orientContext.resources.displayMetrics
        val screenW = displayMetrics.widthPixels / density
        val screenH = displayMetrics.heightPixels / density
        val canvasW = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            minOf(screenW, screenH).coerceAtLeast(360f)
        } else {
            maxOf(screenW, screenH).coerceAtLeast(640f)
        }
        val canvasH = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxOf(screenW, screenH).coerceAtLeast(640f)
        } else {
            minOf(screenW, screenH).coerceAtLeast(360f)
        }

        val dummyParent = android.widget.FrameLayout(orientContext)
        val root = try {
            LayoutInflater.from(orientContext).inflate(
                com.retro.pixelplayer.R.layout.activity_deck,
                dummyParent,
                false
            ) as? ViewGroup
        } catch (e: Exception) {
            null
        } ?: return ViewportSkin(canvasW, canvasH, emptyMap())

        root.layoutDirection = View.LAYOUT_DIRECTION_LTR

        // Make all child views VISIBLE so ConstraintLayout calculates their real width & height
        // even if they have android:visibility="gone" in the XML template (like deck_door_open)
        for (i in 0 until root.childCount) {
            root.getChildAt(i).visibility = View.VISIBLE
        }

        // Run full measure and layout pass so all constraints, chains, and margins are resolved to exact pixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec((canvasW * density).toInt(), View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec((canvasH * density).toInt(), View.MeasureSpec.EXACTLY)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, (canvasW * density).toInt(), (canvasH * density).toInt())

        val elements = mutableMapOf<String, SkinElement>()

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            val idInt = child.id
            if (idInt == View.NO_ID) continue

            val id = try {
                orientContext.resources.getResourceEntryName(idInt)
            } catch (e: Exception) {
                null
            } ?: continue

            val lp = child.layoutParams
            val wPx = when {
                child.width > 0 -> child.width.toFloat()
                child.measuredWidth > 0 -> child.measuredWidth.toFloat()
                lp != null && lp.width > 0 -> lp.width.toFloat()
                else -> 0f
            }
            val hPx = when {
                child.height > 0 -> child.height.toFloat()
                child.measuredHeight > 0 -> child.measuredHeight.toFloat()
                lp != null && lp.height > 0 -> lp.height.toFloat()
                else -> 0f
            }
            val leftPx = child.left.toFloat()
            val topPx = child.top.toFloat()

            val wDp = (wPx / density).coerceAtLeast(0f)
            val hDp = (hPx / density).coerceAtLeast(0f)
            val xDp = leftPx / density
            val yDp = topPx / density

            val relX = xDp / canvasW
            val relY = yDp / canvasH
            val relW = wDp / canvasW
            val relH = hDp / canvasH

            val element = SkinElement(
                id = id,
                name = id,
                sprite = "",
                x = xDp,
                y = yDp,
                width = wDp,
                height = hDp,
                relX = relX,
                relY = relY,
                relWidth = relW,
                relHeight = relH
            )
            elements[id] = element

            // Backward compatibility aliases
            if (id == "cassette_bay") {
                elements["cassette_door"] = element.copy(id = "cassette_door")
            } else if (id == "cassette_door") {
                elements["cassette_bay"] = element.copy(id = "cassette_bay")
            }
        }

        return ViewportSkin(canvasW, canvasH, elements)
    }

    private fun parseSkinJson(jsonStr: String): SkinData? {
        return try {
            val root = JSONObject(jsonStr)
            val name = root.optString("skinName", "Sunset Soundwaves Walkman Classic")
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
        // Safe hardcoded defaults matching sunset-soundwaves-skin.json
        val portraitMap = mapOf(
            "cassette_door" to SkinElement("cassette_door", "Cassette Deck (Empty Base)", "deck_empty_base.png", 18f, 50f, 375f, 216f, 0.0437f, 0.0546f, 0.9102f, 0.2361f),
            "deck_door_open" to SkinElement("deck_door_open", "Open Deck Door (Tilted Cradle)", "deck_door_open_front.png", 0f, 149f, 423f, 117f, 0.0f, 0.1628f, 1.0267f, 0.1279f),
            "speaker_vents" to SkinElement("speaker_vents", "Speaker Vents", "chassis_speaker_vents.png", 50f, 275f, 315f, 75f, 0.1214f, 0.3005f, 0.7646f, 0.0820f),
            "lcd_display" to SkinElement("lcd_display", "LCD Display Frame", "chassis_lcd_frame.png", 10f, 360f, 392f, 163f, 0.0243f, 0.3934f, 0.9515f, 0.1781f),
            "switch_shuffle" to SkinElement("switch_shuffle", "Shuffle Switch", "switch_shuffle_off.png", 50f, 545f, 60f, 31f, 0.1214f, 0.5956f, 0.1456f, 0.0339f),
            "switch_repeat" to SkinElement("switch_repeat", "Repeat Switch", "switch_repeat_off.png", 290f, 545f, 60f, 31f, 0.7039f, 0.5956f, 0.1456f, 0.0339f),
            "btn_skip_prev" to SkinElement("btn_skip_prev", "Prev Track", "btn_skip_prev_unpressed.png", 20f, 600f, 44f, 48f, 0.0485f, 0.6557f, 0.1068f, 0.0525f),
            "btn_rewind" to SkinElement("btn_rewind", "Rewind", "btn_rewind_unpressed.png", 75f, 600f, 43f, 48f, 0.1820f, 0.6557f, 0.1044f, 0.0525f),
            "btn_play" to SkinElement("btn_play", "Play", "btn_play_unpressed.png", 130f, 600f, 56f, 49f, 0.3155f, 0.6557f, 0.1359f, 0.0536f),
            "btn_fast_fwd" to SkinElement("btn_fast_fwd", "Fast Forward", "btn_fast_fwd_unpressed.png", 200f, 600f, 44f, 48f, 0.4854f, 0.6557f, 0.1068f, 0.0525f),
            "btn_skip_next" to SkinElement("btn_skip_next", "Next Track", "btn_skip_next_unpressed.png", 255f, 600f, 44f, 48f, 0.6189f, 0.6557f, 0.1068f, 0.0525f),
            "btn_stop" to SkinElement("btn_stop", "Stop", "btn_stop_unpressed.png", 310f, 600f, 42f, 48f, 0.7524f, 0.6557f, 0.1019f, 0.0525f),
            "btn_eject" to SkinElement("btn_eject", "Eject Button", "btn_eject_unpressed.png", 170f, 670f, 49f, 56f, 0.4126f, 0.7322f, 0.1189f, 0.0612f)
        )

        val landscapeMap = mapOf(
            "cassette_door" to SkinElement("cassette_door", "Cassette Deck (Empty Base)", "deck_empty_base.png", 29f, 62f, 450f, 259f, 0.0317f, 0.1505f, 0.4918f, 0.6286f),
            "deck_door_open" to SkinElement("deck_door_open", "Open Deck Door (Tilted Cradle)", "deck_door_open_front.png", -4f, 177f, 508f, 140f, -0.0044f, 0.4296f, 0.5552f, 0.3398f),
            "lcd_display" to SkinElement("lcd_display", "LCD Display Frame", "chassis_lcd_frame.png", 500f, 40f, 392f, 163f, 0.5464f, 0.0971f, 0.4284f, 0.3956f),
            "speaker_vents" to SkinElement("speaker_vents", "Speaker Vents", "chassis_speaker_vents.png", 500f, 215f, 315f, 75f, 0.5464f, 0.5218f, 0.3443f, 0.1820f),
            "switch_shuffle" to SkinElement("switch_shuffle", "Shuffle Switch", "switch_shuffle_off.png", 510f, 275f, 60f, 31f, 0.5574f, 0.6675f, 0.0656f, 0.0752f),
            "switch_repeat" to SkinElement("switch_repeat", "Repeat Switch", "switch_repeat_off.png", 590f, 275f, 60f, 31f, 0.6448f, 0.6675f, 0.0656f, 0.0752f),
            "btn_skip_prev" to SkinElement("btn_skip_prev", "Prev Track", "btn_skip_prev_unpressed.png", 30f, 335f, 47f, 52f, 0.0328f, 0.8131f, 0.0514f, 0.1262f),
            "btn_rewind" to SkinElement("btn_rewind", "Rewind", "btn_rewind_unpressed.png", 90f, 335f, 47f, 52f, 0.0984f, 0.8131f, 0.0514f, 0.1262f),
            "btn_play" to SkinElement("btn_play", "Play", "btn_play_unpressed.png", 150f, 335f, 61f, 53f, 0.1639f, 0.8131f, 0.0667f, 0.1286f),
            "btn_fast_fwd" to SkinElement("btn_fast_fwd", "Fast Forward", "btn_fast_fwd_unpressed.png", 225f, 335f, 47f, 52f, 0.2459f, 0.8131f, 0.0514f, 0.1262f),
            "btn_skip_next" to SkinElement("btn_skip_next", "Next Track", "btn_skip_next_unpressed.png", 285f, 335f, 47f, 52f, 0.3115f, 0.8131f, 0.0514f, 0.1262f),
            "btn_stop" to SkinElement("btn_stop", "Stop", "btn_stop_unpressed.png", 345f, 335f, 46f, 52f, 0.3770f, 0.8131f, 0.0503f, 0.1262f),
            "btn_eject" to SkinElement("btn_eject", "Eject Button", "btn_eject_unpressed.png", 720f, 325f, 52f, 60f, 0.7869f, 0.7888f, 0.0568f, 0.1456f)
        )

        return SkinData("Sunset Soundwaves Walkman Classic", ViewportSkin(412f, 915f, portraitMap), ViewportSkin(915f, 412f, landscapeMap))
    }
}
