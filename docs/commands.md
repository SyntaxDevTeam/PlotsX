# Komendy PlotsX

Lista odpowiada aktualnej implementacji. `[argument]` jest opcjonalny, a `<argument>` wymagany. Uprawnienia opisano w [permissions.md](permissions.md).

## Działki

| Komenda | Uprawnienie | Działanie |
| --- | --- | --- |
| `/claim` | `plotsx.cmd.claim` | Otwiera potwierdzenie utworzenia działki w miejscu gracza. Obowiązują dozwolone światy, limity terenu i kontrola kolizji. |
| `/unclaim` | `plotsx.cmd.unclaim` | Otwiera potwierdzenie usunięcia własnej działki, na której stoi gracz. |
| `/plot` | `plotsx.cmd.plot` | Otwiera panel działki pod graczem, jeśli jest jej właścicielem lub ma bypass; poza działką pokazuje listę własnych działek. |
| `/plot <nazwa>` | `plotsx.cmd.plot` | Otwiera panel własnej działki wyszukanej po nazwie. |

Te komendy są dostępne tylko dla graczy. `/unclaim` nie obsługuje wyboru działki po nazwie, mimo że podpowiedzi komendy zawierają nazwy działek. Panel `/plot` udostępnia flagi, teleportację, zmianę nazwy, listę działek, wizualizację granic i rozszerzanie; rozszerzanie wymaga dodatkowo `plotsx.plot.expand`.

### Członkowie działki

Komendy wymagają `plotsx.cmd.plot` i stania na własnej działce. Tylko właściciel może zarządzać członkostwem oraz wyświetlać listę.

| Komenda | Działanie |
| --- | --- |
| `/plot add <gracz>` | Dodaje gracza znanego serwerowi po nicku lub UUID, także gdy jest offline. |
| `/plot remove <gracz>` | Usuwa członka po nicku lub UUID. |
| `/plot members` | Wyświetla właściciela i członków działki. |

Członkowie mogą budować i korzystać z działki zgodnie z istniejącym mechanizmem uprawnień członków. Dostęp do cudzych prywatnych skrzyń wymaga osobnego udostępnienia. Po usunięciu członka udostępnienie przestaje pozwalać na dostęp, również w już otwartym oknie skrzyni. Lista udostępnień pozostaje zapisana w bloku: ponowne dodanie do działki przywraca taki dostęp. Własność jego własnych skrzyń pozostaje bez zmian. Nazwy `add`, `remove` i `members` są zarezerwowane jako podkomendy; działkę o takiej nazwie można otworzyć przez `/plot`, stojąc na niej.

Domyślne dodatkowe aliasy to `/c` dla `/claim` i `/unc` dla `/unclaim`. Można je zmienić w `config.yml` przez `aliases.claim` i `aliases.unclaim`.

## Rozszerzanie działki i opłaty

W panelu `/plot` wybierz rozszerzanie i potwierdź zakup. Wymagane są własność działki oraz `plotsx.plot.expand`. Konfiguracja:

```yaml
plots:
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
```

Każda działka zaczyna od poziomu 0. Kupuje się kolejno poziomy 1, 2, 3 itd.; brak następnego poziomu kończy rozszerzanie. Każdy poziom określa własne `step` (dodatni przyrost promienia) oraz `price` (opłata za ten poziom w domyślnej walucie). Zero oznacza brak opłaty i nie wymaga ekonomii. Nieprawidłowa cena lub niedodatni krok blokują zakup. GUI pokazuje docelowy poziom, promień i cenę. Zmiana oferty po otwarciu GUI wymaga ponownego otwarcia panelu. OP i bypass nie zwalniają z opłaty.

Poziom jest zapisywany w tabeli `plot_expansion_levels` w tej samej transakcji co promień. Błąd lub kolizja nie zwiększa poziomu. Istniejące działki zachowują rozmiar i zaczynają nowy system od poziomu 0 — wcześniejsze rozszerzenia nie są przeliczane na poziomy. Dawne `plots.expansion.step` i `plots.expansion.price` nie są już używane. Zmiana konfiguracji poziomów nie zmienia zakupionego terenu ani zapisanego poziomu.

Plugin wybiera usługę Economy VaultUnlocked (API v2), a gdy jest niedostępna — klasyczne Vault. Potrzebny jest również plugin ekonomii rejestrujący tę usługę. Sam Vault nie zapewnia kont ani sald. Brak usługi lub odrzucona płatność blokuje płatne rozszerzenie.

Środek `(x, z)` pozostaje bez zmian. Promień rośnie o `step` kupowanego poziomu, więc każda z czterech granic przesuwa się na zewnątrz o tę liczbę bloków: zachód `−X`, wschód `+X`, północ `−Z`, południe `+Z`. Nie wybiera się kierunku na podstawie pozycji lub spojrzenia gracza. Ochrona nadal obejmuje całą wysokość świata.

| Parametr | Przed | Po jednym domyślnym kroku |
| --- | --- | --- |
| Promień | 16 | 24 |
| Bok (`2r + 1`) | 33 | 49 |
| Powierzchnia | 1089 | 2401 |

Przyrost wynosi 1312 bloków². Sprawdzane są limit promienia, łączna powierzchnia działek właściciela, kolizje z innymi działkami i regionami WorldGuard. Gdy pełny krok nie mieści się w limicie, operacja jest odrzucana, a nie przycinana do limitu.

Płatność jest pobierana przed transakcją rozszerzenia. Odrzucenie rozszerzenia powoduje próbę zwrotu przez tego samego dostawcę. Nieudany zwrot jest zgłaszany graczowi i zapisany w logu jako `REFUND REQUIRED` z UUID, ID działki i kwotą. Ekonomia i SQL nie są wspólną transakcją: awaria procesu lub niejednoznaczny błąd dostawcy/połączenia może wymagać ręcznego rozliczenia. Obsługa płatności i SQL odbywa się synchronicznie; wolna baza może opóźnić wątek obsługujący zakup.

Dokumentacja API: [Vault](https://milkbowl.github.io/VaultAPI/net/milkbowl/vault/economy/Economy.html), [VaultUnlocked](https://github.com/TheNewEconomy/VaultUnlockedAPI).

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

Nowe kontenery właścicieli i członków działki są automatycznie zabezpieczane, gdy `privateChests.protectOnPlace` ma wartość `true` (domyślnie). Istniejące kontenery można zabezpieczyć przez `lock`.

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
