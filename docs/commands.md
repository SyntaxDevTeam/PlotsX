# Komendy PlotsX

Lista odpowiada aktualnej implementacji. `[argument]` jest opcjonalny, a `<argument>` wymagany. Uprawnienia opisano w [permissions.md](permissions.md).

## Działki

| Komenda | Uprawnienie | Działanie |
| --- | --- | --- |
| `/claim` | `plotsx.cmd.claim` | Otwiera potwierdzenie utworzenia działki w miejscu gracza. Obowiązują dozwolone światy, limity terenu i kontrola kolizji. |
| `/unclaim` | `plotsx.cmd.unclaim` | Otwiera potwierdzenie usunięcia własnej działki, na której stoi gracz. |
| `/plot` | `plotsx.cmd.plot` | Otwiera panel działki pod graczem, jeśli jest jej właścicielem, członkiem lub ma dostęp administracyjny; poza działką pokazuje listę własnych działek, również dla OP. |
| `/plot <nazwa>` | `plotsx.cmd.plot` | Otwiera panel dostępnej działki wyszukanej po nazwie; własna ma pierwszeństwo. |

Zwykłe komendy działek są dostępne tylko dla graczy; `/plot admin <id> <podkomenda>` działa również z konsoli. `/unclaim` nie obsługuje wyboru działki po nazwie, mimo że podpowiedzi komendy zawierają nazwy działek. Panel `/plot` udostępnia flagi, teleportację, zmianę nazwy, listę działek, wizualizację granic i rozszerzanie; rozszerzanie wymaga dodatkowo `plotsx.plot.expand`.

### Członkowie działki

Komendy wymagają `plotsx.cmd.plot` i stania na działce, do której gracz ma dostęp. Właściciel i administrator zarządzają wszystkimi opcjami; członkowie korzystają z grantów swojej roli.

| Komenda | Działanie i wymagany dostęp |
| --- | --- |
| `/plot add <gracz>` | Dodaje znanego serwerowi gracza po nicku lub UUID, również offline. Wymaga grantu `invite` lub własności/administracji. |
| `/plot remove <gracz>` | Usuwa członka. Grant `kick` pozwala usuwać wyłącznie niższe role: `member < builder < manager`. Właściciel/administrator nie podlega temu ograniczeniu. |
| `/plot members` | Otwiera panel członków, dostępny także członkom działki. |
| `/plot role <gracz> <member\|builder\|manager>` | Zmienia rolę członka; tylko właściciel/administrator. |
| `/plot permission <rola> <grant> <true\|false>` | Ustawia grant roli dla tej działki; tylko właściciel/administrator. |
| `/plot transfer <gracz> confirm` | Przekazuje własność obecnemu członkowi, który jest online; tylko właściciel/administrator. Sprawdza limity liczby, promienia i powierzchni odbiorcy oraz konflikt nazwy działki. |
| `/plot admin list <gracz/UUID>` | Wypisuje wszystkie działki właściciela (także offline): ID, nazwę, świat, pozycję i promień. Działa z konsoli i w grze; wymaga `plotsx.admin.manage`. |
| `/plot admin <id>` | Otwiera panel członków wskazanej działki; wymaga `plotsx.admin.manage`. |
| `/plot admin <id> <podkomenda> [argumenty]` | Wykonuje powyższe operacje na wskazanej działce, także z konsoli. Wymaga `plotsx.admin.manage`, bez osobnego wymogu `plotsx.cmd.plot`. `members` wypisuje listę. |

Przykłady: `/plot permission builder flag.build true`, `/plot role Alex manager`, `/plot admin 12 members`.
Granty to `invite`, `kick`, `rename` i `flag.<identyfikator>`; nie obsługują `flag.*`.

Członkowie mogą budować i korzystać z działki zgodnie z istniejącym mechanizmem uprawnień członków. Dostęp do cudzych prywatnych skrzyń wymaga osobnego udostępnienia. Po usunięciu członka udostępnienie przestaje pozwalać na dostęp, również w już otwartym oknie skrzyni. Lista udostępnień pozostaje zapisana w bloku: ponowne dodanie do działki przywraca taki dostęp. Własność jego własnych skrzyń pozostaje bez zmian. Nazwy `add`, `remove`, `members`, `role`, `permission`, `transfer` i `admin` są zarezerwowane jako podkomendy; działkę o takiej nazwie można otworzyć przez `/plot`, stojąc na niej.

Domyślne dodatkowe aliasy to `/c` dla `/claim` i `/unc` dla `/unclaim`. Można je zmienić w `config.yml` przez `aliases.claim` i `aliases.unclaim`.

## Rozszerzanie działki i opłaty

W panelu `/plot` wybierz rozszerzanie i potwierdź zakup. Wymagane są własność działki oraz `plotsx.plot.expand`. Konfiguracja:

```yaml
plots:
  expansion:
    price: 500.0
    defaultMaxRadius: 64
    defaultMaxTotalArea: 16641
```

GUI pozwala wybrać północ (−Z), wschód (+X), południe (+Z) lub zachód (−X), a następnie potwierdzić zakup. Każdy zakup dodaje jeden segment o rozmiarze pierwotnej działki, za stałą cenę `plots.expansion.price`. `0` oznacza darmowe rozszerzenie. Nie ma poziomów ani przyrostu promienia. Dla promienia 16 każdy segment ma 33 × 33 bloki i dodaje 1089 bloków².

Segmenty są dokładane od pierwotnej działki w wybranym kierunku. Powtórzenie kierunku przedłuża ten sam odcinek o kolejny segment. Zakup północy i wschodu daje kształt L; pusty północno-wschodni narożnik pozostaje poza działką. Ochrona obejmuje całą wysokość świata. Segmenty współdzielą właściciela, flagi i członków.

Sprawdzane są kolizje z rzeczywistymi segmentami innych działek i regionami WorldGuard, łączna powierzchnia oraz maksymalna odległość granicy od pierwotnego środka (`defaultMaxRadius` / `plotsx.plot.size.*`). Segment musi zmieścić się w całości. Nie zwiększa liczby działek właściciela.

Istniejące działki zachowują cały teren; ich aktualny rozmiar staje się stałym rozmiarem bazowym, ponieważ dawny system nie zapisywał pierwotnego promienia. Nowe segmenty są zapisywane w `plot_segments`. Historyczna tabela `plot_expansion_levels` pozostaje w kopiach zapasowych, ale nie steruje rozszerzaniem. Usuń `levels` z konfiguracji i ustaw `price` (domyślnie 500.0). Zmiana `plots.radius` nie zmienia wielkości segmentów istniejącej działki.

Plugin korzysta z VaultUnlocked lub Vault oraz dostawcy ekonomii. Zmiana ceny lub docelowego segmentu po otwarciu oferty wymaga ponownego otwarcia menu. OP i bypass nie zwalniają z opłaty.

Płatność jest pobierana przed transakcją rozszerzenia. Odrzucenie rozszerzenia powoduje próbę zwrotu przez tego samego dostawcę. Nieudany zwrot jest zgłaszany graczowi i zapisany w logu jako `REFUND REQUIRED` z UUID, ID działki i kwotą. Ekonomia i SQL nie są wspólną transakcją: awaria procesu lub niejednoznaczny błąd dostawcy/połączenia może wymagać ręcznego rozliczenia. Obsługa płatności i SQL odbywa się synchronicznie; wolna baza może opóźnić wątek obsługujący zakup.

Dokumentacja API: [Vault](https://milkbowl.github.io/VaultAPI/net/milkbowl/vault/economy/Economy.html), [VaultUnlocked](https://github.com/TheNewEconomy/VaultUnlockedAPI).

## Tworzenie działek: limity i światy

`/claim` korzysta z `plots.world`, które przyjmuje listę, np. `["world", "survival"]`.
Stary pojedynczy wpis nadal działa; `["*"]` dopuszcza wszystkie światy, a `[]` blokuje tworzenie.
Światy muszą być załadowane, np. przez Multiverse-Core; osobna integracja nie jest wymagana.

`plotsx.plot.max-plots.<liczba>` nadpisuje `plots.maxPlots`, a `plotsx.plot.radius.<promień>`
nadpisuje `plots.radius`. Najwyższa przyznana wartość danego uprawnienia wygrywa.
Liczba i powierzchnia działek są liczone łącznie we wszystkich światach.
Promień początkowy podlega również limitowi `plotsx.plot.size.<promień>` i łącznej powierzchni.
Limity i świat są sprawdzane ponownie przy potwierdzeniu utworzenia działki.

## Prywatne skrzynie

Wszystkie poniższe komendy wymagają `plotsx.cmd.privatechest`. Alias `/pchest` działa tak samo jak `/privatechest`. Gracz musi patrzeć na skrzynię, skrzynię-pułapkę, beczkę lub shulker box na działce, w zasięgu 6 bloków.

| Komenda | Działanie |
| --- | --- |
| `/privatechest` | Pokazuje składnię po sprawdzeniu wskazanego kontenera i działki. |
| `/privatechest lock` | Przypisuje niezabezpieczony kontener do wykonującego komendę właściciela lub członka działki; dostępny jest również bypass administracyjny. |
| `/privatechest unlock` | Usuwa prywatną ochronę kontenera. |
| `/privatechest trust <gracz>` | Udostępnia kontener wskazanemu właścicielowi lub członkowi działki. |
| `/privatechest share <gracz>` | Synonim `trust`. |
| `/privatechest untrust <gracz>` | Cofa udostępnienie kontenera. |
| `/privatechest unshare <gracz>` | Synonim `untrust`. |
| `/privatechest info` | Pokazuje właściciela kontenera i listę udostępnień. |

`unlock`, `trust` i `untrust` wymagają własności skrzyni lub bypassu administracyjnego. Argument gracza może być nickiem znanym serwerowi albo UUID. Obecnie również cofanie udostępnienia wyszukuje gracza wyłącznie wśród aktualnego właściciela i członków działki. Samo udostępnienie nie pozwala niszczyć skrzyni ani zarządzać jej ochroną.

Nowe kontenery właścicieli i członków działki są automatycznie zabezpieczane, gdy `privateChests.protectOnPlace` ma wartość `true` (w dołączonym configu: `false`). Istniejące kontenery można zabezpieczyć przez `lock`.

## Obsługa pluginu

`/plotsx` i `/ptx` obsługują ten sam zestaw podkomend. Wszystkie wymagają `plotsx.cmd.ptx` i mogą być wykonywane również z konsoli.

| Komenda | Działanie |
| --- | --- |
| `/ptx` | Wyświetla wskazówkę użycia `/ptx help`. |
| `/ptx help [strona]` | Wyświetla wbudowaną, skróconą pomoc. Domyślna strona: 1. |
| `/ptx version` | Wyświetla nazwę pluginu, autorów, witrynę i wersję. |
| `/ptx reload` | Ponownie wczytuje konfigurację pluginu; nie przeładowuje całego pluginu ani cache działek. |
| `/ptx export` | Uruchamia zapis do `dump/backup.sql` w katalogu danych pluginu. Obecna implementacja odwołuje się do tabel `punishments` i `punishmenthistory`, więc nie jest poprawnym eksportem działek PlotsX. |
| `/ptx import` | Wykonuje SQL z `dump/backup.sql` w katalogu danych pluginu. Nie odświeża cache działek po imporcie. |

`reload`, `export` i `import` nie mają osobnych kontroli uprawnień administracyjnych — uprawnienie `plotsx.cmd.ptx` pozwala wywołać je wszystkie.

## Nazwy działek i CleanerX

Zmiana nazwy sprawdza `containsBannedWord` z API CleanerX. Wykryte zakazane słowo lub pusta nazwa powoduje odrzucenie zmiany, również dla OP. Gdy zainstalowany CleanerX jest wyłączony, ma niezgodne API lub zgłasza błąd, zapis nazwy jest blokowany. Bez zainstalowanego CleanerX filtr słów nie działa. Obowiązuje słownik i whitelist CleanerX; istniejące nazwy nie są automatycznie zmieniane.
