# 01 — Wymagania i zachowanie systemów

[Spis planu](README.md) · [Architektura](02-architektura-i-geometria.md)

## 1. Stan obecny

Model `PlotData` przechowuje środek, promień i listę rozszerzeń `PlotSegment`. Segment ma bok `2 * radius + 1`, a przynależność do działki jest sumą logiczną przynależności do jej segmentów. Dla domyślnego promienia 16 pojedynczy segment ma rozmiar 33 × 33 i powierzchnię 1089 bloków. Nie jest to chunk 16 × 16.

`ClaimCMD` sprawdza świat, uprawnienia, promień, liczbę działek, powierzchnię i kolizje. Potwierdzenie ponawia część sprawdzeń, a zapis korzysta z `claimPlotAtomically`. Rozszerzanie działa przez sąsiednie segmenty, z ceną zależną od liczby zakupionych rozszerzeń. Dane członków i flag są powiązane z ID działki. Te elementy należy wykorzystać jako wspólną podstawę, wydzielając zależności od promienia.

## 2. Słownik

| Pojęcie | Znaczenie w projekcie |
| --- | --- |
| `classic` | Obecna geometria: segment bazowy i rozszerzenia o nieparzystej długości boku. |
| `chunks` | Nowa geometria: niepusty, spójny zbiór chunków jednego świata. |
| Działka | Wspólna jednostka własności, nazwy, flag, ról i usunięcia. |
| Chunk działki | Para współrzędnych `chunkX`, `chunkZ`, należąca do jednej działki. |
| Tryb zajmowania | Globalna konfiguracja tworzenia nowych działek; nie filtr ochrony. |
| Powierzchnia | Liczba chronionych kolumn bloków X/Z; wysokość nie mnoży limitu. |
| Punkt teleportacji | Pozycja startowa do wyszukania bezpiecznego miejsca, niezależna od kształtu. |

## 3. Reguły współistnienia

| Operacja | `mode: classic` | `mode: chunks` |
| --- | --- | --- |
| Utworzenie nowej działki | Segment klasyczny | Jeden chunk |
| Ochrona istniejącej klasycznej | Aktywna | Aktywna |
| Ochrona istniejącej chunkowej | Aktywna | Aktywna |
| Rozszerzenie klasycznej | Klasyczny segment | Klasyczny segment |
| Rozszerzenie chunkowej | Sąsiedni chunk | Sąsiedni chunk |
| Lista, członkowie, flagi, zmiana nazwy | Oba typy | Oba typy |
| Transfer i usunięcie | Oba typy | Oba typy |

Obsługa rozszerzeń według zapisanego typu pozwala nadal zarządzać posiadanym terenem po zmianie konfiguracji. Globalny wybór nie udostępnia graczowi alternatywnego sposobu tworzenia nowych działek przez parametr komendy.

## 4. Tworzenie działki chunkowej

1. Gracz wywołuje `/claim` na niezajętym terenie.
2. System pobiera świat i współrzędne blokowe gracza, a następnie wyznacza chunk bez jego ładowania.
3. Weryfikuje prawo do tworzenia, dozwolony świat, wszystkie limity, kolizje z obiema geometriami i regionami zewnętrznymi.
4. Pokazuje potwierdzenie: świat, współrzędne chunka, zakres bloków, powierzchnię 256 i wykorzystanie limitów po operacji.
5. Potwierdzenie sprawdza ponownie stan gracza, wycenę, konfigurację i uprawnienia. Przejście do innego chunka lub świata unieważnia ofertę. Przesunięcie wewnątrz tego samego chunka jej nie unieważnia.
6. Warstwa zapisu ponownie sprawdza konflikty i limity na aktualnych danych, zapisuje całą operację i publikuje stan ochrony.
7. Dopiero po publikacji gracz otrzymuje komunikat powodzenia i podgląd granic.

`/claim` na istniejącej działce informuje o zajętym terenie i nie rozszerza jej automatycznie. Dzięki temu ta sama komenda nie zmienia znaczenia w zależności od własności sąsiednich chunków.

## 5. Rozszerzanie

Gracz stojący na swojej działce chunkowej otwiera istniejący panel rozszerzania. Jako źródło wybierany jest chunk pod graczem. Kierunki północ, wschód, południe i zachód wskazują dokładnie jednego sąsiada źródła. Kierunek do chunka już należącego do działki jest niedostępny; do innej działki powoduje kolizję, także przy tym samym właścicielu.

Każde rozszerzenie zwiększa powierzchnię o 256 i licznik zakupionych rozszerzeń o jeden. Nie zwiększa liczby działek. Dołączenie przez sam narożnik nie jest dopuszczalne. Dodawanie chunków może tworzyć wklęsły kształt lub otoczyć pusty obszar: niezajęte chunki wewnątrz obwiedni nadal pozostają wolne. Ochrona ani wizualizacja nie mogą traktować prostokątnej obwiedni jako rzeczywistej działki.

Potwierdzenie utrwala ID działki, chunk źródłowy, chunk docelowy, rewizję geometrii i cenę. Zmiana właściciela, przejście do innego źródła, zmiana ceny lub inna mutacja działki wymagają nowego potwierdzenia. Podwójne kliknięcie wykonuje operację najwyżej raz.

## 6. Pozostałe operacje

- **Usunięcie:** `/unclaim` wyświetla nazwę, ID, typ, liczbę chunków i całkowitą powierzchnię. Potwierdzenie usuwa całą działkę oraz powiązane rekordy. W pierwszej wersji nie zwraca kosztów rozszerzeń.
- **Transfer:** odbiorca musi spełniać dotychczasowe wymagania transferu, w tym członkostwo, oraz wspólne limity liczby działek i powierzchni. Dla chunków dochodzą limity chunkowe; promień nie ma zastosowania. Zachować reguły konfliktu nazw i zmian ról.
- **Flagi i członkowie:** obowiązują na całej działce, bez indywidualnych ustawień chunków.
- **Teleportacja:** zachować obecny mechanizm bezpiecznego teleportu, ale zaakceptować cel wyłącznie wewnątrz rzeczywistej geometrii. Brak bezpiecznego miejsca kończy się komunikatem.
- **Obniżenie limitów:** nie usuwa istniejącego terenu; blokuje operacje zwiększające przekroczony limit i transfer do odbiorcy niespełniającego warunków.
- **Świat wyłączony z listy:** blokuje tworzenie i powiększanie terenu w tym świecie; istniejąca ochrona, zarządzanie i usuwanie nadal działają. Dla klasycznych rozszerzeń należy ujednolicić tę regułę i objąć ją regresją.

## 7. Scenariusze odbioru

**Zmiana konfiguracji:** administrator ma klasyczną działkę A, uruchamia `chunks`, tworzy B i ponownie włącza `classic`. A i B zachowują ID, kształt, właścicieli, członków i ochronę. Nowa działka C jest klasyczna.

**Kolizja częściowa:** klasyczna działka zajmuje jeden blok docelowego chunka. Zajęcie całego chunka jest odrzucone bez pobrania pieniędzy i bez częściowego zapisu.

**Limity mieszane:** właściciel ma klasyczny segment 1089 bloków i działkę z dwóch chunków, czyli 512 bloków. Łączna powierzchnia wynosi 1601. Przy limicie 1800 kolejny chunk jest odrzucony, mimo że limit liczby chunków nie został osiągnięty.

**Wspólna granica:** dwie działki mogą stykać się bokami, jeżeli nie współdzielą żadnej kolumny bloków. Kierunek przenoszenia przedmiotów, płynów lub bloków przez granicę nadal podlega obecnym regułom flag.
