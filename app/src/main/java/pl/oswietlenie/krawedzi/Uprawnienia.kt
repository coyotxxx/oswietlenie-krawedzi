package pl.oswietlenie.krawedzi

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Stan trzech pozwoleń, bez których ramka nie zadziała, i drogi do ich ustawień. */
object Uprawnienia {
    fun powiadomienia(ctx: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun nakladka(ctx: Context): Boolean {
        val wlaczone = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val moja = ComponentName(ctx, UslugaRamki::class.java)
        return wlaczone.split(':').any { ComponentName.unflattenFromString(it) == moja }
    }

    fun bateria(ctx: Context) =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun wszystkie(ctx: Context) = powiadomienia(ctx) && nakladka(ctx) && bateria(ctx)

    fun otworzPowiadomienia(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun otworzNakladke(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    @SuppressLint("BatteryLife")
    fun otworzBaterie(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    /**
     * Android 13+ blokuje ułatwienia dostępu i dostęp do powiadomień dla aplikacji
     * zainstalowanych z pliku. Odblokowuje się to w informacjach o aplikacji:
     * ⋮ → „Zezwól na ustawienia z ograniczeniami”.
     */
    fun otworzInformacjeOAplikacji(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
