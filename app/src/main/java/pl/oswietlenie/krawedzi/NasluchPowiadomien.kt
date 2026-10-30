package pl.oswietlenie.krawedzi

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Dowiaduje się o powiadomieniach i decyduje, czy zaświecić. Samo nic nie rysuje —
 * przekazuje [Zdarzenie] do [UslugaRamki].
 */
class NasluchPowiadomien : NotificationListenerService() {
    private val zakres = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ustawienia by lazy {
        Magazyn(this).ustawienia.stateIn(zakres, SharingStarted.Eagerly, Ustawienia())
    }

    override fun onListenerConnected() { ustawienia.value }

    override fun onDestroy() { zakres.cancel(); super.onDestroy() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val u = ustawienia.value
        if (!u.wlaczone || sbn.packageName == packageName) return
        val n = sbn.notification

        if (Filtr.czyDzwoni(n)) {
            if (u.dzwonienie) UslugaRamki.pokaz(zdarzenie(sbn, ciagle = true))
            return
        }
        // Rozmowa odebrana — to samo powiadomienie zmienia się z „dzwoni” na „trwa”.
        if (n.category == Notification.CATEGORY_CALL) { UslugaRamki.zatrzymaj(sbn.key); return }

        if (!u.powiadomienia || !u.swieciDla(sbn.packageName)) return
        if (!Filtr.czySwiecic(sbn, glosne(sbn.key))) return
        UslugaRamki.pokaz(zdarzenie(sbn, ciagle = false))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        UslugaRamki.zatrzymaj(sbn.key)
    }

    /**
     * Czy powiadomienie naprawdę alarmuje: nie jest ciche i przechodzi przez tryb Nie przeszkadzać.
     * Ciche (muzyka w tle, pobieranie) ramki nie zapalają — tak jak nie dają dźwięku.
     */
    private fun glosne(klucz: String): Boolean {
        val r = Ranking()
        if (currentRanking?.getRanking(klucz, r) != true) return true
        return r.matchesInterruptionFilter() && r.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun zdarzenie(sbn: StatusBarNotification, ciagle: Boolean): Zdarzenie {
        val n = sbn.notification
        val e = n.extras
        val styl = ustawienia.value.styl
        val kolor = if (styl.kolorAuto) KolorAplikacji.dla(this, sbn.packageName, n.color) else styl.kolor
        return Zdarzenie(
            klucz = sbn.key,
            pakiet = sbn.packageName,
            kolor = if (ciagle && styl.kolorAuto) KOLOR_DZWONIENIA else kolor,
            tytul = e.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            tekst = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            ikona = n.smallIcon,
            otworz = n.contentIntent,
            ciagle = ciagle
        )
    }

    companion object {
        const val TAG = "Krawedzie"
        const val KOLOR_DZWONIENIA = 0xFF2ED69B.toInt()
    }
}

/** Reguły „czy świecić” wydzielone, żeby dało się je przetestować bez telefonu. */
object Filtr {
    /** Typ rozmowy z CallStyle (Android 12+): 1 = przychodząca, 2 = trwająca, 3 = filtrowana. */
    private const val TYP_PRZYCHODZACA = 1

    fun czyDzwoni(n: Notification): Boolean {
        if (n.category != Notification.CATEGORY_CALL) return false
        val typ = n.extras.getInt("android.callType", 0)
        if (typ != 0) return typ == TYP_PRZYCHODZACA
        // Starsze dialery bez CallStyle: dzwoniące połączenie ma pełnoekranowy ekran odbierania.
        return n.fullScreenIntent != null
    }

    fun czySwiecic(sbn: StatusBarNotification, glosne: Boolean): Boolean {
        val n = sbn.notification
        if (!glosne) return false
        if (sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return true
    }
}
