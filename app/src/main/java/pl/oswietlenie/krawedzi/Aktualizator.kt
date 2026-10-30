package pl.oswietlenie.krawedzi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long
)

/** Postęp aktualizacji — z tego rysujemy pasek na ekranie. */
sealed interface UpdateProgress {
    data object Idle : UpdateProgress
    data object Checking : UpdateProgress
    data class Downloading(val done: Long, val total: Long) : UpdateProgress {
        val percent: Int get() = if (total > 0) ((done * 100) / total).toInt() else 0
    }
    data object Installing : UpdateProgress
    data class Failed(val message: String) : UpdateProgress
}

/**
 * Aktualizacja z wydań na GitHubie. Repo jest publiczne, więc bez żadnego tokena
 * — nic wrażliwego nie musi siedzieć w APK.
 */
object Updater {

    const val TAG = "KrawedzieUpdate"
    const val REPO = "coyotxxx/oswietlenie-krawedzi"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val UA = "Krawedzie-Android"

    /** true, gdy ostatnie sprawdzenie nie dostało odpowiedzi z GitHuba — wtedy nie mówimy „masz najnowszą”. */
    @Volatile var ostatnioBlad = false
        private set

    /** @return informacja o nowszym wydaniu albo null, gdy jesteśmy na bieżąco. */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        ostatnioBlad = true
        val body = runCatching { get(API) }.getOrElse {
            Log.w(TAG, "nie udało się sprawdzić wydań: ${it.message}")
            return@withContext null
        } ?: return@withContext null
        ostatnioBlad = false

        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        val tag = root.optString("tag_name").ifBlank { return@withContext null }
        val latest = tag.removePrefix("v")
        if (!isNewer(latest, currentVersion)) {
            Log.i(TAG, "wersja aktualna ($currentVersion, najnowsza $latest)")
            return@withContext null
        }

        val assets = root.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                Log.i(TAG, "dostępna aktualizacja $latest")
                return@withContext UpdateInfo(
                    version = latest,
                    apkUrl = a.optString("browser_download_url"),
                    notes = root.optString("body").trim(),
                    sizeBytes = a.optLong("size")
                )
            }
        }
        Log.w(TAG, "wydanie $latest nie ma pliku APK")
        null
    }

    /**
     * Pobiera APK i oddaje go systemowemu instalatorowi.
     * @param onProgress wołane w trakcie pobierania — stąd bierze się pasek postępu.
     */
    suspend fun downloadAndInstall(
        ctx: Context,
        info: UpdateInfo,
        onProgress: (UpdateProgress) -> Unit
    ): Boolean {
        if (!canInstall(ctx)) {
            onProgress(UpdateProgress.Failed("Zezwól na instalowanie aplikacji z tego źródła"))
            openInstallPermission(ctx)
            return false
        }

        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }          // stare pobrania nie zaśmiecają
                val out = File(dir, "krawedzie-${info.version}.apk")

                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", UA)
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { input ->
                    val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
                    var done = 0L
                    var lastPct = -1
                    out.outputStream().use { fos ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(UpdateProgress.Downloading(done, total))
                            }
                        }
                    }
                }
                conn.disconnect()
                if (out.length() < 1024) error("pobrany plik jest pusty")
                out
            }.getOrElse {
                Log.e(TAG, "pobieranie nie powiodło się", it)
                null
            }
        }

        if (file == null) {
            onProgress(UpdateProgress.Failed("Nie udało się pobrać aktualizacji. Sprawdź internet."))
            return false
        }

        onProgress(UpdateProgress.Installing)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { ctx.startActivity(intent); true }.getOrElse {
            Log.e(TAG, "instalator nie wystartował", it)
            onProgress(UpdateProgress.Failed("Nie udało się uruchomić instalatora"))
            false
        }
    }

    /** Android wymaga osobnej zgody na instalowanie plików spoza sklepu. */
    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) true
        else ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Porównanie wersji po członach: 1.10.0 jest nowsze niż 1.9.9. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim().split(".", "-", "_")
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val l = parts(latest)
        val c = parts(current)
        if (l.isEmpty()) return false
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", UA)
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
