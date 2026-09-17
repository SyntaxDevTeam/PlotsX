# PlotsX — Alpha-02: status dodatkowych flag i zabezpieczeń

> Stan repozytorium zweryfikowany na gałęzi `main`, commit `c4ce7dc246f0a3693b6504e269ba17a74289ef79`.
>
> Dokument opisuje nie tylko obecność wpisu w `PlotFlagRegistry`, ale również faktyczną obsługę zdarzeń w `PlotProtectionListener`, obecność w GUI i zgodność z pierwotnym założeniem Alpha-02.

## Legenda

- ✅ — zaimplementowane i faktycznie obsługiwane,
- 🟡 — funkcjonalność istnieje, ale pod inną nazwą, wspólną flagą lub nie obejmuje jeszcze całego planowanego zakresu,
- ❌ — brak implementacji,
- ➖ — pomysł świadomie zastąpiony innym rozwiązaniem / osobna flaga nie jest obecnie potrzebna.

---

# Zbiór pomysłów: Alpha-02

## Dodatkowe flagi i zabezpieczenia

### Zabezpieczenie przed kradzieżą zwierząt

- ✅ ~~Flaga `animal-leash` — blokująca przywiązywanie zwierząt do liny~~
  - Nie istnieje jako osobna flaga.
  - Funkcjonalność została połączona we wspólną flagę `animal-interact`.
  - Obsługiwane są `PlayerLeashEntityEvent` oraz `PlayerUnleashEntityEvent`.

- ✅ ~~Flaga `animal-ride` — blokująca wsiadanie na zwierzęta (konie, muły, osły)~~
  - Nie istnieje jako osobna flaga.
  - Obsługiwana przez `animal-interact`.
  - Wykorzystywany jest `EntityMountEvent`.

- ✅ ~~Flaga `animal-breed` — blokująca rozmnażanie zwierząt~~
  - Nie istnieje jako osobna flaga.
  - Obsługiwana przez `animal-interact`.
  - Wykorzystywany jest `EntityBreedEvent`.

- 🟡 **Jedna flaga `passive`, która to wszystko blokuje automatycznie.**
  - Konsolidacja została wykonana, ale nie pod nazwą `passive`.
  - Obecna flaga `animal-interact` kontroluje interakcje ze zwierzętami, m.in. smycz, rozmnażanie, wsiadanie i część pozostałych interakcji.
  - Obecna flaga `passives` ma inne znaczenie: blokuje zadawanie obrażeń pasywnym zwierzętom.
  - Taki podział jest czytelniejszy i warto go zachować:
    - `passives` — ochrona zwierząt przed obrażeniami,
    - `animal-interact` — ochrona przed interakcjami/kradzieżą/użyciem zwierząt.

### Zabezpieczenie przed kradzieżą przedmiotów

- ✅ ~~Flaga `item-pickup` — blokująca podnoszenie przedmiotów z ziemi~~
  - Jest już pełnoprawną flagą.
  - Obsługiwana przez `EntityPickupItemEvent`.
  - Dotyczy graczy podnoszących przedmioty znajdujące się na terenie działki.

- ✅ ~~Flaga `item-drop` — blokująca wyrzucanie przedmiotów na działce~~
  - Jest już pełnoprawną flagą.
  - Obsługiwana przez `PlayerDropItemEvent`.

### Zabezpieczenie przed niszczeniem roślin

- ✅ **Flaga `crop-trample` — blokująca niszczenie upraw przez skakanie.**
  - Zaimplementowana.
  - Obsługiwana przez `PlayerInteractEvent`.
  - Sprawdzane jest `Action.PHYSICAL` na `Material.FARMLAND`.
  - Spełnia wymaganie oznaczone wcześniej jako konieczne jeszcze dla Alpha-01.

- ➖ ~~Flaga `sapling-trample` — blokująca niszczenie sadzonek~~
  - Osobna flaga nie została dodana.
  - Zgodnie z założeniem ten przypadek pozostaje pod ochroną budowania/niszczenia bloków, czyli `build`.

### Zabezpieczenie przed używaniem przedmiotów

- ➖ ~~Flaga `item-use` — blokująca używanie przedmiotów (np. łuki, miecze, narzędzia)~~
  - Nie istnieje jedna ogólna flaga `item-use`.
  - Zakres został rozdzielony na bardziej precyzyjne zabezpieczenia, m.in.:
    - `projectiles`,
    - `use-potions`,
    - `special-weapons`,
    - `fishing`,
    - `elytra`.
  - Obecny podział daje większą kontrolę właścicielowi działki.

- ✅ **Flaga `fishing` — blokująca łowienie ryb.**
  - Zaimplementowana.
  - Obsługiwana przez `PlayerFishEvent`.
  - Sprawdzane są lokalizacje gracza, haczyka oraz złowionego obiektu.

### Zabezpieczenie przed interakcją z blokami

- ✅ **Flaga `bed-use` — blokująca używanie łóżek.**
  - Zaimplementowana.
  - Obejmuje wszystkie materiały, których nazwa kończy się na `_BED`.
  - Spełnia wymaganie oznaczone wcześniej jako konieczne jeszcze dla Alpha-01.

- ✅ **Flaga `crafting` — blokująca używanie stołów rzemieślniczych jako osobna flaga poza `utility`.**
  - Zaimplementowana.
  - Obejmuje `CRAFTING_TABLE` oraz `CRAFTER`.
  - Jest oddzielona od ogólnej flagi `utility`.
  - Automatycznie pojawia się w GUI flag.

- ✅ **Flaga `enchanting` — blokująca używanie stołów do zaklęć jako osobna flaga poza `utility`.**
  - Zaimplementowana.
  - Obejmuje `ENCHANTING_TABLE`.
  - Jest oddzielona od `utility`.
  - Automatycznie pojawia się w GUI flag.

### Zabezpieczenie przed efektami pogodowymi

- 🟡 **Flaga `weather-damage` — blokująca obrażenia od piorunów.**
  - Funkcjonalność istnieje pod wspólną flagą `weather`.
  - Obsługiwane są:
    - `LightningStrikeEvent`,
    - `EntityDamageEvent` z `DamageCause.LIGHTNING`.
  - Osobna flaga `weather-damage` nie jest obecnie potrzebna, jeśli akceptujemy wspólną kontrolę przez `weather`.

- ❌ **Flaga `weather-effects` — blokująca efekty pogodowe (deszcz, burza).**
  - Brak implementacji.
  - Obecne `weather` chroni przed piorunami, ale nie tworzy lokalnej pogody dla gracza na działce.
  - Do realizacji wymagane byłoby sterowanie pogodą per-player albo inne rozwiązanie symulujące lokalne warunki pogodowe.

### Zabezpieczenie przed używaniem komend

- ✅ **Flaga `command-use` — blokująca używanie komend na działce.**
  - Zaimplementowana przez `PlayerCommandPreprocessEvent`.
  - `/home` i `/sethome` mają osobną kontrolę przez `allow-home`.
  - Bezpieczne wyjątki dla komend PlotsX:
    - `/plotsx`,
    - `/ptx`,
    - `/plot`,
    - `/claim`,
    - `/unclaim`.

### Zabezpieczenie przed używaniem przedmiotów specjalnych

- ✅ **Flaga `elytra-use` — blokująca używanie elytry.**
  - Funkcjonalność istnieje pod nazwą `elytra`.
  - Blokowane jest rozpoczęcie szybowania przez `EntityToggleGlideEvent`.

- 🟡 **Flaga `trident-use` — blokująca używanie trójzębu.**
  - Funkcjonalność została połączona z `special-weapons`.
  - Obsługiwane jest:
    - rzucanie trójzębem przez `ProjectileLaunchEvent`,
    - zadawanie obrażeń trójzębem.
  - Do sprawdzenia / uzupełnienia pozostaje obsługa Riptide (`PlayerRiptideEvent`), aby nie można było ominąć ochrony poprzez użycie trójzębu do przemieszczania się.

- ✅ **Flaga `mace-use` — blokująca używanie buzdyganu.**
  - Funkcjonalność istnieje pod wspólną flagą `special-weapons`.
  - Blokowane są obrażenia zadawane przez `MACE`.

### Zabezpieczenie przed używaniem bloków specjalnych

- 🟡 **Flaga `conduit-use` — blokująca używanie przewodników.**
  - Funkcjonalność istnieje jako `conduit-effects`.
  - Blokowane jest nakładanie efektu przewodnika przez `EntityPotionEffectEvent.Cause.CONDUIT`.
  - To rozwiązanie odpowiada faktycznemu mechanizmowi działania conduitów lepiej niż blokowanie zwykłej interakcji kliknięciem.

- ✅ **Flaga `respawn-anchor-use` — blokująca używanie kotwic odrodzenia.**
  - Funkcjonalność istnieje pod nazwą `respawn-anchor`.
  - Obsługiwana podczas interakcji z `Material.RESPAWN_ANCHOR`.

### Zabezpieczenie przed używaniem przedmiotów dekoracyjnych

- 🟡 **Flaga `frame-use` — blokująca używanie ramek na przedmioty.**
  - Funkcjonalność jest częściowo objęta wspólną flagą `decorations`.
  - Chronione jest m.in.:
    - umieszczanie obiektów wiszących,
    - niszczenie obiektów wiszących,
    - usuwanie przez gracza lub pocisk.
  - Potencjalna luka: brak dedykowanej obsługi interakcji z już istniejącym `ItemFrame` / `GlowItemFrame` podczas obracania lub podmiany przedmiotu.
  - **Do uzupełnienia.**

- ✅ **Flaga `armor-stand-use` — blokująca używanie stojaków na zbroję.**
  - Funkcjonalność istnieje pod wspólną flagą `decorations`.
  - Obsługiwane jest:
    - `PlayerArmorStandManipulateEvent`,
    - niszczenie ArmorStanda przez gracza lub pocisk.

---

# GUI flag

`FlagsGUI` nie posiada ręcznie utrzymywanej listy flag.

GUI iteruje po `PlotFlagRegistry.allFlags`, dlatego każda poprawnie zarejestrowana flaga automatycznie pojawia się w panelu. Dotyczy to między innymi:

- `crop-trample`,
- `animal-interact`,
- `item-pickup`,
- `item-drop`,
- `fishing`,
- `command-use`,
- `elytra`,
- `special-weapons`,
- `weather`,
- `bed-use`,
- `crafting`,
- `enchanting`,
- `respawn-anchor`,
- `conduit-effects`,
- `decorations`.

Dla nowych flag nie trzeba ręcznie dodawać osobnych slotów, o ile zostaną poprawnie dodane do `PlotFlagRegistry` wraz z nazwą, opisem i materiałem.

---

# Najważniejsze braki do domknięcia Alpha-02

## 1. `weather-effects`

Status: ❌ brak.

Do zaprojektowania pozostaje sposób blokowania lub ukrywania deszczu/burzy dla gracza znajdującego się na działce. Należy pamiętać, że standardowa pogoda świata nie jest ograniczona do regionu działki, więc najprawdopodobniej potrzebne będzie rozwiązanie per-player.

## 2. Pełna ochrona ItemFrame / GlowItemFrame

Status: 🟡 częściowo.

`decorations` chroni obecnie umieszczanie i niszczenie dekoracji, ale trzeba upewnić się, że blokowane są również:

- wkładanie przedmiotu do ramki,
- wyjmowanie przedmiotu,
- podmiana przedmiotu,
- obracanie przedmiotu,
- analogiczne operacje dla `GlowItemFrame`.

## 3. Trident + Riptide

Status: 🟡 do weryfikacji / uzupełnienia.

`special-weapons` blokuje rzucanie trójzębem oraz obrażenia, ale warto dodać kontrolę `PlayerRiptideEvent`, aby użytkownik nie mógł wykorzystać trójzębu do poruszania się na działce mimo blokady.

---

# Podsumowanie

Alpha-02 jest już w zdecydowanej większości zaimplementowana.

Najważniejsza zmiana względem pierwotnej listy polega na scaleniu części bardzo szczegółowych flag w bardziej ogólne grupy:

| Pierwotny pomysł | Aktualna flaga |
| --- | --- |
| `animal-leash` | `animal-interact` |
| `animal-ride` | `animal-interact` |
| `animal-breed` | `animal-interact` |
| ochrona zwierząt przed obrażeniami | `passives` |
| `elytra-use` | `elytra` |
| `trident-use` | `special-weapons` |
| `mace-use` | `special-weapons` |
| `weather-damage` | `weather` |
| `conduit-use` | `conduit-effects` |
| `respawn-anchor-use` | `respawn-anchor` |
| `frame-use` | `decorations` |
| `armor-stand-use` | `decorations` |

Aktualna architektura jest bardziej zwarta i pozwala uniknąć nadmiernego rozdrobnienia GUI flag.

Do pełnego zamknięcia tej listy pozostają przede wszystkim:

1. `weather-effects`,
2. pełna ochrona interakcji z `ItemFrame` / `GlowItemFrame`,
3. obsługa Riptide dla `special-weapons`.

Po zaimplementowaniu tych elementów Alpha-02 można uznać za funkcjonalnie domkniętą w zakresie tej checklisty.
