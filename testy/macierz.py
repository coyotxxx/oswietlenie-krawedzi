#!/usr/bin/env python3
"""
Pełna macierz testów Krawędzi na emulatorze (adb).

Uruchomienie:  python3 testy/macierz.py [serial] [katalog_wynikow]
Wymaga zainstalowanych: aplikacji (app-debug.apk) i testera (tester-debug.apk).
Wynik: tabela Markdown + zrzuty ekranu w katalogu wyników.

Co sprawdza każdy test — patrz pole `opis`. Stan ramki odczytujemy z `dumpsys window`
(okno typu 2032 naszej aplikacji, flaga NOT_TOUCHABLE = przezroczysta, bez czarnego tła),
stan ekranu z `dumpsys power`, decyzje aplikacji z jej dziennika (run-as).
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = "/home/debian/Android/Sdk/platform-tools/adb"
SERIAL = sys.argv[1] if len(sys.argv) > 1 else "emulator-5554"
WYNIKI = sys.argv[2] if len(sys.argv) > 2 else "/home/debian/tmp/krawedzie-testy"
PKG = "pl.oswietlenie.krawedzi"
TESTER = "pl.krawedzie.tester"
NUMER = "600123456"
os.makedirs(WYNIKI, exist_ok=True)


def adb(*a, timeout=60):
    r = subprocess.run([ADB, "-s", SERIAL, *a], capture_output=True, text=True, timeout=timeout)
    return r.stdout + r.stderr


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def czekaj(s):
    time.sleep(s)


# ───────── odczyty stanu ─────────

def ramka():
    """None = brak okna ramki; 'przezroczysta' = NOT_TOUCHABLE; 'z_tlem' = dotykalna (czarne tło).
    Okno ramki = okno naszej aplikacji typu 2032 (nie mylić z oknem MainActivity)."""
    out = sh("dumpsys window windows")
    bloki, akt = [], None
    for linia in out.splitlines():
        if "Window #" in linia:
            akt = [] if f"u0 {PKG}}}" in linia else None
            if akt is not None:
                bloki.append(akt)
        if akt is not None:
            akt.append(linia)
    for b in bloki:
        txt = "\n".join(b)
        if ("ty=2032" in txt or "ty=ACCESSIBILITY_OVERLAY" in txt) and "isVisible=true" in txt:
            fl = re.search(r"fl=([^\n]*)", txt)
            return "przezroczysta" if fl and "NOT_TOUCHABLE" in fl.group(1) else "z_tlem"
    return None


def ekran():
    m = re.search(r"mWakefulness=(\w+)", sh("dumpsys power"))
    return m.group(1) if m else "?"


def fokus():
    m = re.search(r"mCurrentFocus=Window\{[^ ]+ u0 ([^}]+)\}", sh("dumpsys window"))
    return m.group(1) if m else "?"


def dziennik():
    return sh(f"run-as {PKG} cat files/dziennik.txt").splitlines()


class Znacznik:
    """Nowe wpisy dziennika od chwili utworzenia znacznika."""
    def __init__(self):
        self.przed = dziennik()

    def nowe(self):
        po = dziennik()
        if self.przed and self.przed[-1] in po:
            i = len(po) - 1 - po[::-1].index(self.przed[-1])
            return po[i + 1:]
        return po


def zrzut(nazwa):
    with open(os.path.join(WYNIKI, nazwa + ".png"), "wb") as f:
        f.write(subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True).stdout)


# ───────── akcje ─────────

def obudz_odblokuj():
    sh("input keyevent KEYCODE_WAKEUP")
    czekaj(0.7)
    sh("wm dismiss-keyguard")
    czekaj(0.7)
    sh("input keyevent KEYCODE_HOME")
    czekaj(1)


def uspij():
    sh("input keyevent KEYCODE_SLEEP")
    czekaj(2.5)


def wyslij(rodzaj, id=1):
    sh(f"am broadcast --include-stopped-packages -n {TESTER}/.Wyslij --es rodzaj {rodzaj} --ei id {id}")


def anuluj(id=1):
    sh(f"am broadcast --include-stopped-packages -n {TESTER}/.Wyslij --ez anuluj true --ei id {id}")


def dzwon():
    adb("emu", "gsm", "call", NUMER)


def odbierz():
    adb("emu", "gsm", "accept", NUMER)


def rozlacz():
    adb("emu", "gsm", "cancel", NUMER)


def wszystkie_powiadomienia_precz():
    for i in (1, 2, 3, 50, 51, 52):
        anuluj(i)
    sh("service call notification 1")  # cancelAll dla shella — nieszkodliwe, gdy nie działa


def ui():
    # --windows: także banery systemowe (np. dzwonienie z przyciskami); starsze Androidy bez tej flagi.
    if "dumped" not in sh("uiautomator dump --windows /sdcard/ui.xml"):
        sh("uiautomator dump /sdcard/ui.xml")
    xml = sh("cat /sdcard/ui.xml")
    xml = xml[xml.find("<?xml"):] if "<?xml" in xml else xml
    try:
        return ET.fromstring(xml[: xml.rfind(">") + 1])
    except ET.ParseError:
        return None


def granice(n):
    a = list(map(int, re.findall(r"\d+", n.get("bounds"))))
    return a  # x1 y1 x2 y2


def stuknij_tekst(tekst, dokladnie=False):
    r = ui()
    if r is None:
        return False
    for n in r.iter("node"):
        t = n.get("text", "") or n.get("content-desc", "")
        if (t == tekst) if dokladnie else (tekst.lower() in t.lower()):
            x1, y1, x2, y2 = granice(n)
            sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            return True
    return False


def widac_odbieranie():
    """Ekran odbierania: pełnoekranowy (InCallActivity) albo baner z przyciskami Odbierz/Odrzuć."""
    for _ in range(3):
        if "InCall" in fokus():
            return True
        r = ui()
        if r is not None and any((n.get("text") or n.get("content-desc") or "") in ("Answer", "Odbierz") or "to answer" in (n.get("text") or "") for n in r.iter("node")):
            return True
        czekaj(1)
    return False


def otworz_aplikacje():
    sh(f"am start -n {PKG}/.MainActivity")
    czekaj(2.5)
    # Aplikacja pokazuje ekran uprawnień, jeśli przy pierwszym starcie ich brakowało — wracamy na główny.
    if stuknij_tekst("Pomiń na razie", dokladnie=True) or stuknij_tekst("Gotowe", dokladnie=True):
        czekaj(1.5)


def przelacznik(tytul, wlaczony):
    """Ustawia przełącznik w wierszu o danym tytule na ekranie głównym aplikacji."""
    otworz_aplikacje()
    for _ in range(3):
        r = ui()
        if r is None:
            continue
        wezly = list(r.iter("node"))
        tyt = next((n for n in wezly if (n.get("text") or "") == tytul), None)
        if tyt is None:
            sh("input swipe 540 1500 540 700 300")
            czekaj(1)
            continue
        _, ty1, _, ty2 = granice(tyt)
        sw = [n for n in wezly if n.get("checkable") == "true" and abs((granice(n)[1] + granice(n)[3]) / 2 - (ty1 + ty2) / 2) < 90]
        if not sw:
            return False
        if (sw[0].get("checked") == "true") != wlaczony:
            x1, y1, x2, y2 = granice(sw[0])
            sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            czekaj(1)
        sh("input keyevent KEYCODE_HOME")
        czekaj(1)
        return True
    return False


def wszystkie_aplikacje(wlaczone):
    otworz_aplikacje()
    stuknij_tekst("Aplikacje", dokladnie=True)
    czekaj(2)
    r = ui()
    ok = False
    if r is not None:
        wezly = list(r.iter("node"))
        tyt = next((n for n in wezly if (n.get("text") or "") == "Wszystkie aplikacje"), None)
        if tyt is not None:
            _, ty1, _, ty2 = granice(tyt)
            sw = [n for n in wezly if n.get("checkable") == "true" and abs((granice(n)[1] + granice(n)[3]) / 2 - (ty1 + ty2) / 2) < 90]
            if sw:
                if (sw[0].get("checked") == "true") != wlaczone:
                    x1, y1, x2, y2 = granice(sw[0])
                    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
                    czekaj(1)
                ok = True
    sh("input keyevent KEYCODE_BACK")
    czekaj(0.5)
    sh("input keyevent KEYCODE_HOME")
    czekaj(1)
    return ok


# ───────── testy ─────────

WYNIK = []


def test(nr, nazwa):
    def dek(f):
        def opakowanie():
            wszystkie_powiadomienia_precz()
            rozlacz()
            czekaj(1.5)
            obudz_odblokuj()
            uwagi = []
            try:
                ok = f(uwagi)
            except Exception as e:  # noqa: BLE001
                ok = False
                uwagi.append(f"wyjątek: {e}")
            WYNIK.append((nr, nazwa, ok, "; ".join(uwagi)))
            print(f"{nr} {'OK ' if ok else 'BŁĄD'} {nazwa} — {'; '.join(uwagi)}", flush=True)
            rozlacz()
            wszystkie_powiadomienia_precz()
            czekaj(1)
        opakowanie.__name__ = f.__name__
        return opakowanie
    return dek


def sprawdz(uwagi, warunek, opis):
    uwagi.append(("✓ " if warunek else "✗ ") + opis)
    return bool(warunek)


@test("P1", "Powiadomienie, ekran włączony")
def p1(u):
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ramka() == "przezroczysta", "ramka świeci, przezroczysta")
    zrzut("P1_powiadomienie_ekran_wl")
    czekaj(6.5)
    b = sprawdz(u, ramka() is None, "zgasła po 5 s")
    c = sprawdz(u, ekran() == "Awake", "ekran dalej włączony")
    return a and b and c


@test("P2", "Powiadomienie, ekran zgaszony → wybudza, czarne tło, sam gaśnie")
def p2(u):
    uspij()
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ekran() == "Awake", "ekran wybudzony")
    b = sprawdz(u, ramka() == "z_tlem", "ramka z czarnym tłem i dymkiem")
    zrzut("P2_powiadomienie_ekran_zgaszony")
    czekaj(6.5)
    c = sprawdz(u, ramka() is None, "ramka zgasła")
    d = sprawdz(u, ekran() != "Awake", "ekran znów zgaszony")
    return a and b and c and d


@test("P3", "Powiadomienie przy zgaszonym, dotknięcie → otwiera")
def p3(u):
    uspij()
    wyslij("zwykle")
    czekaj(1.5)
    sh("input tap 540 1000")
    czekaj(2)
    a = sprawdz(u, ramka() is None, "ramka zgasła po dotknięciu")
    b = sprawdz(u, "Otwarte" in fokus(), f"otwarte powiadomienie ({fokus()})")
    c = sprawdz(u, ekran() == "Awake", "ekran został włączony")
    zrzut("P3_dotkniecie")
    return a and b and c


@test("P4", "Przy zgaszonym ekranie WYŁ. → nie budzi")
def p4(u):
    przelacznik("Przy zgaszonym ekranie", False)
    uspij()
    wyslij("zwykle")
    czekaj(2)
    a = sprawdz(u, ekran() != "Awake", "ekran nie wybudzony")
    obudz_odblokuj()
    przelacznik("Przy zgaszonym ekranie", True)
    return a


@test("P5", "Ciche powiadomienie → bez ramki")
def p5(u):
    z = Znacznik()
    wyslij("ciche")
    czekaj(1.5)
    a = sprawdz(u, ramka() is None, "brak ramki")
    b = sprawdz(u, any("ciche" in w for w in z.nowe()), "dziennik: ciche")
    return a and b


@test("P6", "Stałe (ongoing) → bez ramki")
def p6(u):
    wyslij("stale")
    czekaj(1.5)
    return sprawdz(u, ramka() is None, "brak ramki")


@test("P7", "Podsumowanie grupy → bez ramki")
def p7(u):
    wyslij("grupa")
    czekaj(1.5)
    return sprawdz(u, ramka() is None, "brak ramki")


@test("P8", "Nie przeszkadzać → bez ramki")
def p8(u):
    sh("cmd notification set_dnd on")
    czekaj(1)
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ramka() is None, "brak ramki przy DND")
    sh("cmd notification set_dnd off")
    czekaj(1)
    return a


@test("P9", "Główny przełącznik WYŁ. → bez ramki")
def p9(u):
    przelacznik("Włączone", False)
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ramka() is None, "brak ramki")
    dzwon()
    czekaj(3)
    b = sprawdz(u, ramka() is None, "brak ramki przy dzwonieniu")
    rozlacz()
    czekaj(2)
    przelacznik("Wyłączone", True)
    return a and b


@test("P10", "„Powiadomienia” WYŁ. → bez ramki, dzwonienie dalej działa")
def p10(u):
    przelacznik("Powiadomienia", False)
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ramka() is None, "brak ramki przy powiadomieniu")
    sh("input keyevent KEYCODE_HOME")
    dzwon()
    czekaj(3)
    b = sprawdz(u, ramka() == "przezroczysta", "dzwonienie świeci")
    rozlacz()
    czekaj(2)
    przelacznik("Powiadomienia", True)
    return a and b


@test("P11", "Aplikacja niewybrana → bez ramki")
def p11(u):
    ok1 = sprawdz(u, wszystkie_aplikacje(False), "wyłączono „Wszystkie aplikacje”")
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ramka() is None, "brak ramki")
    sprawdz(u, wszystkie_aplikacje(True), "przywrócono")
    return ok1 and a


@test("P12", "Prawdziwy SMS (aplikacja Wiadomości)")
def p12(u):
    adb("emu", "sms", "send", NUMER, "Test")
    czekaj(3)
    a = sprawdz(u, ramka() is not None, "ramka świeci")
    zrzut("P12_sms")
    return a


@test("D1", "Dzwonienie, ekran ZGASZONY, nieodebrane (zgłoszony błąd)")
def d1(u):
    uspij()
    z = Znacznik()
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() == "przezroczysta", "ramka bez czarnego tła")
    b = sprawdz(u, widac_odbieranie(), "widać ekran odbierania")
    zrzut("D1_dzwonienie_zgaszony_3s")
    czekaj(10)
    c = sprawdz(u, ramka() == "przezroczysta", "po 13 s nadal świeci")
    zrzut("D1_dzwonienie_zgaszony_13s")
    rozlacz()
    czekaj(2.5)
    d = sprawdz(u, any("koniec dzwonienia" in w for w in z.nowe()), "zgasła po rozłączeniu")
    czekaj(7)
    e = sprawdz(u, ramka() is None, "potem (nieodebrane, 5 s) zgasła")
    return a and b and c and d and e


@test("D2", "Dzwonienie, ekran ZGASZONY, odebrane")
def d2(u):
    uspij()
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci")
    odbierz()
    czekaj(2.5)
    b = sprawdz(u, ramka() is None, "zgasła po odebraniu")
    zrzut("D2_po_odebraniu")
    return a and b


@test("D3", "Dzwonienie, ekran ZABLOKOWANY (włączony)")
def d3(u):
    uspij()
    sh("input keyevent KEYCODE_WAKEUP")
    czekaj(1.5)
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci, bez tła")
    zrzut("D3_zablokowany")
    czekaj(6)
    b = sprawdz(u, ramka() == "przezroczysta", "po 9 s nadal świeci")
    return a and b


@test("D4", "Dzwonienie, ekran WŁĄCZONY, odrzucone przyciskiem")
def d4(u):
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci, nie blokuje dotyku")
    zrzut("D4_dzwonienie_ekran_wl")
    b = sprawdz(u, stuknij_tekst("Decline") or stuknij_tekst("Odrzuć"), "stuknięto „Odrzuć” przez ramkę")
    czekaj(3)
    c = sprawdz(u, ramka() is None or ramka() == "przezroczysta", "po odrzuceniu ramka zgasła / mignęła jako nieodebrane")
    czekaj(6)
    d = sprawdz(u, ramka() is None, "zgasła")
    return a and b and c and d


@test("D5", "Dzwonienie, ekran WŁĄCZONY, odebrane")
def d5(u):
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci")
    odbierz()
    czekaj(2.5)
    b = sprawdz(u, ramka() is None, "zgasła po odebraniu")
    return a and b


@test("D6", "Dzwonienie WYŁ. → bez ramki")
def d6(u):
    przelacznik("Dzwonienie", False)
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() is None, "brak ramki")
    rozlacz()
    czekaj(2)
    przelacznik("Dzwonienie", True)
    return a


@test("D7", "Powiadomienie w trakcie dzwonienia → dzwonienie zostaje")
def d7(u):
    dzwon()
    czekaj(3)
    wyslij("zwykle")
    czekaj(7)
    return sprawdz(u, ramka() == "przezroczysta", "po 7 s ramka dzwonienia nadal świeci")


@test("D8", "Dzwonienie w trakcie powiadomienia przy zgaszonym ekranie")
def d8(u):
    uspij()
    wyslij("zwykle")
    czekaj(1.2)
    a = sprawdz(u, ramka() == "z_tlem", "powiadomienie z tłem")
    dzwon()
    czekaj(3)
    b = sprawdz(u, ramka() == "przezroczysta", "przy dzwonieniu tło znika")
    c = sprawdz(u, widac_odbieranie(), "widać Odbierz/Odrzuć")
    zrzut("D8_dzwonienie_po_powiadomieniu")
    czekaj(6)
    d = sprawdz(u, ramka() == "przezroczysta", "nadal świeci")
    return a and b and c and d


@test("V1", "Komunikator (CallStyle): dzwoni → rozmowa trwa")
def v1(u):
    wyslij("voip_dzwoni", 50)
    czekaj(2)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci")
    czekaj(7)
    b = sprawdz(u, ramka() == "przezroczysta", "po 9 s nadal")
    wyslij("voip_trwa", 50)
    czekaj(2)
    c = sprawdz(u, ramka() is None, "zgasła po odebraniu")
    return a and b and c


@test("V2", "Komunikator (CallStyle): dzwoni → odrzucone (usunięte)")
def v2(u):
    wyslij("voip_dzwoni", 50)
    czekaj(2)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci")
    anuluj(50)
    czekaj(2)
    b = sprawdz(u, ramka() is None, "zgasła")
    return a and b


@test("V3", "Komunikator przy zgaszonym ekranie")
def v3(u):
    uspij()
    wyslij("voip_dzwoni", 50)
    czekaj(2.5)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci bez czarnego tła")
    czekaj(7)
    b = sprawdz(u, ramka() == "przezroczysta", "po 9 s nadal")
    anuluj(50)
    czekaj(2)
    c = sprawdz(u, ramka() is None, "zgasła")
    return a and b and c


@test("V4", "Stary styl połączenia (bez CallStyle)")
def v4(u):
    wyslij("stary_dzwoni", 51)
    czekaj(2)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci")
    czekaj(7)
    b = sprawdz(u, ramka() == "przezroczysta", "po 9 s nadal")
    wyslij("stary_trwa", 51)
    czekaj(2)
    c = sprawdz(u, ramka() is None, "zgasła po odebraniu")
    return a and b and c


@test("R2", "Restart usługi ramki w trakcie dzwonienia → ramka wraca")
def r2(u):
    dzwon()
    czekaj(3)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci")
    sh("settings put secure enabled_accessibility_services null")
    czekaj(2)
    b = sprawdz(u, ramka() is None, "usługa wyłączona — ramka znika")
    sh(f"settings put secure enabled_accessibility_services {PKG}/{PKG}.UslugaRamki")
    sh("settings put secure accessibility_enabled 1")
    czekaj(3)
    c = sprawdz(u, ramka() == "przezroczysta", "po restarcie usługi ramka wróciła")
    return a and b and c


@test("R1", "Obrót poziomy — powiadomienie")
def r1(u):
    sh("settings put system accelerometer_rotation 0")
    sh("settings put system user_rotation 1")
    czekaj(2)
    wyslij("zwykle")
    czekaj(1.5)
    a = sprawdz(u, ramka() == "przezroczysta", "świeci w poziomie")
    zrzut("R1_poziomo")
    sh("settings put system user_rotation 0")
    czekaj(2)
    return a


def przygotuj():
    sh(f"pm grant {TESTER} android.permission.POST_NOTIFICATIONS")
    sh(f"appops set {PKG} ACCESS_RESTRICTED_SETTINGS allow")
    sh(f"cmd notification allow_listener {PKG}/{PKG}.NasluchPowiadomien")
    sh(f"settings put secure enabled_accessibility_services {PKG}/{PKG}.UslugaRamki")
    sh("settings put secure accessibility_enabled 1")
    sh(f"dumpsys deviceidle whitelist +{PKG}")
    sh("settings put system screen_off_timeout 1800000")
    sh("cmd notification set_dnd off")
    czekaj(3)


if __name__ == "__main__":
    przygotuj()
    wersja = re.search(r"versionName=(\S+)", sh(f"dumpsys package {PKG}"))
    sdk = sh("getprop ro.build.version.release").strip()
    print(f"Krawędzie {wersja.group(1) if wersja else '?'} na Androidzie {sdk} ({SERIAL})", flush=True)
    wszystkie = [p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, d1, d2, d3, d4, d5, d6, d7, d8, v1, v2, v3, v4, r1, r2]
    wybrane = set(sys.argv[3].split(",")) if len(sys.argv) > 3 else None
    for t in wszystkie:
        if wybrane is None or t.__name__ in wybrane:
            t()
    ok = sum(1 for w in WYNIK if w[2])
    with open(os.path.join(WYNIKI, "wynik.md"), "w") as f:
        f.write(f"# Krawędzie {wersja.group(1) if wersja else '?'} — Android {sdk}\n\n")
        f.write(f"**{ok}/{len(WYNIK)} OK**\n\n| # | Test | Wynik | Szczegóły |\n|---|---|---|---|\n")
        for nr, nazwa, wyn, uw in WYNIK:
            f.write(f"| {nr} | {nazwa} | {'✅' if wyn else '❌'} | {uw} |\n")
    print(f"WYNIK {ok}/{len(WYNIK)}", flush=True)
