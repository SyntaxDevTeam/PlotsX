# Config — ustawienia serwera

Opis limitów zależnych od rang i konfiguracji wielu światów:
[Limity rang i światy](../limits-and-worlds.md).

[← Home](Home.md)

Ustawienia znajdziesz w `plugins/PlotsX/config.yml`. Wartość `true` oznacza „tak”, a `false` — „nie”. Zachowaj wcięcia i używaj spacji zamiast tabulatorów.

## Pełna domyślna konfiguracja

```yaml
plots:
  maxPlots: 5
  radius: 16
  world: "world"
  expansion:
    price: 500.0
    priceMultiplier: 1.5
    defaultMaxRadius: 64
    defaultMaxTotalArea: 16641

privateChests:
  protectOnPlace: true

database:
  type: "sqlite"
  sql:
    host: "localhost"
    port: 3306
    dbname: "my_database"
    username: "root"
    password: "password"

language: "PL"

aliases:
  claim: "c"
  unclaim: "unc"

webhook:
  discord:
    enabled: false
    url: "YOUR_WEBHOOK_URL_HERE"

checkForUpdates: true
autoDownloadUpdates: false
debug: true

stats:
  enabled: false
```

## Działki

| Ustawienie | Znaczenie |
| --- | --- |
| `plots.maxPlots` | Maksymalna liczba działek gracza. Wartość 0 blokuje tworzenie nowych. |
| `plots.radius` | Początkowy promień działki. 16 daje teren 33 × 33 bloki. Używaj dodatniej liczby całkowitej. |
| `plots.world` | Świat, w którym można tworzyć działki. Podaj jego nazwę, np. `world`. `"*"` lub pusty tekst pozwala tworzyć działki we wszystkich światach. |
| `plots.expansion.defaultMaxRadius` | Domyślny maksymalny promień działki; używaj dodatniej liczby. |
| `plots.expansion.defaultMaxTotalArea` | Domyślny limit sumy powierzchni działek gracza; używaj dodatniej liczby. |

Rozmiar początkowy musi mieścić się w obu limitach. Limity promienia i powierzchni można zmienić dla rang przez [uprawnienia](Uprawnienia.md).

Zmiana początkowego promienia nie zmienia wielkości już utworzonych działek. Zmiana dozwolonego świata nie usuwa istniejących terenów.

## Rozszerzanie segmentami

`plots.expansion.price` to cena pierwszego segmentu (`0` = bez opłaty). Segment ma rozmiar pierwotnej działki, np. 33 × 33 bloki dla promienia 16. GUI pozwala wybrać północ, wschód, południe lub zachód. Punktem początkowym jest segment, w którym stoi gracz. Przejście do kolejnego segmentu pozwala skręcać i tworzyć dowolny połączony kształt. Zajętego sąsiada nie można przeskoczyć. Północ i wschód tworzą kształt L bez zajmowania pustego narożnika.

Przycisk „Pokaż granice” w GUI rozszerzania zamyka menu i pokazuje obrys istniejącej działki. Czas ustawia `plots.borderDisplaySeconds: 30` (sekundy, minimum 1); obowiązuje również w głównym panelu działki. Podgląd nie kupuje segmentu.

Cena kolejnych rozszerzeń rośnie według `price × priceMultiplier ^ liczba_zakupionych_segmentów`. Domyślny `plots.expansion.priceMultiplier: 1.5` daje ceny 500, 750, 1125, 1687.5. Mnożnik musi wynosić co najmniej 1; wartość 1 wyłącza wzrost. Licznik jest osobny dla każdej działki, uwzględnia istniejące segmenty i pozostaje po restarcie lub przekazaniu działki. Pierwotny teren i nieudane zakupy nie zwiększają licznika. Zmiana ceny bazowej lub mnożnika przelicza cenę następnego zakupu. Nieprawidłowe wartości lub wynik przekraczający zakres obsługiwany przez ekonomię blokują zakup.

Nie używa się już `levels` ani `step`. Przy aktualizacji usuń `levels` i ustaw `price: 500.0` lub własną cenę. Istniejące działki zachowują teren, a ich aktualny rozmiar staje się rozmiarem bazowym. Limity powierzchni liczą sumę segmentów; limit promienia określa najdalszą granicę względem pierwotnego środka. Szczegóły: [rozszerzanie i opłaty](../commands.md#rozszerzanie-działki-i-opłaty).

## Prywatne pojemniki

`privateChests.protectOnPlace: true` automatycznie zabezpiecza pojemniki stawiane przez właściciela lub członka działki.

`false` wyłącza automatyczne zabezpieczanie nowych pojemników. Nie usuwa wcześniejszych zabezpieczeń. Ręczna komenda `/pchest lock` pozostaje dostępna.

## Zapis danych

| Ustawienie | Znaczenie |
| --- | --- |
| `database.type` | Rodzaj bazy: `sqlite`, `h2`, `mysql`, `mariadb` lub `postgresql`. |
| `database.sql.host` | Adres osobnego serwera bazy danych. |
| `database.sql.port` | Port bazy. Domyślnie 3306; dla typowego PostgreSQL ustaw 5432. |
| `database.sql.dbname` | Nazwa bazy; przy SQLite również nazwa lokalnego pliku bez końcówki `.db`. |
| `database.sql.username` | Login do bazy. |
| `database.sql.password` | Hasło do bazy. |

Na prosty start pozostaw SQLite. Przy domyślnej nazwie dane znajdują się w `plugins/PlotsX/my_database.db`. SQLite nie wymaga hosta, portu, loginu ani hasła. H2 również zapisuje dane lokalnie, ale korzysta z ustawień logowania.

Dla zewnętrznej bazy wpisz dane otrzymane od hostingu. Zmiana nazwy lub rodzaju bazy **nie przenosi działek**. Zrób kopię danych przed taką zmianą, a potem uruchom serwer ponownie.

## Język i skróty

| Ustawienie | Znaczenie |
| --- | --- |
| `language` | Język wiadomości; `PL` to polski, `EN` to angielski. |
| `aliases.claim` | Dodatkowy skrót tworzenia działki, bez ukośnika. |
| `aliases.unclaim` | Dodatkowy skrót usuwania działki, bez ukośnika. |

Z pluginem dołączone są polskie i angielskie wiadomości. Config wymienia też `ES`, `FR` i `DE`; przed ich wybraniem upewnij się, że twoje wydanie ma odpowiednie tłumaczenie. W sprawie dodatkowych języków możesz skontaktować się ze społecznością.

Zmiana aliasu nie usuwa podstawowych komend. Po zmianie aliasów lub języka uruchom serwer ponownie.

## Pozostałe opcje

| Ustawienie | Domyślnie | Przeznaczenie |
| --- | --- | --- |
| `webhook.discord.enabled` | `false` | Ustawienie włączenia połączenia webhook z Discordem. |
| `webhook.discord.url` | `"YOUR_WEBHOOK_URL_HERE"` | Adres webhooka; przykładowy tekst trzeba zastąpić prawdziwym adresem. |
| `checkForUpdates` | `true` | Ustawienie sprawdzania aktualizacji. |
| `autoDownloadUpdates` | `false` | Ustawienie automatycznego pobierania aktualizacji. |
| `debug` | `true` | Szczegółowe wiadomości diagnostyczne w konsoli. |
| `stats.enabled` | `false` | Ustawienie zbierania statystyk działania. |

Te opcje dotyczą obsługi pluginu i mogą zależeć od wydania. Sam wpis webhooka nie określa, jakie zdarzenia będą wysyłane; PlotsX nie udostępnia tutaj listy powiadomień o działkach. Nie udostępniaj publicznie adresu webhooka ani hasła do bazy.

Jeśli nie diagnozujesz problemu, możesz ustawić `debug: false`, aby ograniczyć szczegółowe wpisy.

## Jak zastosować zmiany?

Po zmianie zwykłych ustawień działek użyj `/ptx reload`. Zmiana bazy, języka, aliasów lub integracji wymaga ponownego uruchomienia serwera. Gdy nie masz pewności, zapisz plik i wykonaj pełny restart.

`/ptx reload` odczytuje config, ale nie uruchamia całego pluginu ponownie ani nie wczytuje od nowa wszystkich danych działek.

Dalej: [administracja](Administracja.md).
