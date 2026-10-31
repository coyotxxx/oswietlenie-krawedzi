package pl.oswietlenie.krawedzi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Color
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rysuje ramkę na zwykłym Canvasie. Tego samego kodu używa podgląd w aplikacji
 * i nakładka nad ekranem — to, co widać w ustawieniach, jest tym, co zaświeci.
 */
class Rysownik {
    private val linia = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val poswiata = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val prostokat = RectF()
    // Zamknięta ścieżka zamiast drawRoundRect — rozmycie poświaty nie zostawia kreski w miejscu startu linii.
    private val sciezka = Path()
    private val macierz = Matrix()
    private var tecza: SweepGradient? = null
    private var teczaDla = 0f to 0f
    private val barwa = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wycinanie = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val celMaski = RectF()
    private var maska: Bitmap? = null
    private var maskaDla: List<Float>? = null
    private var blask: SweepGradient? = null
    private var blaskDla: Triple<Float, Float, Int>? = null

    /**
     * @param grubosc szerokość linii w pikselach
     * @param promien promień zaokrąglenia rogów ekranu w pikselach
     * @param czasMs czas od początku efektu — z niego liczona jest animacja
     */
    fun rysuj(
        c: Canvas, szer: Float, wys: Float, styl: Styl, kolor: Int,
        grubosc: Float, promien: Float, czasMs: Long
    ) {
        val krycie = 1f - styl.przezroczystosc / 100f
        // Suwak „Prędkość” przyspiesza albo zwalnia zegar animacji — tak samo dla każdego efektu.
        val t = (czasMs * TEMPO[styl.predkosc - 1]).toLong()
        if (styl.efekt == Efekt.POSWIATA) { rysujPoswiate(c, szer, wys, styl, kolor, promien, t, krycie); return }
        val pol = grubosc / 2f
        prostokat.set(pol, pol, szer - pol, wys - pol)
        val r = (promien - pol).coerceAtLeast(0f)

        val shader: Shader?
        val puls: Float
        when (styl.efekt) {
            Efekt.PODSTAWOWY -> {
                shader = null
                // Łagodne wdech-wydech, jeden oddech na 1,6 s.
                puls = 0.35f + 0.65f * (0.5f - 0.5f * cos(2 * PI * (t % OKRES_PULSU) / OKRES_PULSU).toFloat())
            }
            Efekt.MIGOTANIE -> {
                shader = teczaDo(szer, wys).also {
                    macierz.setRotate(360f * (t % OBROT_TECZY) / OBROT_TECZY, szer / 2, wys / 2)
                    it.setLocalMatrix(macierz)
                }
                puls = 1f
            }
            Efekt.POSWIATA -> return
        }

        for (p in arrayOf(poswiata, linia)) {
            p.shader = shader
            p.color = kolor
            if (shader != null) p.color = 0xFFFFFFFF.toInt()
        }
        sciezka.reset()
        sciezka.addRoundRect(prostokat, r, r, Path.Direction.CW)
        sciezka.close()

        // Poświata z kilku coraz węższych, półprzezroczystych linii. Rozmycie (BlurMaskFilter)
        // zostawiało na tęczy widoczny szew — kreskę z kropek przy krawędzi.
        for ((mnoznik, sila) in POSWIATA) {
            poswiata.strokeWidth = grubosc * mnoznik
            poswiata.alpha = (255 * krycie * puls * sila).toInt()
            c.drawPath(sciezka, poswiata)
        }
        linia.strokeWidth = grubosc
        linia.alpha = (255 * krycie * puls).toInt()
        c.drawPath(sciezka, linia)
    }

    /**
     * Szeroki, miękki blask od krawędzi w głąb ekranu, jak Asystent na Pixelu. Kolory powoli
     * wędrują dookoła. Kolory to obracany SweepGradient, a kształt blasku — maska przezroczystości
     * liczona raz dla rozmiaru ekranu (odległość od zaokrąglonej krawędzi). Pierścienie z linii
     * dawały widoczne prążki, a BlurMaskFilter nie działa z akceleracją sprzętową.
     */
    private fun rysujPoswiate(c: Canvas, szer: Float, wys: Float, styl: Styl, kolor: Int, promien: Float, t: Long, krycie: Float) {
        val klucz = if (styl.kolorPixel) 0 else kolor
        val shader = blask.takeIf { blaskDla == Triple(szer, wys, klucz) } ?: run {
            val (kolory, pozycje) = if (styl.kolorPixel) PIXEL to PIXEL_POZYCJE else odcienie(kolor) to null
            kolowy(szer / 2, wys / 2, kolory, pozycje).also { blask = it; blaskDla = Triple(szer, wys, klucz) }
        }
        macierz.setRotate(360f * (t % OBIEG_POSWIATY) / OBIEG_POSWIATY, szer / 2, wys / 2)
        shader.setLocalMatrix(macierz)
        barwa.shader = shader
        // Łagodny oddech jasności.
        val oddech = 0.85f + 0.15f * sin(2 * PI * (t % ODDECH_POSWIATY) / ODDECH_POSWIATY).toFloat()
        barwa.alpha = (255 * krycie * oddech).toInt()

        val m = maskaDla(szer, wys, minOf(szer, wys) * ZASIEGI[styl.zasieg - 1], promien)
        val warstwa = c.saveLayer(0f, 0f, szer, wys, null)
        c.drawRect(0f, 0f, szer, wys, barwa)
        celMaski.set(0f, 0f, szer, wys)
        c.drawBitmap(m, null, celMaski, wycinanie)
        c.restoreToCount(warstwa)
    }

    private fun maskaDla(szer: Float, wys: Float, zasieg: Float, promien: Float): Bitmap {
        val klucz = listOf(szer, wys, zasieg, promien)
        maska?.takeIf { maskaDla == klucz }?.let { return it }
        // Maska w zmniejszonej rozdzielczości — i tak jest gładka, a liczy się szybko.
        val skala = maxOf(1, (minOf(szer, wys) / 270f).toInt())
        val w = maxOf(1, (szer / skala).toInt()); val h = maxOf(1, (wys / skala).toInt())
        // Promień co najmniej równy zasięgowi — wtedy wewnętrzne kontury są zaokrąglone
        // i w rogach nie ma ukośnych „szwów” (przy kanciastych rogach ekranu były widoczne).
        val z = zasieg / skala; val r = minOf(maxOf(promien, zasieg * 1.2f) / skala, minOf(w, h) / 2f)
        val bw = w / 2f; val bh = h / 2f
        val piksele = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            // Odległość od zaokrąglonej krawędzi do środka (signed distance prostokąta z rogami).
            val qx = abs(x + 0.5f - bw) - (bw - r)
            val qy = abs(y + 0.5f - bh) - (bh - r)
            val zewn = sqrt(maxOf(qx, 0f).let { it * it } + maxOf(qy, 0f).let { it * it })
            val glebokosc = -(zewn + minOf(maxOf(qx, qy), 0f) - r)
            // Łagodnie jak na Pixelu: przy krawędzi ok. 60 %, potem długie, miękkie wygaszanie
            // (smoothstep), bez wyraźnej granicy, gdzie blask się kończy.
            val u = (glebokosc / z).coerceIn(0f, 1f)
            val a = (1f - u * u * (3f - 2f * u)).pow(1.3f) * 0.6f
            piksele[y * w + x] = (a * 255).toInt().toByte()
        }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        b.copyPixelsFromBuffer(ByteBuffer.wrap(piksele))
        maska?.recycle()
        maska = b; maskaDla = klucz
        return b
    }

    /** Jeden kolor, ale żywy: jaśniejszy i lekko przesunięte odcienie przenikają się dookoła. */
    private fun odcienie(k: Int): IntArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(k, hsv)
        fun z(dh: Float, ds: Float, dv: Float) = Color.HSVToColor(floatArrayOf(
            (hsv[0] + dh + 360f) % 360f, (hsv[1] + ds).coerceIn(0f, 1f), (hsv[2] + dv).coerceIn(0f, 1f)))
        return intArrayOf(k, z(18f, -0.15f, 0.2f), k, z(-22f, 0.05f, 0f), k)
    }

    /**
     * SweepGradient z zakładką na złączeniu 0°/360°. Bez niej sprzętowe rysowanie zostawiało
     * cienką ciemną kreskę od środka do krawędzi tam, gdzie gradient się zamyka.
     */
    private fun kolowy(cx: Float, cy: Float, kolory: IntArray, pozycje: FloatArray?): SweepGradient {
        val n = kolory.size
        val poz = pozycje ?: FloatArray(n) { it / (n - 1f) }
        val z = ZAKLADKA
        val k = IntArray(n + 2); val p = FloatArray(n + 2)
        k[0] = kolory[0]; p[0] = 0f
        for (i in 0 until n) { k[i + 1] = kolory[i]; p[i + 1] = z + poz[i] * (1 - 2 * z) }
        k[n + 1] = kolory[n - 1]; p[n + 1] = 1f
        return SweepGradient(cx, cy, k, p)
    }

    private fun teczaDo(szer: Float, wys: Float): SweepGradient {
        val t = tecza
        if (t != null && teczaDla == (szer to wys)) return t
        return kolowy(szer / 2, wys / 2, TECZA, null).also { tecza = it; teczaDla = szer to wys }
    }

    companion object {
        const val OKRES_PULSU = 1600L
        const val OBROT_TECZY = 2400L
        private const val ZAKLADKA = 0.01f
        const val OBIEG_POSWIATY = 9000L
        const val ODDECH_POSWIATY = 4000L
        /** Mnożnik zegara dla suwaka „Prędkość” 1..5 (3 = jak dotąd). */
        val TEMPO = floatArrayOf(0.4f, 0.65f, 1f, 1.6f, 2.5f)
        /** Głębokość poświaty jako część krótszego boku ekranu dla suwaka „Zasięg” 1..5. */
        val ZASIEGI = floatArrayOf(0.13f, 0.19f, 0.26f, 0.33f, 0.42f)
        /** Kolory jak na zrzucie z Pixela, zgodnie z ruchem wskazówek od prawej krawędzi. */
        val PIXEL = intArrayOf(
            0xFFF2B33D.toInt(), 0xFF8CC152.toInt(), 0xFF2FB8A8.toInt(), 0xFF3B82F6.toInt(),
            0xFF4F6BFF.toInt(), 0xFF4C9BFF.toInt(), 0xFFF07A45.toInt(), 0xFFF2B33D.toInt()
        )
        private val PIXEL_POZYCJE = floatArrayOf(0f, 0.12f, 0.28f, 0.45f, 0.62f, 0.78f, 0.9f, 1f)
        private val POSWIATA = arrayOf(3.2f to 0.10f, 2.4f to 0.16f, 1.7f to 0.24f)
        val TECZA = intArrayOf(
            0xFFFF3B6B.toInt(), 0xFFFFB020.toInt(), 0xFF3CE07A.toInt(),
            0xFF2FD3FF.toInt(), 0xFF5B6CFF.toInt(), 0xFFC84CFF.toInt(), 0xFFFF3B6B.toInt()
        )
    }
}
