package pl.oswietlenie.krawedzi

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Efekt { PODSTAWOWY, MIGOTANIE }

/**
 * Wygląd ramki. Te same pola co trzy zakładki Samsunga: Efekty, Kolor, Zaawansowane.
 *
 * [okres] to czas świecenia przy powiadomieniu w sekundach — u Samsunga suwak „Okres”
 * od Krótko do Długo. Dzwonienie świeci tak długo, jak dzwoni, niezależnie od okresu.
 */
data class Styl(
    val efekt: Efekt = Efekt.MIGOTANIE,
    val kolorAuto: Boolean = true,
    val kolor: Int = 0xFF3D9BFF.toInt(),
    val przezroczystosc: Int = 0,   // 0..80 %
    val szerokosc: Int = 5,         // dp, 2..14
    val okres: Int = 5              // s, 2..10
) {
    companion object {
        val PRZEZROCZYSTOSC = 0..80
        val SZEROKOSC = 2..14
        val OKRES = 2..10
    }
}

data class Ustawienia(
    val wlaczone: Boolean = true,
    val powiadomienia: Boolean = true,
    val dzwonienie: Boolean = true,
    val wybudzaj: Boolean = true,
    val wszystkieAplikacje: Boolean = true,
    val wybrane: Set<String> = emptySet(),
    val styl: Styl = Styl()
) {
    fun swieciDla(pakiet: String) = wszystkieAplikacje || pakiet in wybrane
}

private val Context.magazyn by preferencesDataStore("ustawienia")

/** Zapis i odczyt ustawień. Wszystko lokalnie na telefonie. */
class Magazyn(private val ctx: Context) {
    private object K {
        val wlaczone = booleanPreferencesKey("wlaczone")
        val powiadomienia = booleanPreferencesKey("powiadomienia")
        val dzwonienie = booleanPreferencesKey("dzwonienie")
        val wybudzaj = booleanPreferencesKey("wybudzaj")
        val wszystkie = booleanPreferencesKey("wszystkie")
        val wybrane = stringSetPreferencesKey("wybrane")
        val efekt = stringPreferencesKey("efekt")
        val kolorAuto = booleanPreferencesKey("kolor_auto")
        val kolor = intPreferencesKey("kolor")
        val przezr = intPreferencesKey("przezroczystosc")
        val szer = intPreferencesKey("szerokosc")
        val okres = intPreferencesKey("okres")
    }

    val ustawienia: Flow<Ustawienia> = ctx.magazyn.data.map { p ->
        val d = Ustawienia()
        Ustawienia(
            wlaczone = p[K.wlaczone] ?: d.wlaczone,
            powiadomienia = p[K.powiadomienia] ?: d.powiadomienia,
            dzwonienie = p[K.dzwonienie] ?: d.dzwonienie,
            wybudzaj = p[K.wybudzaj] ?: d.wybudzaj,
            wszystkieAplikacje = p[K.wszystkie] ?: d.wszystkieAplikacje,
            wybrane = p[K.wybrane] ?: d.wybrane,
            styl = Styl(
                efekt = p[K.efekt]?.let { runCatching { Efekt.valueOf(it) }.getOrNull() } ?: d.styl.efekt,
                kolorAuto = p[K.kolorAuto] ?: d.styl.kolorAuto,
                kolor = p[K.kolor] ?: d.styl.kolor,
                przezroczystosc = (p[K.przezr] ?: d.styl.przezroczystosc).coerceIn(Styl.PRZEZROCZYSTOSC),
                szerokosc = (p[K.szer] ?: d.styl.szerokosc).coerceIn(Styl.SZEROKOSC),
                okres = (p[K.okres] ?: d.styl.okres).coerceIn(Styl.OKRES)
            )
        )
    }

    suspend fun zapisz(u: Ustawienia) {
        ctx.magazyn.edit { p ->
            p[K.wlaczone] = u.wlaczone
            p[K.powiadomienia] = u.powiadomienia
            p[K.dzwonienie] = u.dzwonienie
            p[K.wybudzaj] = u.wybudzaj
            p[K.wszystkie] = u.wszystkieAplikacje
            p[K.wybrane] = u.wybrane
            p[K.efekt] = u.styl.efekt.name
            p[K.kolorAuto] = u.styl.kolorAuto
            p[K.kolor] = u.styl.kolor
            p[K.przezr] = u.styl.przezroczystosc
            p[K.szer] = u.styl.szerokosc
            p[K.okres] = u.styl.okres
        }
    }
}
