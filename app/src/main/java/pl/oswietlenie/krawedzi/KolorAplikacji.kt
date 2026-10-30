package pl.oswietlenie.krawedzi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.palette.graphics.Palette

/**
 * Kolor „automatyczny” — wyraźny kolor z ikony aplikacji (jak u Samsunga).
 * Liczony raz na aplikację i trzymany w pamięci.
 */
object KolorAplikacji {
    private val pamiec = HashMap<String, Int>()
    const val ZAPASOWY = 0xFF3D9BFF.toInt()

    fun dla(ctx: Context, pakiet: String, kolorPowiadomienia: Int = 0): Int = synchronized(pamiec) {
        pamiec.getOrPut(pakiet) { zIkony(ctx, pakiet) ?: kolorPowiadomienia.takeIf { it != 0 } ?: ZAPASOWY }
    }

    private fun zIkony(ctx: Context, pakiet: String): Int? = runCatching {
        val ikona = ctx.packageManager.getApplicationIcon(pakiet)
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        ikona.setBounds(0, 0, 96, 96)
        ikona.draw(Canvas(bmp))
        val p = Palette.from(bmp).generate()
        val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch
        bmp.recycle()
        sw?.rgb?.let { it or 0xFF000000.toInt() }
    }.getOrNull()
}
