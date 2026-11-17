package io.github.kalpa0448.taskmgr.compose

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File

/** 主题与背景配置（从 SharedPreferences 读取，和旧版设置共用同一份） */
class ThemeState(val sp: SharedPreferences) {

    var accent by mutableStateOf(Color(0xFF4FC3F7))
    var dark by mutableStateOf(true)
    var bg by mutableStateOf<ImageBitmap?>(null)      // 清晰背景
    var bgBlur by mutableStateOf<ImageBitmap?>(null)  // 卡片取样用模糊背景
    var luma by mutableStateOf(0.35f)
    var cardAlpha by mutableStateOf(0.55f)
    var scrim by mutableStateOf(0.30f)
    var blurLevel by mutableStateOf(5)
    var bgSeed by mutableStateOf(true)

    // 调色板
    var bgColor by mutableStateOf(Color(0xFF101012))
    var cardColor by mutableStateOf(Color(0xFF1E1E22))
    var cardColor2 by mutableStateOf(Color(0xFF17171A))
    var textColor by mutableStateOf(Color(0xFFECECEC))
    var subColor by mutableStateOf(Color(0xFF9A9A9A))
    var memColor by mutableStateOf(Color(0xFF81C784))
    var warnColor by mutableStateOf(Color(0xFFEF5350))
    var swapColor by mutableStateOf(Color(0xFFFFB74D))

    fun reload(ctx: android.content.Context) {
        val themeMode = sp.getString("darkMode", "auto") ?: "auto"
        val sysDark = (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        dark = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> sysDark
        }

        blurLevel = sp.getInt("blurLevel", 5)
        cardAlpha = Math.max(10, Math.min(100, sp.getInt("cardAlpha", 55))) / 100f
        scrim = Math.max(0, Math.min(100, sp.getInt("scrim", 30))) / 100f
        bgSeed = sp.getBoolean("bgSeed", true)

        val f = File(ctx.filesDir, "bg.png")
        if (f.exists()) {
            val raw = BitmapFactory.decodeFile(f.absolutePath)
            if (raw != null) {
                val fitted = fitToScreen(ctx, raw)
                bg = fitted.asImageBitmap()
                luma = averageLuma(fitted)
                bgBlur = blur(fitted, blurLevel)?.asImageBitmap()
            }
        } else {
            bg = null
            bgBlur = null
            luma = 0.35f
        }

        val accentBase = when {
            bgSeed && bg != null -> extractAccent(bgBlur ?: bg!!) ?: defaultAccent()
            sp.getString("accentMode", "default") == "monet" && Build.VERSION.SDK_INT >= 31 ->
                monetAccent(ctx) ?: defaultAccent()
            else -> defaultAccent()
        }
        accent = accentBase

        bgColor = if (dark) Color(0xFF101013) else Color(0xFFF3F3F7)
        cardColor = if (dark) Color(0xFF1C1C21) else Color(0xFFFFFFFF)
        cardColor2 = if (dark) Color(0xFF17171B) else Color(0xFFF0F0F5)
        textColor = if (dark) Color(0xFFECECEC) else Color(0xFF1A1A1A)
        subColor = if (dark) Color(0xFF9A9A9A) else Color(0xFF6B6B6B)
        memColor = if (dark) Color(0xFF7FD48A) else Color(0xFF2E7D32)
        warnColor = if (dark) Color(0xFFEF6B6B) else Color(0xFFC62828)
        swapColor = if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
    }

    /** 卡片底色（带透明度） */
    fun cardTint(alt: Boolean = false): Color {
        val c = if (alt) cardColor2 else cardColor
        return c.copy(alpha = cardAlpha)
    }

    /** 叠在背景上的暗色蒙版 */
    fun scrimColor(): Color = Color.Black.copy(alpha = scrim)

    private fun defaultAccent() = if (dark) Color(0xFF4FC3F7) else Color(0xFF0288D1)

    private fun monetAccent(ctx: android.content.Context): Color? = try {
        val id = if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        Color(ctx.getColor(id))
    } catch (t: Throwable) {
        null
    }

    private fun fitToScreen(ctx: android.content.Context, src: Bitmap): Bitmap {
        val dm = ctx.resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val scale = maxOf(sw.toFloat() / src.width, sh.toFloat() / src.height)
        val w = maxOf(sw, (src.width * scale).toInt())
        val h = maxOf(sh, (src.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val x = ((w - sw) / 2).coerceAtLeast(0)
        val y = ((h - sh) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, x, y, minOf(sw, w), minOf(sh, h))
    }

    private fun averageLuma(bm: Bitmap): Float {
        val tiny = Bitmap.createScaledBitmap(bm, 12, 12, true)
        var sum = 0.0
        for (y in 0 until 12) for (x in 0 until 12) {
            val c = tiny.getPixel(x, y)
            sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) +
                    0.114 * (c and 0xFF)) / 255.0
        }
        tiny.recycle()
        return (sum / 144).toFloat()
    }

    private fun extractAccent(bm: ImageBitmap): Color? {
        return try {
            val tiny = Bitmap.createScaledBitmap(bm.asAndroidBitmap(), 12, 12, true)
            var r = 0L; var g = 0L; var b = 0L
            for (y in 0 until 12) for (x in 0 until 12) {
                val c = tiny.getPixel(x, y)
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            tiny.recycle()
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r / 144).toInt(), (g / 144).toInt(), (b / 144).toInt(), hsv)
            hsv[1] = Math.min(1f, hsv[1] * 1.6f + 0.35f)
            hsv[2] = Math.max(0.62f, Math.min(0.92f, hsv[2] * 0.6f + 0.45f))
            Color(android.graphics.Color.HSVToColor(hsv))
        } catch (t: Throwable) {
            null
        }
    }
}

/** 降采样 + 放大 = 快速模糊 */
fun blur(src: Bitmap, level: Int): Bitmap? = try {
    val div = 6 + level * 4
    val w = maxOf(8, src.width / div)
    val h = maxOf(8, src.height / div)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val out = Bitmap.createScaledBitmap(small, src.width, src.height, true)
    if (small != out) small.recycle()
    out
} catch (t: Throwable) {
    null
}


