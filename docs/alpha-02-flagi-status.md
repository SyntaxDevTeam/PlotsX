# PlotsX — Alpha-02: status dodatkowych flag i zabezpieczeń

> Stan repozytorium zweryfikowany ponownie 17.09.2026 na gałęzi `main`.
>
> Dokument opisuje nie tylko obecność wpisu w `PlotFlagRegistry`, ale również faktyczną obsługę zdarzeń, obecność w GUI i zgodność z pierwotnym założeniem Alpha-02.

## Legenda

- ✅ — zaimplementowane i faktycznie obsługiwane,
- 🟡 — funkcjonalność istnieje, ale pod inną nazwą, wspólną flagą lub nie obejmuje jeszcze całego planowanego zakresu,
- ❌ — brak implementacji,
- ➖ — pomysł świadomie zastąpiony innym rozwiązaniem / osobna flaga nie jest obecnie potrzebna.

---

# Zbiór pomysłów: Alpha-02

## Dodatkowe flagi i zabezpieczenia

### Zabezpieczenie przed kradzieżą zwierząt

- ✅ **Flaga `animal-leash` — blokująca przywiązywanie i odwiązywanie zwierząt.**
  - Jest osobną flagą typu `WHITELIST`, domyślnie `false`.
  - Obsługuje `PlayerLeashEntityEvent` oraz `PlayerUnleashEntityEvent`.
  - Paper udostępnia te zdarzenia jako niezależne i anulowalne, więc nie ma potrzeby łączenia smyczy z jazdą lub rozmnażaniem.

- ✅ **Flaga `animal-ride` — blokująca wsiadanie na zwierzęta.**
  - Jest osobną flagą typu `WHITELIST`, domyślnie `false`.
  - Obsługiwana przez anulowalny `EntityMountEvent`.
  - Ochrona jest stosowana do bytów rozpoznawanych przez PlotsX jako pasywne zwierzęta.

- ✅ **Flaga `animal-breed` — blokująca rozmnażanie zwierząt.**
  - Jest osobną flagą typu `WHITELIST`, domyślnie `false`.
  - Obsługuje `EntityBreedEvent` dla klasycznego rozmnażania kończącego się utworzeniem potomka.
  - Dodatkowo obsługuje Paper `EntityFertilizeEggEvent`, ponieważ żaby, snifferry i żółwie używają mechanizmu zapłodnienia, w którym potomek lub jajo powstaje później.

- ➖ **Dawna wspólna flaga `animal-interact`.**
  - Nie jest już flagą konfiguracyjną prezentowaną użytkownikowi.
  - Pozostaje tymczasowo jako ukryty klucz zgodności wstecznej.
  - Przy starcie pluginu istniejąca zapisana wartość `animal-interact` jest kopiowana do `animal-leash`, `animal-ride` i `animal-breed`, jeżeli konkretna nowa flaga nie ma już własnej wartości.
  - Po migracji legacy `animal-interact` jest ustawiane na wartość neutralizującą stare handlery, aby nie nakładały ponownie wspólnej blokady.
  - Analogicznie migrowane są zapisane granty ról `flag.animal-interact`.

- ✅ **Flaga `passives` pozostaje osobna.**
  - Jej zadaniem jest ochrona zwierząt przed zadawaniem obrażeń.
  - Nie należy jej łączyć z `animal-leash`, `animal-ride` ani `animal-breed`, ponieważ opisuje inną kategorię działania.

### Zgodność API dla rozdzielenia flag zwierząt

Rozdzielenie zostało sprawdzone względem API Paper używanego przez nowsze wersje Minecraft/Paper:

| Wersja | Smycz | Jazda | Rozmnażanie | Zapłodnienie jaj |
| --- | --- | --- | --- | --- |
| `26.1.2` | osobne anulowalne zdarzenie | osobne anulowalne zdarzenie | `EntityBreedEvent` | `EntityFertilizeEggEvent` |
| `26.2` | osobne anulowalne zdarzenie | osobne anulowalne zdarzenie | `EntityBreedEvent` | `EntityFertilizeEggEvent` |
| `26.3` | API nadal zawiera osobne zdarzenia | API nadal zawiera osobne zdarzenia | API nadal zawiera zdarzenie | API nadal zawiera zdarzenie |

Wniosek: technicznie nie ma potrzeby utrzymywania jednej wspólnej flagi. Paper rozdziela te mechaniki wystarczająco precyzyjnie, aby PlotsX również mógł udostępniać trzy niezależne ustawienia.

### Zabezpieczenie przed kradzieżą przedmiotów

- ✅ **Flaga `item-pickup` — blokująca podnoszenie przedmiotów z ziemi.**
  - Jest pełnoprawną flagą.
  - Obsługiwana przez `EntityPickupItemEvent`.
  - Dotyczy graczy podnoszących przedmioty znajdujące się na terenie działki.

- ✅ **Flaga `item-drop` — blokująca wyrzucanie przedmiotów na działce.**
  - Jest pełnoprawną flagą.
  - Obsługiwana przez `PlayerDropItemEvent`.

### Zabezpieczenie przed niszczeniem roślin

- ✅ **Flaga `crop-trample` — blokująca niszczenie upraw przez skakanie.**
  - Zaimplementowana.
  - Obsługiwana przez `PlayerInteractEvent`.
  - Sprawdzane jest `Action.PHYSICAL` na `Material.FARMLAND`.
  - Spełnia wymaganie oznaczone wcześniej jako konieczne jeszcze dla Alpha-01.

- ➖ **Flaga `sapling-trample` — blokująca niszczenie sadzonek.**
  - Osobna flaga nie została dodana.
  - Ten przypadek pozostaje pod ochroną budowania/niszczenia bloków, czyli `build`.

### Zabezpieczenie przed używaniem przedmiotów

- ➖ **Flaga `item-use` — blokująca używanie przedmiotów.**
  - Nie istnieje jedna ogólna flaga `item-use`.
  - Zakres został rozdzielony na bardziej precyzyjne zabezpieczenia, m.in.:
    - `projectiles`,
    - `use-potions`,
    - `special-weapons`,
    - `fishing`,
    - `elytra`.

- ✅ **Flaga `fishing` — blokująca łowienie ryb.**
  - Zaimplementowana.
  - Obsługiwana przez `PlayerFishEvent`.
  - Sprawdzane są lokalizacje gracza, haczyka oraz złowionego obiektu.

### Zabezpieczenie przed interakcją z blokami

- ✅ **Flaga `bed-use` — blokująca używanie łóżek.**
  - Zaimplementowana.
  - Obejmuje wszystkie materiały, których nazwa kończy się na `_BED`.

- ✅ **Flaga `crafting` — osobna od `utility`.**
  - Obejmuje `CRAFTING_TABLE` oraz `CRAFTER`.
  - Automatycznie pojawia się w GUI flag.

- ✅ **Flaga `enchanting` — osobna od `utility`.**
  - Obejmuje `ENCHANTING_TABLE`.
  - Automatycznie pojawia się w GUI flag.

### Zabezpieczenie przed efektami pogodowymi

- 🟡 **Flaga `weather-damage` — blokująca obrażenia od piorunów.**
  - Funkcjonalność istnieje pod wspólną flagą `weather`.
  - Obsługiwane są `LightningStrikeEvent` oraz `EntityDamageEvent` z `DamageCause.LIGHTNING`.

- ❌ **Flaga `weather-effects` — blokująca efekty pogodowe (deszcz, burza).**
  - Brak implementacji.
  - Obecne `weather` chroni przed piorunami, ale nie tworzy lokalnej pogody dla gracza na działce.

### Zabezpieczenie przed używaniem komend

- ✅ **Flaga `command-use` — blokująca używanie komend na działce.**
  - Zaimplementowana przez `PlayerCommandPreprocessEvent`.
  - `/home` i `/sethome` mają osobną kontrolę przez `allow-home`.
  - Bezpieczne wyjątki dla komend PlotsX: `/plotsx`, `/ptx`, `/plot`, `/claim`, `/unclaim`.

### Zabezpieczenie przed używaniem przedmiotów specjalnych

- ✅ **Flaga `elytra-use`.**
  - Funkcjonalność istnieje pod nazwą `elytra`.
  - Blokowane jest rozpoczęcie szybowania przez `EntityToggleGlideEvent`.

- 🟡 **Flaga `trident-use`.**
  - Funkcjonalność jest połączona z `special-weapons`.
  - Obsługiwane jest rzucanie trójzębem i zadawanie nim obrażeń.
  - Do uzupełnienia pozostaje Riptide (`PlayerRiptideEvent`).

- ✅ **Flaga `mace-use`.**
  - Funkcjonalność istnieje pod wspólną flagą `special-weapons`.
  - Blokowane są obrażenia zadawane przez `MACE`.

### Zabezpieczenie przed używaniem bloków specjalnych

- 🟡 **Flaga `conduit-use`.**
  - Funkcjonalność istnieje jako `conduit-effects`.
  - Blokowane jest nakładanie efektu przewodnika przez `EntityPotionEffectEvent.Cause.CONDUIT`.

- ✅ **Flaga `respawn-anchor-use`.**
  - Funkcjonalność istnieje pod nazwą `respawn-anchor`.
  - Obsługiwana podczas interakcji z `Material.RESPAWN_ANCHOR`.

### Zabezpieczenie przed używaniem przedmiotów dekoracyjnych

- 🟡 **Flaga `frame-use`.**
  - Funkcjonalność jest częściowo objęta wspólną flagą `decorations`.
  - Chronione jest umieszczanie i niszczenie obiektów wiszących.
  - Do uzupełnienia pozostają interakcje z istniejącym `ItemFrame` / `GlowItemFrame`, np. obracanie lub podmiana przedmiotu.

- ✅ **Flaga `armor-stand-use`.**
  - Funkcjonalność istnieje pod wspólną flagą `decorations`.
  - Obsługiwane jest `PlayerArmorStandManipulateEvent` oraz niszczenie ArmorStanda.

---

# GUI flag

`FlagsGUI` korzysta z listy `PlotFlagRegistry.visibleFlags`.

Każda normalna, widoczna flaga jest automatycznie umieszczana w panelu, natomiast klucze legacy mogą pozostać w rejestrze bez pokazywania ich użytkownikowi.

Po rozdzieleniu zwierząt GUI pokazuje osobno:

- `animal-leash`,
- `animal-ride`,
- `animal-breed`.

`animal-interact` jest ukryte i służy wyłącznie do zgodności/migracji istniejących danych.

---

# Najważniejsze braki do domknięcia Alpha-02

## 1. `weather-effects`

Status: ❌ brak.

Do zaprojektowania pozostaje sposób blokowania lub ukrywania deszczu/burzy dla gracza znajdującego się na działce. Standardowa pogoda świata nie jest ograniczona do regionu działki, więc potrzebne będzie rozwiązanie per-player albo równoważny mechanizm.

## 2. Pełna ochrona ItemFrame / GlowItemFrame

Status: 🟡 częściowo.

`decorations` chroni obecnie umieszczanie i niszczenie dekoracji, ale trzeba objąć również:

- wkładanie przedmiotu do ramki,
- wyjmowanie przedmiotu,
- podmianę przedmiotu,
- obracanie przedmiotu,
- analogiczne operacje dla `GlowItemFrame`.

## 3. Trident + Riptide

Status: 🟡 do uzupełnienia.

`special-weapons` blokuje rzucanie trójzębem oraz obrażenia, ale należy dodać kontrolę `PlayerRiptideEvent`.

---

# Podsumowanie

Alpha-02 jest w zdecydowanej większości zaimplementowana.

Po analizie nowszego Paper API wcześniejsze scalenie `animal-leash`, `animal-ride` i `animal-breed` pod `animal-interact` zostało wycofane. API rozdziela te mechaniki na niezależne anulowalne zdarzenia, dlatego PlotsX również używa obecnie trzech niezależnych flag.

| Pierwotny pomysł | Aktualna flaga |
| --- | --- |
| `animal-leash` | `animal-leash` |
| `animal-ride` | `animal-ride` |
| `animal-breed` | `animal-breed` |
| ochrona zwierząt przed obrażeniami | `passives` |
| `elytra-use` | `elytra` |
| `trident-use` | `special-weapons` |
| `mace-use` | `special-weapons` |
| `weather-damage` | `weather` |
| `conduit-use` | `conduit-effects` |
| `respawn-anchor-use` | `respawn-anchor` |
| `frame-use` | `decorations` |
| `armor-stand-use` | `decorations` |

Do pełnego zamknięcia tej checklisty pozostają przede wszystkim:

1. `weather-effects`,
2. pełna ochrona interakcji z `ItemFrame` / `GlowItemFrame`,
3. obsługa Riptide dla `special-weapons`.
