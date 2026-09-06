# Config — ustawienia serwera

[← Home](Home.md)

Ustawienia znajdziesz w `plugins/PlotsX/config.yml`. Wartość `true` oznacza „tak”, a `false` — „nie”. Zachowaj wcięcia i używaj spacji zamiast tabulatorów.

## Pełna domyślna konfiguracja

```yaml
plots:
  maxPlots: 5
  radius: 16
  world: "world"
  expansion:
    levels:
      1:
        step: 8
        price: 500.0
      2:
        step: 8
        price: 1000.0
      3:
        step: 16
        price: 2000.0
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

## Poziomy rozszerzania

Każdy numer w `plots.expansion.levels` to następny poziom:

- `step` — o ile bloków przesuwa się każda granica. Musi być dodatnią liczbą całkowitą.
- `price` — cena tego poziomu. `0` oznacza rozszerzenie za darmo. Cena nie może być ujemna.

Numeruj poziomy kolejno: 1, 2, 3 i dalej. Brak następnego numeru kończy dostępną ścieżkę rozszerzania.

Przykład dwóch darmowych ulepszeń — zastąp nim całą sekcję `levels`:

```yaml
    levels:
      1:
        step: 8
        price: 0.0
      2:
        step: 8
        price: 0.0
```

Zmiana ceny lub kroku nie zmienia zakupionego terenu ani osiągniętego poziomu. Po zmianie oferty gracze powinni ponownie otworzyć menu. Starsze ustawienia `plots.expansion.step` i `plots.expansion.price` nie zastępują listy poziomów.

Przy aktualizacji ze starszego systemu istniejące działki zachowują rozmiar, ale zaczynają ścieżkę poziomów od 0. Wcześniejsze rozszerzenia nie są przeliczane.

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
