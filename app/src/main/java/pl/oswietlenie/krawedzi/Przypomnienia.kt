package pl.oswietlenie.krawedzi

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Przypomnienia o powiadomieniach, które przyszły przy zgaszonym ekranie i których jeszcze
 * nie widziałeś: co [Ustawienia.przypomnienieCoMin] minut ramka zapala się na chwilę na czarnym ekranie.
 *
 * Koniec, gdy powiadomienie zniknie z paska (otwarte, odrzucone), gdy sam włączysz ekran
 * (widziałeś ekran blokady) albo gdy minie [Ustawienia.przypomnienieMaksMin].
 * Bez tego ostatniego warunku stare, zapomniane powiadomienia budziłyby telefon całą noc.
 *
 * Odliczanie robi AlarmManager — zwykły Handler w głębokim uśpieniu telefonu stoi.
 * Stan w SharedPreferences, żeby przeżył restart usług.
 */
object Przypomnienia {
    val CO_ILE = listOf(1, 2, 5, 10, 15, 30)
    /** 0 = aż spojrzysz na telefon */
    val JAK_DLUGO = listOf(30, 60, 120, 0)
    private const val PLIK = "przypomnienia"
    private const val KOD = 7

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PLIK, Context.MODE_PRIVATE)

    /** klucz powiadomienia → kiedy przyszło (System.currentTimeMillis) */
    fun oczekujace(ctx: Context): Map<String, Long> =
        prefs(ctx).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    @Synchronized
    fun dodaj(ctx: Context, klucz: String, coMin: Int) {
        val p = prefs(ctx)
        if (p.contains(klucz)) return
        val pierwsze = p.all.isEmpty()
        p.edit().putLong(klucz, System.currentTimeMillis()).apply()
        // Kolejne powiadomienie nie przesuwa już zaplanowanego przypomnienia.
        if (pierwsze) zaplanuj(ctx, coMin)
    }

    @Synchronized
    fun usun(ctx: Context, klucz: String) {
        val p = prefs(ctx)
        if (!p.contains(klucz)) return
        p.edit().remove(klucz).apply()
        if (p.all.isEmpty()) { anuluj(ctx); Dziennik.dodaj(ctx, "przypomnienia: koniec — powiadomienie usunięte") }
    }

    @Synchronized
    fun wyczysc(ctx: Context, powod: String) {
        val p = prefs(ctx)
        if (p.all.isEmpty()) return
        p.edit().clear().apply()
        anuluj(ctx)
        Dziennik.dodaj(ctx, "przypomnienia: koniec — $powod")
    }

    /** Po restarcie telefonu lub usługi budzik systemowy znika — planujemy od nowa. */
    fun wznow(ctx: Context, u: Ustawienia) {
        if (oczekujace(ctx).isNotEmpty() && u.przypominaj) zaplanuj(ctx, u.przypomnienieCoMin)
    }

    fun zaplanuj(ctx: Context, coMin: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val kiedy = SystemClock.elapsedRealtime() + coMin * 60_000L
        val pi = intencja(ctx)
        // Dokładny budzik działa też w trybie uśpienia (Doze). Bez zgody — przybliżony.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, kiedy, pi)
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, kiedy, pi)
    }

    private fun anuluj(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(intencja(ctx))
    }

    private fun intencja(ctx: Context) = PendingIntent.getBroadcast(
        ctx, KOD, Intent(ctx, Przypominacz::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Wołane przez budzik, na głównym wątku. */
    fun wykonaj(ctx: Context, u: Ustawienia) {
        if (oczekujace(ctx).isEmpty()) return
        if (!u.wlaczone || !u.powiadomienia || !u.przypominaj) { wyczysc(ctx, "przypomnienia wyłączone"); return }

        // Porządki: przeterminowane i te, których już nie ma na pasku.
        val teraz = System.currentTimeMillis()
        val aktywne = NasluchPowiadomien.aktywneKlucze()
        val p = prefs(ctx)
        val stare = oczekujace(ctx).filter { (k, kiedy) ->
            (u.przypomnienieMaksMin > 0 && teraz - kiedy > u.przypomnienieMaksMin * 60_000L) ||
                (aktywne != null && k !in aktywne)
        }
        if (stare.isNotEmpty()) p.edit().apply { stare.keys.forEach { remove(it) } }.apply()
        val zostaly = oczekujace(ctx)
        if (zostaly.isEmpty()) { anuluj(ctx); Dziennik.dodaj(ctx, "przypomnienia: koniec — minął czas lub powiadomienia zniknęły"); return }

        zaplanuj(ctx, u.przypomnienieCoMin)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val audio = ctx.getSystemService(AudioManager::class.java)
        val powod = when {
            pm?.isInteractive == true -> "ekran włączony"
            (nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) > NotificationManager.INTERRUPTION_FILTER_ALL -> "Nie przeszkadzać"
            (audio?.mode ?: AudioManager.MODE_NORMAL) != AudioManager.MODE_NORMAL -> "trwa rozmowa"
            !UslugaRamki.dziala -> "usługa ramki nie działa"
            UslugaRamki.swieci -> "ramka już świeci"
            else -> null
        }
        if (powod != null) { Dziennik.dodaj(ctx, "przypomnienie: pomijam — $powod"); return }

        val klucz = zostaly.maxBy { it.value }.key
        if (NasluchPowiadomien.przypomnij(klucz)) Dziennik.dodaj(ctx, "przypomnienie: świecę (czeka ${zostaly.size}), następne za ${u.przypomnienieCoMin} min")
        else Dziennik.dodaj(ctx, "przypomnienie: nasłuch powiadomień niepołączony")
    }
}

/** Odbiera budzik przypomnienia. */
class Przypominacz : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val wynik = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val u = Magazyn(app).ustawienia.first()
                withContext(Dispatchers.Main) { Przypomnienia.wykonaj(app, u) }
            } finally {
                // Ramka zapala się w kolejce głównego wątku — kończymy po niej, żeby telefon
                // nie zasnął między budzikiem a zapaleniem ekranu.
                Handler(Looper.getMainLooper()).post { wynik.finish() }
            }
        }
    }
}
