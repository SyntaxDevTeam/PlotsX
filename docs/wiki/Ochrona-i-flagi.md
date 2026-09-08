# Ochrona i flagi

## Supported grants — granty ról działki

Granty wpisuje się do `plot-roles.<rola>.permissions`, dla ról `member`, `builder`
i `manager`. To role działki, niezależne od rang serwera, np. VIP.
Właściciel może nadpisywać ich granty dla konkretnej działki w `/plot members`.

| Grant | Znaczenie |
| --- | --- |
| `invite` | Dodawanie członków. |
| `kick` | Usuwanie członków z uwzględnieniem hierarchii ról. |
| `rename` | Zmiana nazwy działki. |
| `flag.<identyfikator>` | Zmiana wskazanej flagi, np. `flag.build`. |

Każda flaga w tabelach poniżej ma grant `flag.<identyfikator>`. Dotyczy to także
dodatkowych flag rejestrowanych przez integracje API. Nie ma grantu `flag.*`.
Grant pozwala zmieniać flagę, ale sam nie zmienia jej wartości.
Nadawanie ról i przekazywanie własności pozostaje dostępne właścicielowi lub administratorowi.
Przykład: `permissions: [invite, rename, flag.build, flag.pvp]`.
Domyślnie tylko `manager` ma granty: `invite`, `kick`, `rename`.

Flaga integracyjna `grave-create` (domyślnie TAK) pozwala na tworzenie grobów
przez integracje korzystające z tej flagi; członkostwo nie omija tej zasady.

[← Home](Home.md)

Flagi to przełączniki zasad na twojej działce. Otwórz `/plot`, wybierz flagi i kliknij ustawienie, które chcesz zmienić.

## Jak czytać przełączniki?

Wartość **TAK** nie zawsze oznacza pozwolenie. Niektóre przełączniki włączają czynność, a inne jej blokadę. Na przykład `build = TAK` pozwala gościom budować, ale `chest = TAK` blokuje im zwykły dostęp do skrzyń.

Tabele niżej pokazują dokładnie, co oznacza **TAK**. Wartość **NIE** daje przeciwny efekt. Identyfikator pomaga rozpoznać ustawienie w komunikatach.

Zasady dostępu graczy dotyczą głównie gości. Właściciel i członkowie mają szerszy dostęp. Zasady otoczenia, takie jak ogień, wzrost czy eksplozje, działają na terenie działki. Prywatne pojemniki mają dodatkową ochronę niezależną od zwykłego dostępu.

## Budowanie i korzystanie z bazy

| Ustawienie | Co oznacza TAK? | Domyślnie |
| --- | --- | --- |
| `build` | Goście mogą stawiać i niszczyć bloki. | NIE |
| `chest` | Blokada zwykłego dostępu gości do pojemników. | TAK |
| `ender-chest` | Blokada używania ender chestów przez gości. | TAK |
| `lever` | Blokada dźwigni dla gości. | TAK |
| `button` | Blokada przycisków dla gości. | TAK |
| `door` | Blokada zwykłego otwierania drzwi i furtek przez gości. | TAK |
| `smart-door` | Goście mogą korzystać ze wspólnego otwierania sąsiednich drzwi tego samego rodzaju. | NIE |
| `utility` | Goście mogą używać bloków użytkowych, np. pieców. | NIE |
| `redstone` | Goście mogą zmieniać obsługiwane elementy redstone, np. przekaźniki. | NIE |
| `decorations` | Zezwolenie na działania związane z dekoracjami, np. stojakami i ramkami. | NIE |
| `bed-use` | Goście mogą korzystać z łóżek. | NIE |
| `crafting` | Goście mogą korzystać ze stołów rzemieślniczych i crafterów. | NIE |
| `enchanting` | Goście mogą używać stołów do zaklinania. | NIE |
| `respawn-anchor` | Goście mogą korzystać z kotwic odrodzenia. | NIE |
| `item-transfer` | Blokada automatycznego przenoszenia przedmiotów między pojemnikami. | TAK |

Inteligentne drzwi mają własny dostęp. Jeśli chcesz zamknąć wejście przed gośćmi, pozostaw `door = TAK` i `smart-door = NIE`.

## Walka, przedmioty i zwierzęta

| Ustawienie | Co oznacza TAK? | Domyślnie |
| --- | --- | --- |
| `pvp` | Zezwolenie na walkę graczy przy sprawdzaniu dostępu. | NIE |
| `passives` | Goście mogą zadawać obrażenia zwierzętom. | NIE |
| `use-potions` | Goście mogą używać mikstur. | NIE |
| `special-weapons` | Goście mogą korzystać z obsługiwanych specjalnych broni, np. trójzębu. | NIE |
| `projectiles` | Goście mogą wystrzeliwać pociski. | NIE |
| `animal-interact` | Goście mogą wykonywać obsługiwane interakcje ze zwierzętami, np. karmić je i używać smyczy. | NIE |
| `fishing` | Goście mogą łowić. | NIE |
| `item-pickup` | Goście mogą podnosić przedmioty. | NIE |
| `item-drop` | Goście mogą wyrzucać przedmioty. | NIE |
| `crop-trample` | Goście mogą deptać grządki. | NIE |

Dodanie osoby do działki daje jej szerszy dostęp również przy sprawdzaniu wielu z tych zasad. Flaga PvP nie jest gwarancją wyłączenia walki między wszystkimi członkami ekipy.

## Poruszanie się i komendy

| Ustawienie | Co oznacza TAK? | Domyślnie |
| --- | --- | --- |
| `minecart` | Blokada obsługiwanych interakcji gości z pojazdami. | TAK |
| `teleport` | Blokada teleportowania gości na działkę perłą Endu lub owocem refrenusu. | TAK |
| `portal-create` | Blokada tworzenia portali. | TAK |
| `portal-use` | Goście mogą korzystać z portali. | NIE |
| `allow-home` | Goście mogą używać komend `/home` i `/sethome`. | NIE |
| `command-use` | Goście mogą używać pozostałych komend. | TAK |
| `elytra` | Goście mogą rozpoczynać lot elytrą. | NIE |
| `flight` | Goście mogą włączać lot, jeśli już mają taką możliwość. | NIE |

PlotsX nie dodaje `/home`, `/sethome` ani umiejętności latania. Te flagi regulują istniejące możliwości.

Podstawowe komendy `/plotsx`, `/ptx`, `/plot`, `/claim` i `/unclaim` są wyjątkami od blokady komend. Własne skróty i komendy prywatnych skrzyń nie należą do tej listy. Komendy `/home` i `/sethome` mają osobną kontrolę.

## Otoczenie i naturalne zmiany

| Ustawienie | Co oznacza TAK? | Domyślnie |
| --- | --- | --- |
| `spawn-monsters` | Zezwolenie na pojawianie się potworów. | NIE |
| `spawn-animals` | Zezwolenie na pojawianie się zwierząt. | TAK |
| `allow-spawners` | Zezwolenie na stawianie i niszczenie spawnerów przy sprawdzaniu dostępu gracza. | NIE |
| `flow` | Blokada przepływu płynów. | TAK |
| `flow-damage` | Zezwolenie na niszczenie podatnych bloków przez płyny. | NIE |
| `fire` | Blokada obsługiwanych zdarzeń zapłonu, rozchodzenia się ognia i spalania. | TAK |
| `iceform-player` | Goście mogą zamrażać wodę efektem Mroźnego Piechura. | NIE |
| `iceform-world` | Zezwolenie na naturalne tworzenie i topnienie lodu lub śniegu. | NIE |
| `cant-grow` | Blokada wzrostu roślin. | TAK |
| `leaves-decay` | Blokada naturalnego znikania liści. | TAK |
| `block-transform` | Blokada obsługiwanych przemian bloków, np. rozprzestrzeniania się niektórych bloków. | TAK |
| `fall` | Blokada zmian związanych ze spadającymi blokami. | TAK |
| `explosions` | Blokada niszczenia terenu przez eksplozje i obsługiwanych eksplozji na działce. | TAK |
| `effects` | Blokada obsługiwanych efektów nakładanych na graczy. | TAK |
| `conduit-effects` | Blokada efektów przewodni. | TAK |
| `weather` | Blokada uderzeń i zapłonów piorunów na działce. | TAK |

Flaga pogody nie tworzy osobnej pogody nad działką. Prywatne pojemniki pozostają chronione przed zniszczeniem przez eksplozje także po zezwoleniu na eksplozje.

Niektóre działania podlegają kilku zasadom jednocześnie. Naturalne tworzenie lodu może wymagać zarówno `iceform-world = TAK`, jak i `block-transform = NIE`. Zezwolenie na mikstury nie wyłącza osobnej blokady efektów.

## Przygotuj działkę pod swój pomysł

**Farma:** zacznij od `cant-grow = NIE`. Jeśli potrzebujesz płynącej wody, ustaw `flow = NIE`. Dla transportu lejkami potrzebne jest `item-transfer = NIE` i brak prywatnej blokady pojemników.

**Dom otwarty dla gości:** możesz odblokować drzwi i wybrane stanowiska pracy, pozostawiając budowanie zamknięte.

**Wspólna baza:** dodaj ekipę jako członków, zamiast pozwalać wszystkim gościom na budowanie.

Po zmianach sprawdź efekt z osobą, która nie jest członkiem ani administratorem. Inne pluginy serwera mogą nakładać dodatkowe ograniczenia.

Dalej: [prywatne skrzynie](Prywatne-skrzynie.md).
