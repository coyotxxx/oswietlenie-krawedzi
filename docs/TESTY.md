# Testy Krawędzi na emulatorach

Skrypt: `testy/macierz.py` (jedna wersja), `testy/wszystkie_androidy.sh` (po kolei). Aplikacja testowa `tester/` wysyła każdy rodzaj powiadomienia.

## Wynik v0.3.0 (2026-10-31)

| # | Scenariusz | A12 | A13 | A14 | A15 | A16 |
|---|---|---|---|---|---|---|
| P1 | Powiadomienie, ekran włączony | ✅ | ✅ | ✅ | ✅ | ✅ |
| P2 | Powiadomienie, ekran zgaszony → wybudza, czarne tło, sam gaśnie | ✅ | ✅ | ✅ | ✅ | ✅ |
| P3 | Powiadomienie przy zgaszonym, dotknięcie → otwiera | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| P4 | Przy zgaszonym ekranie WYŁ. → nie budzi | ✅ | ✅ | ✅ | ✅ | ✅ |
| P5 | Ciche powiadomienie → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P6 | Stałe (ongoing) → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P7 | Podsumowanie grupy → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P8 | Nie przeszkadzać → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P9 | Główny przełącznik WYŁ. → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P10 | „Powiadomienia” WYŁ. → bez ramki, dzwonienie dalej działa | ✅ | ✅ | ✅ | ✅ | ✅ |
| P11 | Aplikacja niewybrana → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P12 | Prawdziwy SMS (aplikacja Wiadomości) | ✅ | ✅ | ✅ | ✅ | ✅ |
| D1 | Dzwonienie, ekran ZGASZONY, nieodebrane | ✅ | ✅ | ✅ | ✅ | ✅ |
| D2 | Dzwonienie, ekran ZGASZONY, odebrane | ✅ | ✅ | ✅ | ✅ | ✅ |
| D3 | Dzwonienie, ekran ZABLOKOWANY (włączony) | ✅ | ✅ | ✅ | ✅ | ✅ |
| D4 | Dzwonienie, ekran WŁĄCZONY, odrzucone przyciskiem | ✅ | ✅ | ✅ | ✅ | ✅ |
| D5 | Dzwonienie, ekran WŁĄCZONY, odebrane | ✅ | ✅ | ✅ | ✅ | ✅ |
| D6 | Dzwonienie WYŁ. → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| D7 | Powiadomienie w trakcie dzwonienia → dzwonienie zostaje | ✅ | ✅ | ✅ | ✅ | ✅ |
| D8 | Dzwonienie w trakcie powiadomienia przy zgaszonym ekranie | ✅ | ✅ | ✅ | ✅ | ✅ |
| V1 | Komunikator (CallStyle): dzwoni → rozmowa trwa | ✅ | ✅ | ✅ | ✅ | ✅ |
| V2 | Komunikator (CallStyle): dzwoni → odrzucone (usunięte) | ✅ | ✅ | ✅ | ✅ | ✅ |
| V3 | Komunikator przy zgaszonym ekranie | ✅ | ✅ | ✅ | ✅ | ✅ |
| V4 | Stary styl połączenia (bez CallStyle) | ✅ | ✅ | ✅ | ✅ | ✅ |
| R1 | Obrót poziomy | ✅ | ✅ | ✅ | ✅ | ✅ |
| R2 | Restart usługi ramki w trakcie dzwonienia → ramka wraca | ✅ | ✅ | ✅ | ✅ | ✅ |
| E1 | **Poświata** — powiadomienie przy zgaszonym ekranie | ✅ | ✅ | ✅ | ✅ | ✅ |
| E2 | **Poświata** — dzwonienie przy zgaszonym ekranie | ✅ | ✅ | ✅ | ✅ | ✅ |
| E3 | **Suwak Prędkość** zapisuje się | ✅ | ✅ | ✅ | ✅ | ✅ |
| Z1 | **Przypomnienie** co 1 min przy zgaszonym ekranie | ✅ | ✅ | ✅ | ✅ | ✅ |
| Z2 | **Przypomnienie** w głębokim uśpieniu (Doze), bez wyjątku baterii | ✅ | ✅ | ✅ | ✅ | ✅ |
| Z3 | Powiadomienie usunięte → koniec przypomnień | ✅ | ✅ | ✅ | ✅ | ✅ |
| Z4 | Sam włączasz ekran → koniec przypomnień | ✅ | ✅ | ✅ | ✅ | ✅ |
| Z5 | Przypominanie WYŁ. → brak przypomnień | ✅ | ✅ | ✅ | ✅ | ✅ |
| Z6 | Powiadomienie przy włączonym ekranie → bez przypomnień | ✅ | ✅ | ✅ | ✅ | ✅ |

Pierwszy pełny przebieg: A12 34/35, A13 34/35, A14 34/35, A15 33/35, A16 30/35. Drugi pełny przebieg (A15, A16) po poprawkach skryptu: A15 **35/35**, A16 34/35.

**Co nie przeszło i dlaczego:**
- P11 (wszędzie) — błąd skryptu: nowa sekcja przypomnień zepchnęła wiersz „Aplikacje” poniżej ekranu. Skrypt przewija; powtórzone na każdej wersji: OK.
- D8 (A15, A16) — błąd skryptu: emulator A16 zgłasza dzwonienie 2–4,5 s po `gsm call`, a test sprawdzał po 3 s. Dziennik aplikacji: tło znika w tej samej sekundzie, w której przychodzi dzwonienie. Test czeka teraz do 8 s; 3/3 OK.
- P1, P12, D1 (A16, tylko 1. przebieg, zaraz po starcie emulatora) — w powtórce i w 2. przebiegu OK.
- ⚠️ P3 na A16 — dotknięcie czarnego ekranu raz na kilkanaście razy nie dociera do ramki (ramka jest najwyższym oknem, a dotyk nie przychodzi). W sondzie 15/16 OK. Na pozostałych wersjach zawsze OK. Ponowne dotknięcie działa.
- Uwaga: na A16 podczas zrzutu interfejsu (uiautomator) system restartuje usługę ramki; ramka wraca po ~3 s (mechanizm z R2).

## Wynik v0.2.2 (2026-10-31)

| # | Scenariusz | A12 | A13 | A14 | A15 | A16 |
|---|---|---|---|---|---|---|
| P1 | Powiadomienie, ekran włączony | ✅ | ✅ | ✅ | ✅ | ❌ |
| P2 | Powiadomienie, ekran zgaszony → wybudza, czarne tło, sam gaśnie | ✅ | ✅ | ✅ | ✅ | ✅ |
| P3 | Powiadomienie przy zgaszonym, dotknięcie → otwiera | ✅ | ✅ | ✅ | ✅ | ✅ |
| P4 | Przy zgaszonym ekranie WYŁ. → nie budzi | ✅ | ✅ | ✅ | ✅ | ✅ |
| P5 | Ciche powiadomienie → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P6 | Stałe (ongoing) → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P7 | Podsumowanie grupy → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P8 | Nie przeszkadzać → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P9 | Główny przełącznik WYŁ. → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P10 | „Powiadomienia” WYŁ. → bez ramki, dzwonienie dalej działa | ✅ | ✅ | ✅ | ✅ | ✅ |
| P11 | Aplikacja niewybrana → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| P12 | Prawdziwy SMS (aplikacja Wiadomości) | ✅ | ✅ | ✅ | ✅ | ✅ |
| D1 | Dzwonienie, ekran ZGASZONY, nieodebrane (zgłoszony błąd) | ✅ | ✅ | ✅ | ✅ | ✅ |
| D2 | Dzwonienie, ekran ZGASZONY, odebrane | ✅ | ✅ | ✅ | ✅ | ✅ |
| D3 | Dzwonienie, ekran ZABLOKOWANY (włączony) | ✅ | ✅ | ✅ | ✅ | ✅ |
| D4 | Dzwonienie, ekran WŁĄCZONY, odrzucone przyciskiem | ✅ | ✅ | ✅ | ✅ | ✅ |
| D5 | Dzwonienie, ekran WŁĄCZONY, odebrane | ✅ | ✅ | ✅ | ✅ | ✅ |
| D6 | Dzwonienie WYŁ. → bez ramki | ✅ | ✅ | ✅ | ✅ | ✅ |
| D7 | Powiadomienie w trakcie dzwonienia → dzwonienie zostaje | ✅ | ✅ | ✅ | ✅ | ✅ |
| D8 | Dzwonienie w trakcie powiadomienia przy zgaszonym ekranie | ✅ | ✅ | ✅ | ❌ | ❌ |
| V1 | Komunikator (CallStyle): dzwoni → rozmowa trwa | ✅ | ✅ | ✅ | ✅ | ✅ |
| V2 | Komunikator (CallStyle): dzwoni → odrzucone (usunięte) | ✅ | ✅ | ✅ | ✅ | ✅ |
| V3 | Komunikator przy zgaszonym ekranie | ✅ | ✅ | ✅ | ✅ | ✅ |
| V4 | Stary styl połączenia (bez CallStyle) | ✅ | ✅ | ✅ | ✅ | ✅ |
| R1 | Obrót poziomy | ✅ | ✅ | ✅ | ✅ | ✅ |
| R2 | Restart usługi ramki w trakcie dzwonienia → ramka wraca | ✅ | ✅ | ✅ | ✅ | ✅ |

**Uwagi do ❌:**
- D8 na A15/A16: przyciski Odbierz/Odrzuć są widoczne na zrzucie (`a35/D8_*.png`), a zrzut interfejsu w teście nie zdążył ich odczytać.
- P1 na A16: ramka nie zgasła po 5 s w pierwszym teście po starcie emulatora. Przyczyny nie ustaliłem (dziennik przepadł z emulatorem). Na tym samym Androidzie 16 powtórzone 3 razy: zawsze OK.

## Błędy znalezione przez macierz i naprawione w 0.2.2
1. Dzwonienie przy zgaszonym ekranie: czarne tło zasłaniało ekran odbierania (zgłoszenie z Pixela).
2. Dotknięcie ramki nie otwierało wiadomości na Androidzie 14+ (blokada startu aktywności z tła).
3. Android 12 (AOSP Telefon): po odebraniu ramka świeciła przez całą rozmowę — teraz gaśnie po trybie audio MODE_IN_CALL.
4. Restart usługi ramki w trakcie dzwonienia gasił ramkę — teraz wraca (także gdy Telefon podmienił powiadomienie na bez kategorii).
