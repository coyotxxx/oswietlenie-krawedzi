package pl.krawedzie.tester

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

/**
 * adb shell am broadcast --include-stopped-packages -n pl.krawedzie.tester/.Wyslij --es rodzaj <rodzaj> --ei id <n>
 * rodzaj: zwykle | ciche | stale | grupa | voip_dzwoni | voip_trwa | stary_dzwoni | stary_trwa
 * --ez anuluj true — usuwa powiadomienie o danym id.
 */
class Wyslij : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("glosny", "Głośne", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("cichy", "Ciche", NotificationManager.IMPORTANCE_LOW))
        val id = i.getIntExtra("id", 1)
        if (i.getBooleanExtra("anuluj", false)) { nm.cancel(id); return }
        val rodzaj = i.getStringExtra("rodzaj") ?: "zwykle"

        val otworz = PendingIntent.getActivity(
            ctx, id, Intent(ctx, Otwarte::class.java).putExtra("rodzaj", rodzaj),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(ctx, if (rodzaj == "ciche") "cichy" else "glosny")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Test: $rodzaj")
            .setContentText("powiadomienie testowe")
            .setContentIntent(otworz)
        val osoba = Person.Builder().setName("Jan Testowy").build()
        when (rodzaj) {
            "stale" -> b.setOngoing(true)
            "grupa" -> b.setGroup("g").setGroupSummary(true)
            "voip_dzwoni" -> b.setStyle(Notification.CallStyle.forIncomingCall(osoba, otworz, otworz))
                .setCategory(Notification.CATEGORY_CALL).setFullScreenIntent(otworz, true).setOngoing(true)
            "voip_trwa" -> b.setStyle(Notification.CallStyle.forOngoingCall(osoba, otworz))
                .setCategory(Notification.CATEGORY_CALL).setFullScreenIntent(otworz, true).setOngoing(true)
            "stary_dzwoni" -> b.setCategory(Notification.CATEGORY_CALL).setFullScreenIntent(otworz, true).setOngoing(true)
            "stary_trwa" -> b.setCategory(Notification.CATEGORY_CALL).setOngoing(true)
        }
        nm.notify(id, b.build())
    }
}

/** Ekran „otwarto z powiadomienia” — widać na zrzucie, że dotknięcie zadziałało. */
class Otwarte : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "OTWARTO: ${intent.getStringExtra("rodzaj")}"; textSize = 28f })
    }
}
