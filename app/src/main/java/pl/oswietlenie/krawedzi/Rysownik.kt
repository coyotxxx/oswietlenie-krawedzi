package pl.oswietlenie.krawedzi

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.cos

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
        val pol = grubosc / 2f
        prostokat.set(pol, pol, szer - pol, wys - pol)
        val r = (promien - pol).coerceAtLeast(0f)

        val shader: Shader?
        val puls: Float
        when (styl.efekt) {
            Efekt.PODSTAWOWY -> {
                shader = null
                // Łagodne wdech-wydech, jeden oddech na 1,6 s.
                puls = 0.35f + 0.65f * (0.5f - 0.5f * cos(2 * PI * (czasMs % OKRES_PULSU) / OKRES_PULSU).toFloat())
            }
            Efekt.MIGOTANIE -> {
                shader = teczaDo(szer, wys).also {
                    macierz.setRotate(360f * (czasMs % OBROT_TECZY) / OBROT_TECZY, szer / 2, wys / 2)
                    it.setLocalMatrix(macierz)
                }
                puls = 1f
            }
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

    private fun teczaDo(szer: Float, wys: Float): SweepGradient {
        val t = tecza
        if (t != null && teczaDla == (szer to wys)) return t
        return SweepGradient(szer / 2, wys / 2, TECZA, null).also { tecza = it; teczaDla = szer to wys }
    }

    companion object {
        const val OKRES_PULSU = 1600L
        const val OBROT_TECZY = 2400L
        private val POSWIATA = arrayOf(3.2f to 0.10f, 2.4f to 0.16f, 1.7f to 0.24f)
        val TECZA = intArrayOf(
            0xFFFF3B6B.toInt(), 0xFFFFB020.toInt(), 0xFF3CE07A.toInt(),
            0xFF2FD3FF.toInt(), 0xFF5B6CFF.toInt(), 0xFFC84CFF.toInt(), 0xFFFF3B6B.toInt()
        )
    }
}
