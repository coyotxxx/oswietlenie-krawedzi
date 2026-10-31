# Testy Krawędzi na emulatorach

Skrypt: `testy/macierz.py` (jedna wersja), `testy/wszystkie_androidy.sh` (po kolei). Aplikacja testowa `tester/` wysyła każdy rodzaj powiadomienia.

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
