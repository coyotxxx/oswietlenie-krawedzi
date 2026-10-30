package pl.oswietlenie.krawedzi

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.RoundedCorner
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Rysuje ramkę nad całym ekranem.
 *
 * To usługa ułatwień dostępu tylko dlatego, że tylko jej okno ([WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY])
 * leży nad paskiem stanu, nawigacją i ekranem blokady. Zawartości ekranu nie czyta.
 */
class UslugaRamki : AccessibilityService() {
    private val zakres = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ustawienia by lazy {
        Magazyn(this).ustawienia.stateIn(zakres, SharingStarted.Eagerly, Ustawienia())
    }
    private val glowny = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    private var widok: WidokRamki? = null
    private var biezace: Zdarzenie? = null
    private var budzik: PowerManager.WakeLock? = null
    private var obudzilismy = false
    private var widokZTlem = false
    private val koniec = Runnable { schowaj(zablokuj = true) }

    override fun onServiceConnected() {
        instancja = this
        ustawienia.value
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instancja === this) instancja = null
        schowaj(zablokuj = false)
        zakres.cancel()
        super.onDestroy()
    }

    private fun pokazTu(z: Zdarzenie, proba: Boolean) {
        val u = ustawienia.value
        Log.d(NasluchPowiadomien.TAG, "pokaz ${z.pakiet} ciagle=${z.ciagle} ekranWl=${(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive}")
        // Dzwonienie ma pierwszeństwo — zwykłe powiadomienie go nie przerywa.
        if (biezace?.ciagle == true && !z.ciagle) return
        glowny.removeCallbacks(koniec)
        biezace = z

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val zgaszony = !pm.isInteractive
        if (zgaszony && (u.wybudzaj || proba)) obudz(pm)

        // Dotykalność okna zależy od czarnego tła — przy zmianie trzeba okno dodać od nowa.
        if (widok != null && widokZTlem != obudzilismy) { runCatching { wm.removeView(widok) }; widok = null }
        val w = widok ?: WidokRamki(this).also { dodaj(it); widok = it; widokZTlem = obudzilismy }
        w.ustaw(z, u.styl, promienRogu(), tlo = obudzilismy) { otworz(z) }

        if (!z.ciagle) glowny.postDelayed(koniec, u.styl.okres * 1000L)
    }

    private fun zatrzymajTu(klucz: String) {
        if (biezace?.klucz == klucz && biezace?.ciagle == true) schowaj(zablokuj = true)
    }

    private fun schowaj(zablokuj: Boolean) {
        glowny.removeCallbacks(koniec)
        widok?.let { runCatching { wm.removeView(it) } }
        widok = null
        biezace = null
        budzik?.let { if (it.isHeld) it.release() }
        budzik = null
        // Ekran zapaliliśmy my — gasimy go z powrotem, żeby nie świecił w kieszeni.
        if (obudzilismy && zablokuj && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
        obudzilismy = false
    }

    private fun otworz(z: Zdarzenie) {
        obudzilismy = false   // użytkownik sięgnął po telefon — nie gasimy mu ekranu
        schowaj(zablokuj = false)
        runCatching { z.otworz?.send() }
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun obudz(pm: PowerManager) {
        if (budzik?.isHeld == true) return
        budzik = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "krawedzie:efekt"
        ).also { it.acquire(MAKS_SWIECENIA_MS) }
        obudzilismy = true
    }

    private fun dodaj(w: WidokRamki) {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) p.fitInsetsTypes = 0
        // Bez czarnego tła ramka nie może zabierać dotyku — telefon ma działać normalnie.
        if (!obudzilismy) p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.addView(w, p)
    }

    private fun promienRogu(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = wm.currentWindowMetrics.windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            if (r != null && r.radius > 0) return r.radius.toFloat()
        }
        return ZAPASOWY_PROMIEN_DP * resources.displayMetrics.density
    }

    companion object {
        @Volatile private var instancja: UslugaRamki? = null
        private const val MAKS_SWIECENIA_MS = 60_000L
        const val ZAPASOWY_PROMIEN_DP = 36f

        val dziala: Boolean get() = instancja != null

        fun pokaz(z: Zdarzenie, proba: Boolean = false) {
            val s = instancja ?: return
            s.glowny.post { s.pokazTu(z, proba) }
        }

        fun zatrzymaj(klucz: String) {
            val s = instancja ?: return
            s.glowny.post { s.zatrzymajTu(klucz) }
        }
    }
}
