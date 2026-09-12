# 04 — Konfiguracja, limity i integracje

[Spis planu](README.md) · [Realizacja i testy](05-realizacja-testy-wdrozenie.md)

## 1. Proponowana konfiguracja

Poniższy fragment jest projektem przyszłego configu. Nie należy wklejać go do obecnej wersji w oczekiwaniu obsługi chunków. Wartości nowych limitów i cen są proponowanymi wartościami początkowymi do testów balansu.

```yaml
plots:
  claiming:
    # Wybór metody tworzenia NOWYCH działek: classic | chunks.
    # Zmiana wymaga restartu; ochrona zawsze obsługuje oba typy.
    mode: classic

  # Istniejące ustawienia wspólne:
  maxPlots: 5
  world: ["world"]
  borderDisplaySeconds: 30

  # Istniejące ustawienia geometrii klasycznej:
  radius: 16
  expansion:
    price: 500.0
    priceMultiplier: 1.5
    defaultMaxRadius: 64
    # Pozostaje WSPÓLNYM limitem powierzchni obu typów.
    defaultMaxTotalArea: 16641

  chunks:
    maxPerPlot: 32
    maxTotalOwned: 64
    expansion:
      price: 500.0
      priceMultiplier: 1.5
```

Nie przenosić przy okazji istniejącego `defaultMaxTotalArea` pod nową ścieżkę: utrzymanie klucza zmniejsza ryzyko zmiany limitów po aktualizacji. Jego wspólne znaczenie należy wyraźnie opisać w komentarzu. Pierwsza wersja ma stały rozmiar chunka, jeden chunk początkowy i obowiązkowe przyleganie bokiem; nie wymaga przełączników dla każdej reguły.

## 2. Walidacja i przeładowanie

| Wartość | Reguła |
| --- | --- |
| Brak `plots.claiming.mode` | Użyć `classic` i uzupełnić brakującą konfigurację. |
| Jawnie podany tryb | Akceptować `classic` lub `chunks`, po usunięciu skrajnych spacji i normalizacji liter. |
| Nieznany tryb lub błędny typ YAML | Odrzucić konfigurację z nazwą klucza i oczekiwanym typem. |
| `maxPerPlot`, `maxTotalOwned` | Nieujemne liczby całkowite; 0 blokuje dodanie chunków, nie usuwa istniejących. |
| Cena | Skończona liczba dziesiętna ≥ 0, parsowana zgodnie z mechanizmem cen. |
| Mnożnik | Skończona liczba dziesiętna ≥ 1. |
| `plots.world` | Zachować obsługę listy, starszego stringa i dotychczasowych wartości specjalnych. |

`ConfigHandler` obecnie uzupełnia brakujące sekcje. Przed rekurencyjną synchronizacją trzeba sprawdzić ich typ, aby np. `claiming: false` kończyło się zrozumiałą diagnozą, a nie wyjątkiem null. Błędny jawny tryb nie może cicho przełączyć serwera na inną metodę.

Przy reloadzie odrzucić zmianę trybu i zachować działający snapshot konfiguracji, informując o konieczności restartu. Pozostałe wspierane zmiany stosować atomowo dopiero po pełnej walidacji. Zmiana limitów lub cen unieważnia otwarte oferty. Przy błędzie startowym wymagany jest tryb utrzymaniowy bez dostępu graczy; przy błędzie reloadu pozostaje dotychczasowa ochrona i poprawna konfiguracja.

## 3. Uprawnienia i naliczanie limitów

| Uprawnienie | Zastosowanie |
| --- | --- |
| Dotychczasowe prawa do claim, unclaim i zarządzania | Wspólne; zachować obecne sprawdzanie w `PermissionChecker`. |
| `plotsx.plot.max-plots.<n>` | Łączna liczba działek wszystkich typów i światów. |
| `plotsx.plot.max-area.<n>` | Łączna powierzchnia obu typów w kolumnach bloków. |
| `plotsx.plot.radius.<n>` | Wyłącznie początkowy promień klasyczny. |
| `plotsx.plot.size.<n>` | Wyłącznie klasyczny limit rozmiaru. |
| `plotsx.plot.max-chunks-per-plot.<n>` — nowe | Maksymalna liczba chunków jednej działki chunkowej. |
| `plotsx.plot.max-chunks.<n>` — nowe | Suma chunków wszystkich działek chunkowych właściciela. |

Dla nowych uprawnień numerycznych zachować zasadę najwyższej jawnie przyznanej poprawnej liczby; wartości ujemne, nienumeryczne i przepełnione odrzucać. Wildcard nie oznacza automatycznie nieskończonego limitu. Nie dodawać niejawnego pomijania limitów tylko dlatego, że gracz jest operatorem.

Klasyczna działka przecinająca cztery chunki nie zużywa czterech jednostek limitu chunkowego. Zużywa rzeczywistą powierzchnię i jedną jednostkę liczby działek. Rozdzielenie jednostek musi być widoczne w GUI.

Warunek przyjęcia nowego chunka: liczba działek po operacji ≤ limit działek, powierzchnia po operacji ≤ limit powierzchni, chunki danej działki ≤ limit na działkę i wszystkie chunki właściciela ≤ limit globalny chunków. W rozszerzeniu liczba działek pozostaje bez zmian. W transferze stosować limity odbiorcy i aktualny stan w transakcji. Sumy obliczać w `Long` z ochroną przed przepełnieniem.

## 4. Ekonomia

Cena kolejnego chunkowego rozszerzenia: `basePrice * multiplier ^ purchasedExpansions`. Pierwszy chunk nie zwiększa licznika zakupów. Dla ceny 500 i mnożnika 1,5 trzy rozszerzenia kosztują 500, 750 i 1125. Nie współdzielić licznika między różnymi działkami; zmiana trybu serwera go nie resetuje.

Wykorzystać `ExpansionPricing` i `ExpansionEconomy` po wydzieleniu wyboru konfiguracji cennika. Walidować precyzję oraz konwersję kwoty do typu używanego przez dostawcę. Cena 0 nie wymaga dostawcy ekonomii. Przy cenie dodatniej i braku dostawcy rozszerzanie jest niedostępne z jednoznacznym komunikatem. Protokół błędów i zwrotów opisuje [dokument transakcji](03-dane-transakcje-migracja.md).

## 5. GUI, dialogi i lokalizacja

Zachować obie istniejące ścieżki interakcji: menu ekwipunku i natywne dialogi. Obie konsumują ten sam model oferty i serwis, aby ponowna walidacja nie zależała od interfejsu. Sesje z `DialogSessions` należy objąć unieważnianiem po zmianie danych, wylogowaniu i zatrzymaniu pluginu.

Panel działki pokazuje typ, powierzchnię, a dla chunkowej również liczbę chunków. Panel rozszerzania wskazuje źródło, cel, przyrost powierzchni, cenę i limity po zakupie. Podgląd granic rysuje zewnętrzne krawędzie zajętych chunków; wspólne krawędzie wewnętrzne pomija, a puste obszary wewnątrz działki pozostają wyraźnie wyłączone. Ograniczyć liczbę cząsteczek i czas zadania, wykorzystując `borderDisplaySeconds`.

Planowane grupy komunikatów: typ działki, współrzędne chunka, kolizja mieszana, limit chunków na działkę, limit chunków właściciela, zmieniona oferta, niedostępna płatność, niepewne rozliczenie, restart wymagany po zmianie trybu. Dodać tłumaczenia do EN, PL, ES, FR i DE oraz sprawdzić placeholdery. W komunikatach dla gracza nie ujawniać surowych wyjątków SQL.

## 6. Integracje

**WorldGuard:** rozszerzyć `RegionProtectionHook` o operację na dokładnych granicach prostokąta; dotychczasową sygnaturę klasyczną można zachować jako adapter. Chunk sprawdzać od `minX/minZ` do `maxX/maxZ` w pełnej wysokości świata. Zachować bieżącą politykę pomijania `__global__` i odrzucania zajęcia po błędzie integracji, chyba że zostanie zmieniona odrębną decyzją. Kontrola musi objąć cały kandydat, nie tylko pozycję gracza. Kontrolę Bukkit/WorldGuard wykonywać na właściwym wątku, możliwie blisko zatwierdzenia. Zmiany zewnętrznego regionu nie są częścią transakcji SQL; to ograniczenie należy uwzględnić operacyjnie.

**Prywatne kontenery:** zachować zapisaną własność i reguły dostępu. Zmienić jedynie sposób ustalenia działki. Sprawdzić podwójne skrzynie na granicy, kontenery po usunięciu działki oraz transfer właściciela działki.

**CoreProtect i pozostałe hooki:** przejrzeć, czy operują na lokalizacji, ID, promieniu lub prostokącie. Tam, gdzie wystarczy wspólny resolver, użyć go bez tworzenia osobnej gałęzi integracji. Zachować dotychczasowe warunki opcjonalnego ładowania dostawców.

**Publiczne API:** aktualizować razem z dokumentacją [API](../../api.md) i przykładem integracji. Konsument nie może wyliczać granic chunkowej działki z `radius`. Polityka zgodności i bramka API są opisane w [architekturze](02-architektura-i-geometria.md).
