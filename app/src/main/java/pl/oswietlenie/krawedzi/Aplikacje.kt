package pl.oswietlenie.krawedzi

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class Aplikacja(val pakiet: String, val nazwa: String, val ikona: Drawable)

/** Aplikacje z ikoną w menu — to z nich użytkownik wybiera, przy których świecić. */
fun listaAplikacji(ctx: Context): List<Aplikacja> {
    val pm = ctx.packageManager
    val zamiar = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(zamiar, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != ctx.packageName }
        .map { Aplikacja(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
        .sortedBy { it.nazwa.lowercase() }
}
