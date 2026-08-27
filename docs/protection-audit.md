# Audyt ochrony działek – PlotsX

## Aktualizacja implementacji

Po wykonaniu audytu dodano następujące zabezpieczenia:

- `explosions` — filtruje listę bloków niszczonych przez eksplozje bloków i encji oraz chroni byty na działce przed obrażeniami wybuchowymi;
- `decorations` — chroni obrazy, ramki i inne hanging entities, a także stawianie, niszczenie i wyposażenie armor standów;
- `item-transfer` — blokuje transfer hopperami przez granicę działki, pozostawiając transfer wewnątrz tej samej działki;
- `portal-create` — kontroluje tworzenie portali na działce;
- `portal-use` — kontroluje przechodzenie graczy przez portal z działki lub na działkę;
- `projectiles` — blokuje pociski obcych graczy trafiające w blok lub byt na działce;
- `item-pickup` i `item-drop` — kontrolują podnoszenie i wyrzucanie przedmiotów;
- `crop-trample` — blokuje deptanie pól przez obcych graczy;
- `animal-interact` — kontroluje zwykłe interakcje, smycze, rozmnażanie i dosiadanie zwierząt.
- `command-use` — kontroluje ogólne komendy z bezpiecznymi wyjątkami dla komend PlotsX; `/home` i `/sethome` nadal podlegają `allow-home`;
- `fishing` — kontroluje łowienie wewnątrz działki oraz zarzucanie wędki przez jej granicę;
- `elytra`, `flight` — kontrolują rozpoczęcie lotu elytrą i zwykłego latania;
- `special-weapons` — kontroluje używanie trójzębów i buzdyganów przeciw celom na działce;
- `weather` — chroni działkę przed uderzeniami piorunów i obrażeniami od nich;
- `bed-use`, `crafting`, `enchanting`, `respawn-anchor` — rozdzielają dostęp do specjalnych bloków użytkowych;
- `conduit-effects` — kontroluje efekty nakładane przez conduit.

Właściciel, członkowie oraz gracze z bypassem nadal omijają ograniczenia skierowane do graczy. Zdarzenia środowiskowe, takie jak eksplozje i tworzenie portali, są sterowane bezpośrednio wartością flagi. Nowe flagi działają również na starszych działkach dzięki wartościom domyślnym rejestru, nawet jeśli nie mają jeszcze osobnego rekordu w tabeli `plot_flags`.

Najważniejsze pozycje pierwotnego audytu zostały zaimplementowane. Dalsze prace powinny koncentrować się na testach integracyjnych każdego eventu, zachowaniu przy współpracy z innymi pluginami oraz paginacji GUI przed dodaniem kolejnych flag. Rejestr zawiera obecnie 49 aktywnych flag, czyli maksymalną liczbę mieszczącą się w obecnym układzie GUI z przyciskiem powrotu w slocie 49.

## Zakres ochrony i flagi
- **Rejestr flag** – plugin udostępnia 29 aktywnych flag (whitelist/blacklist) kontrolujących budowanie, PVP, pojemniki, redstone, spawn mobów, płyny, ogień, teleporty, wzrost roślin itd., z domyślnymi wartościami zapisanymi w `PlotFlagRegistry`.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L40】
- **Wymuszanie flag per‑zdarzenie** – logika ochrony obejmuje m.in. wejście/wyjście z działki, teleporty z perłą/chorusem, stawianie/niszczene bloków (w tym spawnerów), tłoki, dyspensery, przepływ cieczy, griefery fluidów, użycie pojemników (w tym ender‑chest), redstone, utility, drzwi/przyciski/dźwignie, mikstury (wypicie/splash/lingering), komendy /home,/sethome, spawn mobów, interakcje ze zwierzętami i pojazdami, rozrost/bloki naturalne (śnieg/lód/liście/uprawy), oraz aplikację efektów.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L94-L413】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L450-L1004】

## Mocne strony
- **Gęste pokrycie interakcji gracza** – kluczowe akcje gracza na blokach i bytach są chronione przez flagi `build`, `chest`, `utility`, `redstone`, `door/smart-door`, `teleport`, `allow-home`, `use-potions` itd., dzięki czemu właściciel może precyzyjnie otwierać/zamykać dostęp.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L264-L537】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L828-L1004】
- **Ochrona przepływów i transformacji bloków** – płyny, piasek/gruz, przemiany śniegu/lodu, rozrost bloków i wzrost roślin są brane pod uwagę, co ogranicza griefing środowiskowy bez udziału gracza.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L355-L413】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L915-L969】
- **Kontrola spawnów i passive griefingu** – flagi `spawn-monsters`, `spawn-animals` i `passives` blokują spawn oraz interakcje/obrażenia wobec pasywnych mobów, a `minecart` zabezpiecza kontenery‑pojazdy oraz wejście do nich.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L450-L808】

## Luki i ryzyka
- **Brak tarczy na eksplozje** – kod nie nasłuchuje TNT/creeper/bed/nether/ender‑crystal ani minecart TNT, więc działki są podatne na zniszczenia wybuchowe mimo innych blokad.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L1-L1004】
- **Brak ochrony dla obiektów wiszących i dekoracji** – nie ma obsługi item frame’ów, obrazów, zbroi, świec, świeczników itp.; gracze z uprawnieniem budowania mogą zabezpieczyć, ale obcy bez flagi „build” nadal mogą je niszczyć rzutami/pociskami, bo zdarzenia nie są przechwytywane.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L264-L413】
- **Brak anty‑portal griefingu** – brak sprawdzeń tworzenia portali Nether/End i przenoszenia przez nie, co pozwala obcym zakładać portale w cudzych działkach lub wciągać byty do środka bez sprawdzenia flag.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L1-L1004】
- **Ochrona przed projektilami ograniczona do mikstur** – brakuje blokady strzał/tridentów/śnieżek/pearli wchodzących w działkę (poza teleportem), co zostawia furtkę na uderzanie bytów i aktywowanie przycisków z zewnątrz.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L475-L808】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L828-L1004】
- **Brak kontroli nad przenoszeniem przedmiotów** – plugin nie filtruje hopperów, wciągania przez lejki z/na działkę ani wyrzutników/lejów z itemami (poza cieczami), więc kradzież przedmiotów kanałami technicznymi pozostaje możliwa.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L416-L448】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L475-L537】

## Weryfikacja względem listy projektowej (Alpha‑02)
- **Zwierzęta (leash/ride/breed)** – rejestr nie ma flag `animal-leash`, `animal-ride`, `animal-breed`; jedyna flaga `passives` kontroluje tylko spawn i zadawanie obrażeń, więc uprowadzanie lub rozmnażanie zwierząt pozostaje bez ochrony.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L18-L20】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L455-L472】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L650-L682】
- **Kradzież przedmiotów (pickup/drop)** – brak flag `item-pickup` i `item-drop` w rejestrze; zdarzenia podnoszenia/ wyrzucania itemów nie są obsłużone w listenerze.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L40】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L1-L1004】
- **Niszczenie roślin (crop-trample)** – jedyna flaga roślinna to `cant-grow` (blokuje wzrost), brak blokady na deptanie upraw; listener nie reaguje na niszczenie upraw przez graczy czy byty.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L28-L34】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L1-L1004】
- **Używanie przedmiotów (item-use/fishing)** – brak globalnej flagi `item-use` oraz blokady łowienia; jedyna kontrola używania przedmiotów dotyczy mikstur przez `use-potions`.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L23-L27】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L828-L876】
- **Interakcja z blokami (bed-use/crafting/enchanting)** – rejestr i listener nie definiują osobnych flag dla łóżek, stołów rzemieślniczych ani zaklinania; obsługiwane są tylko pojemniki, utility, drzwi, redstone i teleporty.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L34】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L475-L537】
- **Efekty pogodowe (weather-damage/weather-effects)** – brak jakichkolwiek flag lub handlerów związanych z obrażeniami od piorunów czy skutkami deszczu/burzy.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L40】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L1-L1004】
- **Komendy (command-use)** – listener blokuje wyłącznie `/home` i `/sethome`; brak flagi ani logiki dla ogólnego zakazu komend na działce.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L810-L826】
- **Przedmioty specjalne (elytra/trident/mace)** – rejestr nie przewiduje flag na elytry, trójzęby ani buzdygany; brak eventów kontrolujących ich użycie.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L40】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L1-L1004】
- **Bloki specjalne (conduit/respawn-anchor)** – brak flag `conduit-use` i `respawn-anchor-use`; listener nie sprawdza interakcji z tymi blokami.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L34】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L475-L520】
- **Dekoracje (frame-use/armor-stand-use)** – brak flag oraz handlerów dla ramek na przedmioty i stojaków na zbroje, co potwierdza wcześniej zidentyfikowaną lukę dekoracyjną.【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotFlagRegistry.kt†L5-L40】【F:src/main/kotlin/pl/syntaxdevteam/plotsx/protection/PlotProtectionListener.kt†L264-L413】

## Rekomendacje (blokady/flag do dodania)
1. **Flagi eksplozji** – dodać `tnt-explosion`, `creeper-explosion`, `bed-explosion`, `ender-crystal` i `minecart-tnt` z domyślnym zakazem oraz handlerami `EntityExplodeEvent`/`ExplosionPrimeEvent`/`HangingBreakEvent`.
2. **Hanging entities** – zabezpieczyć item frame, mapy, obrazy, armor‑standy, świeczniki poprzez flagę `decorations` i obsługę `HangingPlaceEvent`, `HangingBreakByEntityEvent`, `PlayerArmorStandManipulateEvent`.
3. **Portale** – dodać blokadę `portal-create` i kontrolę teleportów `PlayerPortalEvent`/`EntityPortalEnterEvent` na bazie flag WHITELIST.
4. **Pociski** – rozszerzyć `EntityDamageByEntityEvent`/`ProjectileHitEvent` o blokady dla strzał/tridentów/śnieżek wystrzelonych z zewnątrz na działkę (np. flaga `projectiles`).
5. **Transfer przedmiotów** – dodać obsługę `InventoryMoveItemEvent`/`BlockFromToEvent` dla hopperów i dropperów przenoszących itemy przez granicę działki, z flagą `item-transfer` albo wykorzystując istniejącą `chest`/`build`.

## Konkluzja
Plugin ma obecnie znacznie pełniejszą siatkę ochron dla interakcji gracza i środowiska. Najważniejsze klasyczne wektory griefingu zidentyfikowane w pierwotnym audycie — wybuchy, portale, kanały itemów, pociski i dekoracje — otrzymały dedykowane flagi i handlery. Przed stabilnym wydaniem nadal potrzebne są testy integracyjne na rzeczywistym serwerze oraz uzupełnienie mniej krytycznych przypadków wymienionych w aktualizacji audytu.
