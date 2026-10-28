# Config — ustawienia serwera

Opis limitów zależnych od rang i konfiguracji wielu światów:
[Limity rang i światy](Limity-i-swiaty.md).

[← Home](Home.md)

Ustawienia znajdziesz w `plugins/PlotsX/config.yml`. Wartość `true` oznacza „tak”, a `false` — „nie”. Zachowaj wcięcia i używaj spacji zamiast tabulatorów.

## Aktualizacje konfiguracji i przeładowanie

Bieżąca wersja konfiguracji to **103**. Przy aktualizacji starszego pliku PlotsX zachowuje własne ustawienia administratora, dodaje brakujące opcje i tworzy kopię zapasową, np. `config.yml.102.bak`. Kolejna próba nie nadpisuje wcześniejszej kopii. Nie zmieniaj ręcznie `config-version`, aby pominąć aktualizację.

Błędny YAML jest zgłaszany przed nadpisaniem danego pliku. Gdy `/ptx reload` odrzuci konfigurację, dotychczasowa ochrona pozostaje dostępna. Popraw wskazany błąd i ponów przeładowanie. Pliki o bieżącej lub nowszej wersji nie są automatycznie przepisywane ani obniżane do starszej wersji.

Zmiana bazy, trybu zajmowania działek, języka lub aliasów wymaga restartu. Zwykłe ustawienia działek można zastosować przez `/ptx reload`; polecenie odświeża także dane ochrony, ale nie łączy ponownie z inną bazą.

## Przykład głównych ustawień

```yaml
config-version: 103
language: "EN"

plots:
  claiming:
    mode: classic
  chunks:
    maxPerPlot: 32
    maxTotalOwned: 64
    expansion:
      price: 500.0
      priceMultiplier: 1.5
      maxPrice: -1
      standingCommand: true
  maxPlots: 5
  radius: 16
  world: ["world"]
  borderDisplaySeconds: 30
  notifications:
    boundary: actionbar
  expansion:
    price: 500.0
    priceMultiplier: 1.5
    maxPrice: -1
    defaultMaxRadius: 64
    defaultMaxTotalArea: 16641

privateChests:
  protectOnPlace: false

database:
  type: "sqlite"
  sql:
    host: "localhost"
    port: 3306
    dbname: "my_database"
    username: "root"
    password: "password"

aliases:
  claim: "c"
  unclaim: "unc"

update:
  check-for-updates: true
  auto-download: false
  hangar: true
  github: true
  modrinth: true

debug: off
stats:
  enabled: true
```

To skrócony przykład. Pełny aktualny plik: [config.yml](../../src/main/resources/config.yml).

## Działki

| Ustawienie | Znaczenie |
| --- | --- |
| `plots.maxPlots` | Maksymalna liczba działek gracza. Wartość 0 blokuje tworzenie nowych. |
| `plots.radius` | Początkowy promień działki. 16 daje teren 33 × 33 bloki. Używaj dodatniej liczby całkowitej. |
| `plots.world` | Lista dozwolonych światów, np. `["world", "survival"]`. `["*"]` dopuszcza wszystkie światy, a `[]` blokuje nowe działki. |
| `plots.expansion.defaultMaxRadius` | Domyślny maksymalny promień działki; używaj dodatniej liczby. |
| `plots.expansion.defaultMaxTotalArea` | Domyślny limit sumy powierzchni działek gracza; używaj dodatniej liczby. |

Rozmiar początkowy musi mieścić się w obu limitach. Limity promienia i powierzchni można zmienić dla rang przez [uprawnienia](Uprawnienia.md).

Zmiana początkowego promienia nie zmienia wielkości już utworzonych działek. Zmiana dozwolonego świata nie usuwa istniejących terenów.

## Rozszerzanie segmentami

`plots.expansion.price` to cena pierwszego segmentu (`0` = bez opłaty). Segment ma rozmiar pierwotnej działki, np. 33 × 33 bloki dla promienia 16. GUI pozwala wybrać północ, wschód, południe lub zachód. Punktem początkowym jest segment, w którym stoi gracz. Przejście do kolejnego segmentu pozwala skręcać i tworzyć dowolny połączony kształt. Zajętego sąsiada nie można przeskoczyć. Północ i wschód tworzą kształt L bez zajmowania pustego narożnika.

Przycisk „Pokaż granice” w GUI rozszerzania zamyka menu i pokazuje obrys istniejącej działki. Czas ustawia `plots.borderDisplaySeconds: 30` (sekundy, minimum 1); obowiązuje również w głównym panelu działki. Podgląd nie kupuje segmentu.

## Powiadomienia o granicach działek

Informacje o zbliżaniu się do działki, wejściu na nią i jej opuszczeniu są domyślnie
wyświetlane na pasku akcji, dzięki czemu nie zapełniają historii czatu:

```yaml
plots:
  notifications:
    boundary: actionbar
```

Ustaw `boundary: chat`, jeśli te trzy rodzaje powiadomień mają trafiać na czat.
Niepoprawna wartość jest bezpiecznie traktowana jak `actionbar`.

Cena kolejnych rozszerzeń rośnie według `price × priceMultiplier ^ liczba_zakupionych_segmentów`. Domyślny `plots.expansion.priceMultiplier: 1.5` daje ceny 500, 750, 1125, 1687.5. Mnożnik musi wynosić co najmniej 1; wartość 1 wyłącza wzrost. Licznik jest osobny dla każdej działki, uwzględnia istniejące segmenty i pozostaje po restarcie lub przekazaniu działki. Pierwotny teren i nieudane zakupy nie zwiększają licznika. Zmiana ceny bazowej lub mnożnika przelicza cenę następnego zakupu. Nieprawidłowe wartości lub wynik przekraczający zakres obsługiwany przez ekonomię blokują zakup.

Nie używa się już `levels` ani `step`. Przy aktualizacji usuń `levels` i ustaw `price: 500.0` lub własną cenę. Istniejące działki zachowują teren, a ich aktualny rozmiar staje się rozmiarem bazowym. Limity powierzchni liczą sumę segmentów; limit promienia określa najdalszą granicę względem pierwotnego środka. Szczegóły: [rozszerzanie i opłaty](Rozszerzanie.md).

## Prywatne pojemniki

`privateChests.protectOnPlace: true` automatycznie zabezpiecza pojemniki stawiane przez właściciela lub członka działki.

`false` wyłącza automatyczne zabezpieczanie nowych pojemników. Nie usuwa wcześniejszych zabezpieczeń. Ręczna komenda `/pchest lock` pozostaje dostępna.

## Zapis danych

Warstwą połączeń, poolingiem, lifecycle oraz sterownikami JDBC zarządza biblioteka **SyntaxDB**. PlotsX nie tworzy własnego `HikariDataSource` ani osobnych połączeń przez `DriverManager` w runtime.

| Ustawienie | Znaczenie |
| --- | --- |
| `database.type` | Rodzaj bazy: `sqlite`, `mysql`, `mariadb` lub `postgresql`. `mariadb` jest obsługiwane jako kompatybilny alias dialektu MySQL. |
| `database.sql.host` | Adres osobnego serwera bazy danych. |
| `database.sql.port` | Port bazy. Domyślnie 3306; dla typowego PostgreSQL ustaw 5432. |
| `database.sql.dbname` | Nazwa bazy; przy SQLite również nazwa lokalnego pliku bez końcówki `.db`. |
| `database.sql.username` | Login do bazy. |
| `database.sql.password` | Hasło do bazy. |

Na prosty start pozostaw SQLite. Przy domyślnej nazwie dane znajdują się w `plugins/PlotsX/my_database.db`. SQLite nie wymaga hosta, portu, loginu ani hasła. Dla SQLite SyntaxDB utrzymuje `WAL`, klucze obce oraz timeout oczekiwania na blokadę.

PlotsX akceptuje wyłącznie wymienione wyżej typy baz danych. Zmiana nazwy lub rodzaju bazy **nie przenosi działek**. Zrób kopię danych przed taką zmianą, a potem uruchom serwer ponownie.

## Język i skróty

| Ustawienie | Znaczenie |
| --- | --- |
| `language` | Język wiadomości: `EN`, `PL`, `DE`, `ES`, `FR` lub `PT_BR`. |
| `aliases.claim` | Dodatkowy skrót tworzenia działki, bez ukośnika. |
| `aliases.unclaim` | Dodatkowy skrót usuwania działki, bez ukośnika. |

Dołączone języki:

| Wartość | Język |
| --- | --- |
| `EN` | Angielski |
| `PL` | Polski |
| `DE` | Niemiecki |
| `ES` | Hiszpański |
| `FR` | Francuski |
| `PT_BR` | Portugalski brazylijski |

Pliki wiadomości znajdują się w `plugins/PlotsX/lang/`. Wybierz język w `config.yml` i uruchom serwer ponownie.

Zmiana aliasu nie usuwa podstawowych komend. Po zmianie aliasów lub języka uruchom serwer ponownie.

## Pozostałe opcje

| Ustawienie | Domyślnie | Przeznaczenie |
| --- | --- | --- |
| `webhook.discord.enabled` | `false` | Ustawienie włączenia połączenia webhook z Discordem. |
| `webhook.discord.url` | `"YOUR_WEBHOOK_URL_HERE"` | Adres webhooka; przykładowy tekst trzeba zastąpić prawdziwym adresem. |
| `update.check-for-updates` | `true` | Ustawienie sprawdzania aktualizacji. |
| `update.auto-download` | `false` | Ustawienie automatycznego pobierania aktualizacji. |
| `debug` | `off` | Poziom diagnostyki: `off`, `info`, `diag` lub `dev`. |
| `stats.enabled` | `true` | Ustawienie zbierania statystyk działania. |

Te opcje dotyczą obsługi pluginu i mogą zależeć od wydania. Sam wpis webhooka nie określa, jakie zdarzenia będą wysyłane; PlotsX nie udostępnia tutaj listy powiadomień o działkach. Nie udostępniaj publicznie adresu webhooka ani hasła do bazy.

Po zakończeniu diagnostyki ustaw `debug: off`, aby ograniczyć szczegółowe wpisy.

## Jak zastosować zmiany?

Po zmianie zwykłych ustawień działek użyj `/ptx reload`. Zmiana bazy, języka, aliasów lub integracji wymaga ponownego uruchomienia serwera. Gdy nie masz pewności, zapisz plik i wykonaj pełny restart.

`/ptx reload` odczytuje konfigurację i odświeża dane ochrony. Nie uruchamia całego pluginu ponownie ani nie przełącza bazy danych.

Dalej: [administracja](Administracja.md).
