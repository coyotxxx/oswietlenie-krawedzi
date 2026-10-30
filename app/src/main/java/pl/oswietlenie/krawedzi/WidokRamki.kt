package pl.oswietlenie.krawedzi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

/**
 * Okno nakładki: ramka, a przy wybudzonym ekranie także czarne tło i dymek
 * z ikoną i treścią powiadomienia. Dotknięcie czarnego tła otwiera powiadomienie.
 */
@SuppressLint("ViewConstructor")
class WidokRamki(ctx: Context) : View(ctx) {
    private val rysownik = Rysownik()
    private val gestosc = resources.displayMetrics.density
    private var start = SystemClock.uptimeMillis()
    private var z: Zdarzenie? = null
    private var styl = Styl()
    private var promien = 0f
    private var tlo = false
    private var naDotyk: (() -> Unit)? = null
    // Przesunięcie o 0–2 px przy każdym pokazaniu — ramka nie stoi zawsze w tym samym miejscu (OLED).
    private var przes = 0f

    private val dymek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9ECF2.toInt() }
    private val tytulP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A505A.toInt(); textSize = 12 * gestosc; isFakeBoldText = true }
    private val tekstP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1D23.toInt(); textSize = 15 * gestosc }
    private val ramkaDymka = RectF()

    fun ustaw(z: Zdarzenie, styl: Styl, promien: Float, tlo: Boolean, naDotyk: () -> Unit) {
        if (this.z?.klucz != z.klucz) { start = SystemClock.uptimeMillis(); przes = Random.nextInt(0, 3).toFloat() }
        this.z = z; this.styl = styl; this.promien = promien; this.tlo = tlo; this.naDotyk = naDotyk
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val zd = z ?: return
        if (tlo) c.drawColor(Color.BLACK)
        val gr = styl.szerokosc * gestosc
        c.save()
        c.translate(przes, przes)
        rysownik.rysuj(c, width - 2 * przes, height - 2 * przes, styl, zd.kolor, gr, promien, SystemClock.uptimeMillis() - start)
        c.restore()
        if (tlo) rysujDymek(c, zd)
        postInvalidateOnAnimation()
    }

    private fun rysujDymek(c: Canvas, zd: Zdarzenie) {
        val tekst = zd.tekst ?: zd.tytul ?: return
        val maks = width * 0.78f
        val wys = 46 * gestosc
        val ikona = 30 * gestosc
        val margines = 8 * gestosc
        val szerTekstu = maxOf(tekstP.measureText(tekst), tytulP.measureText(zd.tytul ?: ""))
        val szer = (margines * 3 + ikona + szerTekstu + 10 * gestosc).coerceAtMost(maks)
        val lewo = (width - szer) / 2
        val gora = 44 * gestosc
        ramkaDymka.set(lewo, gora, lewo + szer, gora + wys)
        c.drawRoundRect(ramkaDymka, wys / 2, wys / 2, dymek)

        zd.ikona?.loadDrawable(context)?.let { d ->
            val l = (lewo + margines).toInt(); val t = (gora + (wys - ikona) / 2).toInt()
            d.setBounds(l, t, l + ikona.toInt(), t + ikona.toInt())
            d.setTint(zd.kolor)
            d.draw(c)
        }
        val x = lewo + margines * 2 + ikona
        val dostepne = szer - (x - lewo) - margines * 2
        if (zd.tytul != null && zd.tekst != null) {
            c.drawText(skroc(zd.tytul, tytulP, dostepne), x, gora + wys * 0.42f, tytulP)
            c.drawText(skroc(zd.tekst, tekstP, dostepne), x, gora + wys * 0.80f, tekstP)
        } else {
            c.drawText(skroc(tekst, tekstP, dostepne), x, gora + wys * 0.62f, tekstP)
        }
    }

    private fun skroc(s: String, p: Paint, szer: Float): String =
        TextUtils.ellipsize(s, android.text.TextPaint(p), szer, TextUtils.TruncateAt.END).toString()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (tlo && e.action == MotionEvent.ACTION_UP) naDotyk?.invoke()
        return tlo
    }
}
