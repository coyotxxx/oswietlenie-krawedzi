package pl.oswietlenie.krawedzi

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dziennik ostatnich zdarzeń: co przyszło i dlaczego ramka zapaliła się albo zgasła.
 * Służy do diagnozy na telefonie, którego logów nie widać. Bez treści powiadomień —
 * tylko pakiet, rodzaj i decyzja.
 */
object Dziennik {
    private const val MAKS = 200
    private const val PLIK = "dziennik.txt"
    private val czas = SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT)
    private val wpisy = ArrayDeque<String>()
    private var wczytany = false

    @Synchronized
    fun dodaj(ctx: Context, tekst: String) {
        wczytaj(ctx)
        wpisy.addLast("${czas.format(Date())}  $tekst")
        while (wpisy.size > MAKS) wpisy.removeFirst()
        runCatching { File(ctx.filesDir, PLIK).writeText(wpisy.joinToString("\n")) }
    }

    /** Najnowsze na górze. */
    @Synchronized
    fun lista(ctx: Context): List<String> { wczytaj(ctx); return wpisy.reversed() }

    @Synchronized
    fun wyczysc(ctx: Context) {
        wpisy.clear(); wczytany = true
        runCatching { File(ctx.filesDir, PLIK).delete() }
    }

    private fun wczytaj(ctx: Context) {
        if (wczytany) return
        wczytany = true
        runCatching { File(ctx.filesDir, PLIK).takeIf { it.exists() }?.readLines()?.takeLast(MAKS)?.let { wpisy.addAll(it) } }
    }
}
