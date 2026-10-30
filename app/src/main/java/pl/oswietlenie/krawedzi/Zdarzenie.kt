package pl.oswietlenie.krawedzi

import android.app.PendingIntent
import android.graphics.drawable.Icon

/**
 * Coś, przy czym ma zaświecić ramka.
 * [ciagle] = dzwonienie: świeci, dopóki nie zostanie zatrzymane, a nie przez [Styl.okres].
 */
data class Zdarzenie(
    val klucz: String,
    val pakiet: String,
    val kolor: Int,
    val tytul: String? = null,
    val tekst: String? = null,
    val ikona: Icon? = null,
    val otworz: PendingIntent? = null,
    val ciagle: Boolean = false
)
