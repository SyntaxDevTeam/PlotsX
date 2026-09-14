# Plan dodania chunkowego systemu działek

Status: **E3/E4 zintegrowane, E5 podłączony, zakup chunków z dziennikiem i GUI podłączony**. Kod zawiera wybór trybu i zajmowanie jednego chunka; pełny cykl życia oraz wydanie pozostają w kolejnych etapach. Data opracowania: 12 września 2026 r. Dokumentacja powstała na podstawie kodu PlotsX w tym repozytorium. Bieżący zakres wykonanych i pozostałych prac opisuje [rejestr postępu](06-postep-implementacji.md). Nazwy nowych klas, kluczy konfiguracji i uprawnień pozostają propozycją, dopóki rejestr nie potwierdza ich implementacji.

## Cel i zasada nadrzędna

Dodać drugą metodę zajmowania terenu: działki zbudowane z całych chunków. Obecna metoda, oparta na kwadratowych segmentach o zadanym promieniu, pozostaje obsługiwana. Administrator wybiera metodę tworzenia nowych działek w `config.yml` przez `plots.claiming.mode: classic | chunks`. Domyślną wartością jest `classic`, dzięki czemu aktualizacja zachowuje dotychczasowe zachowanie.

**Konfiguracja wybiera sposób tworzenia nowych działek. Typ zapisanej działki wybiera jej geometrię i sposób rozszerzania. Ochrona zawsze obejmuje oba typy.** Zmiana trybu nie konwertuje danych, nie usuwa działek i nie wyłącza ochrony terenów utworzonych wcześniej. Jest to przyjęte w planie rozstrzygnięcie znaczenia współistnienia systemów.

## Spis dokumentów

| Dokument | Zakres |
| --- | --- |
| [01 — Wymagania i zachowanie](01-wymagania-i-zachowanie.md) | Reguły produktu, komendy, cykl życia, granice zakresu i scenariusze użytkownika. |
| [02 — Architektura i geometria](02-architektura-i-geometria.md) | Punkty zmian w kodzie, strategie, obliczenia, ochrona, cache i API. |
| [03 — Dane, transakcje i migracja](03-dane-transakcje-migracja.md) | Schemat SQL, konkurencja, płatności, aktualizacja i odtwarzanie. |
| [04 — Konfiguracja i integracje](04-konfiguracja-i-integracje.md) | Proponowany config, limity, uprawnienia, interfejsy i integracje. |
| [05 — Realizacja, testy i wdrożenie](05-realizacja-testy-wdrozenie.md) | Etapy prac, kryteria odbioru, macierz testów i operacyjny plan wydania. |
| [06 — Postęp implementacji](06-postep-implementacji.md) | Aktualna lista wykonanych elementów, testów i prac pozostałych. |
| [07 — API v2 i uruchomienie](07-api-v2-i-uruchomienie.md) | Rzeczywisty config, migracja konsumentów i kontrakt spójności ochrony. |
| [08 — Raport weryfikacji](08-raport-weryfikacji.md) | Macierz SQL, scenariusze Paper, benchmark i odtwarzanie testów. |
| [09 — Rozszerzanie chunków](09-rozszerzanie-chunkow.md) | Transakcja E6 A, rewizje, liczniki, testy i dalsza integracja płatności. |
| [10 — Dziennik operacji](10-dziennik-operacji.md) | Stany płatności, migracja, recovery, backup v3 i pozostała integracja. |
| [11 — Zakup i GUI](11-zakup-chunka-i-gui.md) | Usługa zakupu, wątki, kompensacja, cennik i podłączone GUI chunków. |

## Najważniejsze decyzje projektowe

1. Pierwsze zajęcie w trybie `chunks` tworzy jedną działkę z jednego chunka 16 × 16 bloków. Ochrona obejmuje całą obsługiwaną wysokość świata.
2. Rozszerzenie dodaje jeden chunk przylegający bokiem do wskazanego chunka tej samej działki. Działka zachowuje jedno ID, właściciela, członków i zestaw flag.
3. Nie łączymy automatycznie sąsiadujących działek, nawet jeżeli mają tego samego właściciela.
4. Kolizje są sprawdzane między wszystkimi typami działek. Częściowo zajęty przez klasyczną działkę chunk nie może zostać zajęty w całości.
5. Liczba działek i powierzchnia właściciela są rozliczane wspólnie dla obu systemów i wszystkich światów. Limit chunków jest dodatkowy, właściwy dla systemu chunkowego.
6. `/unclaim` nadal usuwa całą działkę po potwierdzeniu. Usuwanie pojedynczego chunka, rozdzielanie działek i konwersja geometrii pozostają poza pierwszą wersją.
7. Zmiana trybu wymaga restartu. Zwykłe przeładowanie konfiguracji nie może podmienić strategii przy otwartych potwierdzeniach i trwających zapisach.

## Warunki ukończenia

Wydanie jest gotowe, gdy tryb klasyczny przechodzi testy regresji, tryb chunkowy realizuje pełny cykl życia działki, a obie geometrie pozostają chronione po przełączeniu konfiguracji w dowolną stronę. Migracja, backup i odtworzenie muszą działać na SQLite, MySQL, MariaDB, PostgreSQL i H2. Poprawne działanie samego `/claim` nie jest wystarczające: zakres obejmuje rozszerzanie, transfer własności, GUI, API, ochronę i odzyskiwanie po błędach.

## Poza zakresem pierwszego wydania

- Wybór trybu przez gracza lub osobno dla każdego świata.
- Automatyczne zajmowanie chunków podczas chodzenia oraz zajmowanie wielu chunków jednym poleceniem.
- Łączenie, dzielenie i częściowe zwalnianie działek.
- Automatyczna konwersja działek klasycznych do chunków.
- Współdzielona, aktywnie zapisywana baza działek przez kilka serwerów Minecraft.
- Zmiana modelu flag, hierarchii ról lub prywatnych kontenerów niezwiązana z geometrią.

Te funkcje mogą powstać później, ale nie mogą być ukrytym warunkiem ukończenia podstawowej implementacji.
