package pl.oswietlenie.krawedzi

import android.app.Notification
import android.app.NotificationManager
import android.media.AudioManager
import android.telecom.TelecomManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    override fun onListenerConnected() {
        ustawienia.value
        instancja = this
        Dziennik.dodaj(this, "nasłuch powiadomień połączony")
        wznowTu()
    }

    override fun onListenerDisconnected() {
        if (instancja === this) instancja = null
        Dziennik.dodaj(this, "nasłuch powiadomień ROZŁĄCZONY")
    }

    override fun onDestroy() { if (instancja === this) instancja = null; zakres.cancel(); super.onDestroy() }

    /**
     * Po restarcie usługi (system, brak pamięci, aktualizacja) ramka dzwonienia znikała,
     * choć telefon dalej dzwonił. Sprawdzamy aktywne powiadomienia i zapalamy ją znowu.
     */
    private fun wznowTu() {
        zakres.launch {
            // Prawdziwe ustawienia z dysku — zaraz po starcie stateIn ma jeszcze domyślne.
            val u = Magazyn(this@NasluchPowiadomien).ustawienia.first()
            if (!u.wlaczone || !u.dzwonienie) return@launch
            val lista = runCatching { activeNotifications }.getOrNull() ?: return@launch
            val dzwoni = lista.firstOrNull { it.packageName != packageName && Filtr.czyDzwoni(it.notification) }
                ?: dzwonekBezKategorii(lista) ?: return@launch
            Dziennik.dodaj(this@NasluchPowiadomien, "${opis(dzwoni)} → wciąż dzwoni, wznawiam ramkę")
            UslugaRamki.pokaz(zdarzenie(dzwoni, ciagle = true))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val u = ustawienia.value
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val opis = opis(sbn)
        if (!u.wlaczone) { zapisz("$opis → wyłączone w aplikacji"); return }

        if (Filtr.czyDzwoni(n)) {
            if (u.dzwonienie) { zapisz("$opis → DZWONI, świecę"); UslugaRamki.pokaz(zdarzenie(sbn, ciagle = true)) }
            else zapisz("$opis → dzwoni, ale „Dzwonienie” wyłączone")
            return
        }
        // Rozmowa odebrana — to samo powiadomienie zmienia się z „dzwoni” na „trwa”.
        if (n.category == Notification.CATEGORY_CALL) { zapisz("$opis → rozmowa nie dzwoni, gaszę"); UslugaRamki.zatrzymaj(sbn.key); return }

        val powod = when {
            !u.powiadomienia -> "„Powiadomienia” wyłączone"
            !u.swieciDla(sbn.packageName) -> "aplikacja niewybrana"
            !glosne(sbn.key) -> "ciche / Nie przeszkadzać"
            sbn.isOngoing -> "stałe (ongoing)"
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "podsumowanie grupy"
            else -> null
        }
        if (powod != null) { zapisz("$opis → pomijam: $powod"); return }
        zapisz("$opis → świecę")
        UslugaRamki.pokaz(zdarzenie(sbn, ciagle = false))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_CALL) zapisz("${opis(sbn)} → usunięte")
        UslugaRamki.zatrzymaj(sbn.key)
    }

    private fun zapisz(t: String) = Dziennik.dodaj(this, t)

    /** Krótki opis do dziennika — bez treści powiadomienia. */
    private fun opis(sbn: StatusBarNotification): String {
        val n = sbn.notification
        val b = StringBuilder(sbn.packageName.substringAfterLast('.'))
        n.category?.let { b.append(" kat=").append(it) }
        if (n.category == Notification.CATEGORY_CALL) {
            b.append(" typ=").append(n.extras.getInt("android.callType", 0))
            b.append(" fs=").append(if (n.fullScreenIntent != null) "tak" else "nie")
        }
        return b.toString()
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

    /**
     * Aplikacja Telefon z AOSP (np. Android 12) po pokazaniu ekranu odbierania podmienia
     * powiadomienie „dzwoni” na zwykłe, bez kategorii. Telefon dalej dzwoni — mówi to
     * tryb dźwięku — więc bierzemy stałe powiadomienie domyślnej aplikacji Telefon.
     */
    private fun dzwonekBezKategorii(lista: Array<StatusBarNotification>): StatusBarNotification? {
        val audio = getSystemService(AudioManager::class.java) ?: return null
        if (audio.mode != AudioManager.MODE_RINGTONE) return null
        val telefon = runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull() ?: return null
        return lista.firstOrNull { it.packageName == telefon && it.isOngoing }
    }

    companion object {
        @Volatile private var instancja: NasluchPowiadomien? = null

        /** Wołane, gdy usługa ramki (re)startuje — jeśli coś dzwoni, ramka wraca. */
        fun wznowDzwonienie() { instancja?.wznowTu() }

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
