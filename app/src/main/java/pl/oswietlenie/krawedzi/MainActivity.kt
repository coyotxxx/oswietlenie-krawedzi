package pl.oswietlenie.krawedzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Kolory z makiety
private val Tlo = Color(0xFF0B0D11)
private val Karta = Color(0xFF151920)
private val Linia = Color(0xFF232934)
private val Tekst = Color(0xFFECEEF2)
private val Szary = Color(0xFF9BA3AF)
private val Akcent = Color(0xFF3D7BFF)
private val AkcentTlo = Color(0xFF13264A)
private val Ok = Color(0xFF4FD18B)
private val Wylaczony = Color(0xFF3A404B)

private enum class Ekran { GLOWNY, STYL, APLIKACJE, UPRAWNIENIA }

private val PALETA = listOf(
    0xFF3D9BFF, 0xFFFF5C8A, 0xFFFF3B3B, 0xFFFF8A1F, 0xFFA6E05A, 0xFF2ED69B,
    0xFF2FD3E6, 0xFF4CB8FF, 0xFF2F5BFF, 0xFFFF9CC8, 0xFF9B4CFF, 0xFFFFFFFF
).map { it.toInt() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Aplikacja jest zawsze ciemna — jasne ikony paska stanu i nawigacji.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val magazyn = Magazyn(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Akcent, background = Tlo, surface = Karta)) {
                Aplikacja(magazyn)
            }
        }
    }
}

@Composable
private fun Aplikacja(magazyn: Magazyn) {
    val ctx = LocalContext.current
    val u by magazyn.ustawienia.collectAsStateWithLifecycle(initialValue = null)
    val zakres = rememberCoroutineScope()
    var ekran by remember { mutableStateOf(Ekran.GLOWNY) }

    // Stan uprawnień odświeżany po każdym powrocie z ustawień systemu.
    var uprawnienia by remember { mutableStateOf(Triple(false, false, false)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        uprawnienia = Triple(Uprawnienia.powiadomienia(ctx), Uprawnienia.nakladka(ctx), Uprawnienia.bateria(ctx))
        if (!(uprawnienia.first && uprawnienia.second) && ekran == Ekran.GLOWNY) ekran = Ekran.UPRAWNIENIA
    }

    // Aktualizacje z GitHuba: sprawdzamy raz przy starcie, ręcznie — przyciskiem na dole.
    var aktualizacja by remember { mutableStateOf<UpdateInfo?>(null) }
    var postep by remember { mutableStateOf<UpdateProgress>(UpdateProgress.Idle) }
    var sprawdzono by remember { mutableStateOf(false) }
    val sprawdz: () -> Unit = {
        if (postep !is UpdateProgress.Checking && postep !is UpdateProgress.Downloading) zakres.launch {
            postep = UpdateProgress.Checking
            aktualizacja = Updater.check(BuildConfig.VERSION_NAME)
            postep = UpdateProgress.Idle
            sprawdzono = true
        }
    }
    LaunchedEffect(Unit) { sprawdz() }
    val instaluj: () -> Unit = {
        aktualizacja?.let { info ->
            zakres.launch { Updater.downloadAndInstall(ctx, info) { postep = it } }
        }
    }
    // Wróciliśmy z instalatora albo z ustawienia „instaluj nieznane aplikacje” — pasek znów aktywny.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (postep is UpdateProgress.Installing) postep = UpdateProgress.Idle
    }

    val ust = u ?: return
    val zapisz: (Ustawienia) -> Unit = { nowe -> zakres.launch { magazyn.zapisz(nowe) } }
    BackHandler(enabled = ekran != Ekran.GLOWNY) { ekran = Ekran.GLOWNY }

    Box(Modifier.fillMaxSize().background(Tlo)) {
        when (ekran) {
            Ekran.GLOWNY -> Glowny(ust, uprawnienia, zapisz, StanAktualizacji(aktualizacja, postep, sprawdzono, sprawdz, instaluj)) { ekran = it }
            Ekran.STYL -> EdytorStylu(ust.styl, onAnuluj = { ekran = Ekran.GLOWNY }) {
                zapisz(ust.copy(styl = it)); ekran = Ekran.GLOWNY
            }
            Ekran.APLIKACJE -> WyborAplikacji(ust, zapisz) { ekran = Ekran.GLOWNY }
            Ekran.UPRAWNIENIA -> EkranUprawnien(uprawnienia) { ekran = Ekran.GLOWNY }
        }
    }
}

// ───────────────────────── Ekran główny ─────────────────────────

@Composable
private fun Glowny(
    u: Ustawienia, upr: Triple<Boolean, Boolean, Boolean>, zapisz: (Ustawienia) -> Unit,
    akt: StanAktualizacji, idz: (Ekran) -> Unit
) {
    val ctx = LocalContext.current
    var komunikat by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Oświetlenie krawędzi", color = Tekst, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Ramka świetlna przy powiadomieniach i dzwonieniu", color = Szary, fontSize = 14.sp)
        }

        akt.info?.let { PasekAktualizacji(it, akt.postep, akt.instaluj) }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(if (u.wlaczone) AkcentTlo else Karta)
                .border(1.dp, if (u.wlaczone) Color(0xFF2D5BB8) else Linia, RoundedCornerShape(22.dp))
                .clickable { zapisz(u.copy(wlaczone = !u.wlaczone)) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (u.wlaczone) "Włączone" else "Wyłączone", color = Tekst, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(u.wlaczone, { zapisz(u.copy(wlaczone = it)) }, colors = kolorySwitcha())
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Karta)
                .border(1.dp, Linia, RoundedCornerShape(22.dp)).clickable { idz(Ekran.STYL) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Podglad(u.styl, Modifier.size(62.dp, 96.dp), promienDp = 18f, mini = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("STYL", color = Szary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(nazwa(u.styl.efekt), color = Tekst, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "szerokość ${u.styl.szerokosc} · przezroczystość ${u.styl.przezroczystosc} % · ${u.styl.okres} s",
                    color = Szary, fontSize = 13.sp
                )
            }
            Text("›", color = Szary, fontSize = 26.sp)
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Karta).border(1.dp, Linia, RoundedCornerShape(22.dp))) {
            Text("KIEDY ŚWIECIĆ", color = Szary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp))
            Przelacznik("Powiadomienia", "z wybranych aplikacji", u.powiadomienia) { zapisz(u.copy(powiadomienia = it)) }
            Przelacznik("Dzwonienie", "przez cały czas dzwonienia", u.dzwonienie) { zapisz(u.copy(dzwonienie = it)) }
            Przelacznik("Przy zgaszonym ekranie", "wybudź ekran na czas efektu", u.wybudzaj) { zapisz(u.copy(wybudzaj = it)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kafel(
                "Aplikacje",
                if (u.wszystkieAplikacje) "wszystkie" else "${u.wybrane.size} wybranych",
                Szary, Modifier.weight(1f)
            ) { idz(Ekran.APLIKACJE) }
            val ok = upr.first && upr.second && upr.third
            Kafel("Uprawnienia", if (ok) "wszystko działa" else "do ustawienia", if (ok) Ok else Color(0xFFFFB020), Modifier.weight(1f)) { idz(Ekran.UPRAWNIENIA) }
        }

        Button(
            onClick = {
                if (!UslugaRamki.dziala) { komunikat = "Najpierw włącz uprawnienia — bez nich ramka się nie pokaże."; return@Button }
                komunikat = null
                val kolor = if (u.styl.kolorAuto) KolorAplikacji.ZAPASOWY else u.styl.kolor
                UslugaRamki.pokaz(
                    Zdarzenie("proba", ctx.packageName, kolor, "Krawędzie", "Tak zaświeci przy powiadomieniu"),
                    proba = true
                )
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Akcent, contentColor = Color.White)
        ) { Text("Pokaż próbne powiadomienie", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        komunikat?.let { Text(it, color = Color(0xFFFFB020), fontSize = 14.sp) }

        WierszWersji(akt)
    }
}

private class StanAktualizacji(
    val info: UpdateInfo?, val postep: UpdateProgress, val sprawdzono: Boolean,
    val sprawdz: () -> Unit, val instaluj: () -> Unit
)

/** Pasek „dostępna aktualizacja” na górze ekranu głównego. Znika, gdy nie ma czego instalować. */
@Composable
private fun PasekAktualizacji(info: UpdateInfo, postep: UpdateProgress, instaluj: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF10261C))
            .border(1.dp, Color(0xFF1E5A3D), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dostępna aktualizacja ${info.version}", color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (postep) {
                        is UpdateProgress.Downloading -> "pobieram… ${postep.percent} %"
                        UpdateProgress.Installing -> "otwieram instalator…"
                        is UpdateProgress.Failed -> postep.message
                        else -> "masz ${BuildConfig.VERSION_NAME}" + if (info.sizeBytes > 0) " · ${info.sizeBytes / 1_048_576} MB" else ""
                    },
                    color = if (postep is UpdateProgress.Failed) Color(0xFFFFB020) else Szary, fontSize = 13.sp
                )
            }
            val zajety = postep is UpdateProgress.Downloading || postep == UpdateProgress.Installing
            Button(
                onClick = instaluj, enabled = !zajety, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Tlo)
            ) { Text("Aktualizuj", fontWeight = FontWeight.Bold) }
        }
        if (postep is UpdateProgress.Downloading) LinearProgressIndicator(
            progress = { postep.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Ok, trackColor = Color(0xFF1E5A3D)
        )
        if (info.notes.isNotBlank()) Text(info.notes, color = Szary, fontSize = 13.sp, maxLines = 6)
    }
}

/** Wersja aplikacji i ręczne sprawdzenie aktualizacji — na samym dole ekranu głównego. */
@Composable
private fun WierszWersji(akt: StanAktualizacji) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = akt.sprawdz)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Wersja ${BuildConfig.VERSION_NAME}", color = Szary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    akt.postep == UpdateProgress.Checking -> "sprawdzam…"
                    akt.info != null -> "jest nowsza: ${akt.info.version}"
                    akt.sprawdzono && Updater.ostatnioBlad -> "nie udało się sprawdzić — brak internetu?"
                    akt.sprawdzono -> "masz najnowszą"
                    else -> "aktualizacje z GitHuba"
                },
                color = Color(0xFF6E7681), fontSize = 13.sp
            )
        }
        Text("Sprawdź", color = Akcent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Przelacznik(tytul: String, opis: String, wartosc: Boolean, zmien: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { zmien(!wartosc) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(tytul, color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(opis, color = Szary, fontSize = 13.sp)
        }
        Switch(wartosc, zmien, colors = kolorySwitcha())
    }
}

@Composable
private fun Kafel(tytul: String, opis: String, kolorOpisu: Color, modifier: Modifier, klik: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Karta).border(1.dp, Linia, RoundedCornerShape(20.dp))
            .clickable(onClick = klik).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(tytul, color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(opis, color = kolorOpisu, fontSize = 13.sp)
    }
}

@Composable
private fun kolorySwitcha() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Akcent, checkedBorderColor = Akcent,
    uncheckedThumbColor = Color(0xFFC7CCD4), uncheckedTrackColor = Wylaczony, uncheckedBorderColor = Wylaczony
)

private fun nazwa(e: Efekt) = when (e) { Efekt.PODSTAWOWY -> "Podstawowy"; Efekt.MIGOTANIE -> "Migotanie" }

// ───────────────────────── Podgląd ramki ─────────────────────────

/** Ten sam [Rysownik] co nakładka — podgląd pokazuje dokładnie to, co zaświeci. */
@Composable
private fun Podglad(styl: Styl, modifier: Modifier, promienDp: Float, mini: Boolean = false, kolor: Int? = null) {
    val rysownik = remember { Rysownik() }
    var czas by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { czas = it - start }
    }
    val gestosc = LocalDensity.current.density
    val k = kolor ?: if (styl.kolorAuto) KolorAplikacji.ZAPASOWY else styl.kolor
    Canvas(modifier.clip(RoundedCornerShape(promienDp.dp)).background(Color.Black)) {
        val gr = (if (mini) 3f else styl.szerokosc.toFloat()) * gestosc
        drawIntoCanvas { c ->
            rysownik.rysuj(c.nativeCanvas, size.width, size.height, styl, k, gr, promienDp * gestosc, czas)
        }
    }
}

// ───────────────────────── Edytor stylu ─────────────────────────

@Composable
private fun EdytorStylu(poczatek: Styl, onAnuluj: () -> Unit, onGotowe: (Styl) -> Unit) {
    var s by remember { mutableStateOf(poczatek) }
    var zakladka by remember { mutableIntStateOf(0) }
    val zakladki = if (s.efekt == Efekt.MIGOTANIE) listOf(0 to "Efekty", 2 to "Zaawans.") else listOf(0 to "Efekty", 1 to "Kolor", 2 to "Zaawans.")
    if (zakladki.none { it.first == zakladka }) zakladka = 0
    BackHandler(onBack = onAnuluj)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Podglad(s, Modifier.fillMaxSize(), promienDp = 0f + promienEkranuDp())
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp)
                .clip(RoundedCornerShape(30.dp)).background(Color(0xFF1A1F27))
                .border(1.dp, Color(0xFF2A313D), RoundedCornerShape(30.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF0F1318)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                zakladki.forEach { (i, nazwa) ->
                    val wybrana = i == zakladka
                    Text(
                        nazwa, textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (wybrana) Color.White else Color(0xFFAEB6C2),
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (wybrana) Akcent else Color.Transparent)
                            .clickable { zakladka = i }.padding(vertical = 10.dp)
                    )
                }
            }

            when (zakladka) {
                0 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Efekt.entries.forEach { e ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0F1318))
                                .border(2.dp, if (s.efekt == e) Akcent else Color(0xFF262C36), RoundedCornerShape(20.dp))
                                .clickable { s = s.copy(efekt = e) }.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Podglad(s.copy(efekt = e, przezroczystosc = 0), Modifier.size(70.dp), promienDp = 16f, mini = true)
                            Text(nazwa(e), color = Tekst, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                1 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { s = s.copy(kolorAuto = true) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(s.kolorAuto, { s = s.copy(kolorAuto = true) })
                        Column {
                            Text("Automatyczny", color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("kolor z ikony aplikacji", color = Szary, fontSize = 13.sp)
                        }
                    }
                    PALETA.chunked(6).forEach { wiersz ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            wiersz.forEach { k ->
                                val wybrany = !s.kolorAuto && s.kolor == k
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                        .border(3.dp, if (wybrany) Color.White else Color.Transparent, CircleShape)
                                        .padding(4.dp).clip(CircleShape).background(Color(k))
                                        .clickable { s = s.copy(kolorAuto = false, kolor = k) }
                                )
                            }
                        }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Suwak("Przezroczystość", "${s.przezroczystosc} %", s.przezroczystosc, Styl.PRZEZROCZYSTOSC, 10) { s = s.copy(przezroczystosc = it) }
                    Suwak("Szerokość", "${s.szerokosc}", s.szerokosc, Styl.SZEROKOSC, 1) { s = s.copy(szerokosc = it) }
                    Suwak("Okres", "${s.okres} s", s.okres, Styl.OKRES, 1) { s = s.copy(okres = it) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onAnuluj, Modifier.weight(1f).height(50.dp), shape = CircleShape) {
                    Text("Anuluj", color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    { onGotowe(s) }, Modifier.weight(1f).height(50.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Akcent, contentColor = Color.White)
                ) { Text("Gotowe", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Suwak(tytul: String, wartoscTxt: String, wartosc: Int, zakres: IntRange, krok: Int, zmien: (Int) -> Unit) {
    Column {
        Row {
            Text(tytul, color = Tekst, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(wartoscTxt, color = Szary, fontSize = 15.sp)
        }
        Slider(
            value = wartosc.toFloat(),
            onValueChange = { zmien(it.toInt()) },
            valueRange = zakres.first.toFloat()..zakres.last.toFloat(),
            steps = (zakres.last - zakres.first) / krok - 1
        )
    }
}

/** Promień rogów ekranu w dp — podgląd ma takie rogi jak prawdziwy telefon. */
@Composable
private fun promienEkranuDp(): Float {
    val ctx = LocalContext.current
    val gestosc = LocalDensity.current.density
    return remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val wm = ctx.getSystemService(android.view.WindowManager::class.java)
            wm.currentWindowMetrics.windowInsets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                ?.radius?.takeIf { it > 0 }?.let { it / gestosc } ?: UslugaRamki.ZAPASOWY_PROMIEN_DP
        } else UslugaRamki.ZAPASOWY_PROMIEN_DP
    }
}

// ───────────────────────── Wybór aplikacji ─────────────────────────

@Composable
private fun WyborAplikacji(u: Ustawienia, zapisz: (Ustawienia) -> Unit, wstecz: () -> Unit) {
    val ctx = LocalContext.current
    var lista by remember { mutableStateOf<List<Aplikacja>?>(null) }
    var szukaj by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { lista = withContext(Dispatchers.IO) { listaAplikacji(ctx) } }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(wstecz) { Text("‹", color = Tekst, fontSize = 26.sp) }
            Text("Aplikacje", color = Tekst, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (!u.wszystkieAplikacje) Text("${u.wybrane.size} wybranych", color = Szary, fontSize = 14.sp)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Karta).border(1.dp, Linia, RoundedCornerShape(18.dp))
                .clickable { zapisz(u.copy(wszystkieAplikacje = !u.wszystkieAplikacje)) }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wszystkie aplikacje", color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("wyłącz, żeby wybrać pojedynczo", color = Szary, fontSize = 13.sp)
            }
            Switch(u.wszystkieAplikacje, { zapisz(u.copy(wszystkieAplikacje = it)) }, colors = kolorySwitcha())
        }
        if (!u.wszystkieAplikacje) {
            OutlinedTextField(szukaj, { szukaj = it }, Modifier.fillMaxWidth(), placeholder = { Text("Szukaj aplikacji") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            val l = lista
            if (l == null) Text("Wczytuję aplikacje…", color = Szary)
            else LazyColumn(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Karta)
            ) {
                items(l.filter { szukaj.isBlank() || it.nazwa.contains(szukaj, ignoreCase = true) }, key = { it.pakiet }) { a ->
                    val wl = a.pakiet in u.wybrane
                    val zmien = { nowe: Boolean -> zapisz(u.copy(wybrane = if (nowe) u.wybrane + a.pakiet else u.wybrane - a.pakiet)) }
                    Row(
                        Modifier.fillMaxWidth().clickable { zmien(!wl) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val ikona = remember(a.pakiet) { a.ikona.toBitmap(96, 96).asImageBitmap() }
                        Image(ikona, null, Modifier.size(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.nazwa, color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            val k = remember(a.pakiet) { KolorAplikacji.dla(ctx, a.pakiet) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(k)))
                                Text("kolor z ikony", color = Szary, fontSize = 12.sp)
                            }
                        }
                        Switch(wl, zmien, colors = kolorySwitcha())
                    }
                }
            }
        }
    }
}

// ───────────────────────── Uprawnienia ─────────────────────────

@Composable
private fun EkranUprawnien(upr: Triple<Boolean, Boolean, Boolean>, dalej: () -> Unit) {
    val ctx = LocalContext.current
    val kroki = listOf(
        Triple("Dostęp do powiadomień", "żeby wiedzieć, że coś przyszło lub dzwoni", upr.first) to { Uprawnienia.otworzPowiadomienia(ctx) },
        Triple("Rysowanie ramki (ułatwienia dostępu)", "Ułatwienia dostępu → Krawędzie → włącz. Ramka będzie nad paskiem stanu i ekranem blokady. Aplikacja nie czyta ekranu.", upr.second) to { Uprawnienia.otworzNakladke(ctx) },
        Triple("Bateria bez ograniczeń", "żeby Android nie usypiał aplikacji", upr.third) to { Uprawnienia.otworzBaterie(ctx) }
    )
    val pierwszyBrak = kroki.indexOfFirst { !it.first.third }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Żeby ramka działała, Android musi na to pozwolić", color = Tekst, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
        Text("Każdy przycisk otwiera właściwe ustawienie systemu. Po włączeniu wróć tutaj.", color = Szary, fontSize = 14.sp)

        kroki.forEachIndexed { i, (k, akcja) ->
            val (tytul, opis, ok) = k
            val aktywny = i == pierwszyBrak
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(when { ok -> Color(0xFF10261C); aktywny -> AkcentTlo; else -> Karta })
                    .border(1.dp, when { ok -> Color(0xFF1E5A3D); aktywny -> Color(0xFF2D5BB8); else -> Linia }, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (ok) Ok else Color.Transparent)
                            .border(2.dp, if (ok) Ok else Szary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(if (ok) "✓" else "${i + 1}", color = if (ok) Tlo else Szary, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) {
                        Text(tytul, color = Tekst, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(opis, color = Szary, fontSize = 13.sp)
                    }
                }
                if (!ok) Button(
                    onClick = akcja, modifier = Modifier.fillMaxWidth(), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (aktywny) Akcent else Color(0xFF262C36), contentColor = Color.White)
                ) { Text("Otwórz ustawienie", fontWeight = FontWeight.Bold) }
            }
        }

        // Android 13+: aplikacja z pliku APK ma wyszarzone ułatwienia dostępu i dostęp do powiadomień.
        if (!(upr.first && upr.second)) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A2210))
                .border(1.dp, Color(0xFF6B5420), RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Przełącznik jest wyszarzony?", color = Color(0xFFFFD27A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Android blokuje te ustawienia aplikacjom zainstalowanym z pliku. Otwórz informacje o aplikacji, " +
                    "dotknij ⋮ w prawym górnym rogu i wybierz „Zezwól na ustawienia z ograniczeniami”. Potem wróć tutaj.",
                color = Color(0xFFE6D3A8), fontSize = 13.sp
            )
            TextButton({ Uprawnienia.otworzInformacjeOAplikacji(ctx) }) { Text("Otwórz informacje o aplikacji", color = Color(0xFFFFD27A)) }
        }

        TextButton(dalej, Modifier.fillMaxWidth()) {
            Text(if (upr.first && upr.second && upr.third) "Gotowe" else "Pomiń na razie", color = Tekst, fontSize = 15.sp)
        }
    }
}
