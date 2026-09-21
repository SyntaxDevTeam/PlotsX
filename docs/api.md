# PlotsX API v2

Kontrakt znajduje się w `pl.syntaxdevteam.plotsx.api`. Pobieraj go przez Bukkit
`ServicesManager`; nie odwołuj się do `DatabaseHandler`, `CacheManager` ani
`PermissionChecker` z kodu integracji. Implementacja pozostaje wewnętrzna.

## Zależność i pobranie serwisu

`./gradlew apiJar` tworzy `build/libs/PlotsX-<wersja>-api.jar`.
W projekcie integracji dodaj ten plik jako `compileOnly`, bez dołączania go do
wynikowego JAR-a. API wymaga Paper API i Java 21; Kotlin runtime zapewnia PlotsX.
Artefakt nie jest jeszcze publikowany do repozytorium Maven.

```kotlin
dependencies {
    compileOnly(files("libs/PlotsX-1.0.0-Beta-1-api.jar"))
}
```

Paper plugin korzystający z API deklaruje w swoim `paper-plugin.yml`:

```yaml
dependencies:
  server:
    PlotsX:
      load: BEFORE
      required: false
      join-classpath: true
```

W klasycznym `plugin.yml` użyj `softdepend: [PlotsX]`. Jeśli API jest niezbędne,
ustaw `required: true` / `depend: [PlotsX]`.
Opcjonalną integrację umieść w osobnej klasie ładowanej dopiero po sprawdzeniu,
że PlotsX jest włączony — sam nullable wynik `load()` nie chroni przed brakiem klas API.

Kotlin, wewnątrz klasy integracji:

```kotlin
val api = server.servicesManager.load(PlotsXApi::class.java) ?: return
check(api.apiVersion == 2)
val plot = api.getPlotAt(player.world.name, player.location.blockX, player.location.blockZ)
val owned = api.getOwnedPlots(player.uniqueId)
val accessible = api.getAccessiblePlots(player.uniqueId)

// Exact containment for a building/arena footprint. Unlike corner checks this also rejects holes.
val fits = plot?.containsArea(player.world.name, minX, minZ, maxX, maxZ) == true
val roundArenaFits = plot?.containsCircle(player.world.name, centerX, centerZ, diameter) == true
```

Java:

```java
PlotsXApi api = getServer().getServicesManager().load(PlotsXApi.class);
if (api == null || api.getApiVersion() != 2) return;
PlotSnapshot plot = api.getPlotAt(player.getWorld().getName(),
    player.getLocation().getBlockX(), player.getLocation().getBlockZ());
if (plot != null) {
    List<MemberSnapshot> members = api.getMembers(plot.getId());
}
```

## Zakres API

| Obszar | Metody |
| --- | --- |
| Działki | `getPlot`, `getPlotAt`, `getPlots`, `getOwnedPlots`, `getAccessiblePlots` |
| Członkowie | `getMembers`, `isMember`, `addMember`, `removeMember` |
| Rangi | `getRoles`, `getRolePermissions`, `setMemberRole`, `setRolePermission` |
| Własność | `transferOwnership` |
| Flagi | `getFlags`, `getFlagValue`, `evaluateFlag`, `setFlag` |
| Integracje | `registerFlag`, `unregisterFlag`, `PlotFlagChangedEvent` |
| Uprawnienia zarządzania | `canManage` |

Snapshoty i zwracane listy nie pozwalają modyfikować stanu pluginu. `isMember`
sprawdza wyłącznie dodanych członków; właściciel jest osobnym polem snapshotu.
`getAccessiblePlots` oznacza własne działki i członkostwa, bez administracyjnego bypassu.
Tworzenie/usuwanie działek i rozszerzanie nie są częścią API v2 — wymagają osobnych
kontraktów obejmujących ekonomię, kolizje i ochronę innych pluginów.

API v2 rozróżnia geometrię przez `PlotSnapshot.geometryType`. Dla działki klasycznej
`radius` jest liczbą, `extensions` zawiera dokupione segmenty, a `chunks` jest puste.
Dla działki chunkowej `radius == null`, `chunks` zawiera kompletne współrzędne chunków,
`area` wynosi `liczba_chunków × 256`, a `contains()` używa dokładnej geometrii.
`geometryRevision` zmienia się przy modyfikacji kształtu i pozwala wykryć nieaktualny
snapshot. Zmiana typu getterów względem API v1 jest binarnie niezgodna; integracje
należy przebudować z artefaktem v2.

Metody zapisujące wymagają `CommandSender` inicjującego operację, sprawdzają aktualne
uprawnienia właściciela/rangi/administratora i zwracają enum wyniku. Nie wysyłają
wiadomości ani nie otwierają GUI. Nie przekazuj konsoli zamiast gracza, jeśli
operacja pochodzi od gracza: konsola ma uprawnienia administracyjne.

Przykłady:

```kotlin
api.setFlag(player, plotId, "grave-create", false)
api.addMember(player, plotId, targetUuid) // zawsze ranga member
api.setMemberRole(player, plotId, targetUuid, "manager")
api.setRolePermission(player, plotId, "manager", "flag.grave-create", true)
api.transferOwnership(player, plotId, targetUuid) // wywołaj po własnym potwierdzeniu
```

Przekazanie własności ma te same limity co GUI, wymaga członkostwa i obecności
odbiorcy online. Sprawdzaj wynik każdej operacji; wywołanie nie gwarantuje sukcesu.

## Semantyka flag

`getFlagValue` zwraca zapisaną wartość albo domyślną z definicji; dla nieznanej
działki/flagi zwraca `null`. To surowa wartość — starsze flagi typu blacklist
interpretują `true` jako blokadę. `evaluateFlag` uwzględnia ten kierunek i zwraca:

- `ALLOW`: dozwolone, również poza działkami dla znanej flagi;
- `DENY`: zabronione;
- `UNKNOWN_FLAG`: brak definicji, co integracja powinna potraktować jako odmowę
  lub jawnie obsłużyć jako niezgodną wersję API.

Parametr `subject` to UUID gracza lub `null` dla reguły środowiskowej/odwiedzającego.
Nie jest sprawdzany administracyjny bypass uprawnień serwera. Jeśli definicja ma
`memberBypass=true`, członkowie i właściciel omijają regułę; dla `grave-create`
jest to `false`. Wyłączenie grobów dotyczy zatem każdego, także właściciela i OP.
Zmiana samej flagi nadal wymaga uprawnień zarządzania.

## Własne flagi pluginów

```kotlin
val registered = api.registerFlag(this, FlagDefinition(
    "myplugin:machines", false, true, false,
    Material.REDSTONE_BLOCK, "Maszyny", "Pozwala stawiać maszyny na działce."
))
```

Namespace musi odpowiadać nazwie pluginu zapisanej małymi literami. Klucz zawiera
litery `a-z`, cyfry, `_`, `-` i jeden `:`; maksymalna długość to 120 znaków.
Duplikat zwraca `false`, niepoprawny klucz zgłasza `IllegalArgumentException`.
Flagi wbudowane i flagi innych providerów nie mogą zostać zastąpione.

Flaga automatycznie pojawia się w stronicowanym GUI i uprawnieniach rang jako
`flag.myplugin:machines`. Opis i nazwa własnej flagi są zwykłym tekstem.
Dla wbudowanych flag `getFlags()` udostępnia klucze tłumaczeń w tych polach.
Definicja znika po wyłączeniu providera; zapisane wartości i nadane uprawnienia
pozostają w bazie, aby przetrwać restart. Provider rejestruje flagę ponownie w `onEnable`.
Sam wpis flagi nie chroni zdarzeń: plugin integrujący wywołuje `evaluateFlag`.

## Zdarzenie i cykl życia

`PlotFlagChangedEvent` jest nieanulowalnym powiadomieniem po zapisie i odświeżeniu
cache, emitowanym zarówno przy zmianie z GUI, jak i przez API. Zawiera snapshot
działki, klucz flagi, poprzednią i nową wartość oraz UUID inicjatora (`null` dla konsoli).
Zapis tej samej wartości zwraca `UNCHANGED` i nie emituje zdarzenia.
Zdarzenie nie obejmuje importu bazy ani zmian uprawnień rang.

Odczyty snapshotów, list członków, definicji i wartości flag oraz `evaluateFlag`
działają z cache i mogą być wywoływane asynchronicznie. Są to snapshoty, nie
transakcja łącząca kilka wywołań; równoczesna zmiana może być widoczna przy kolejnym
odczycie. `getRolePermissions`, `canManage`, wszystkie zapisy i rejestracja flag
wymagają głównego wątku Paper. Nie wywołuj ich bezpośrednio na wątkach regionów Folia.

Po wyłączeniu PlotsX serwis jest wyrejestrowany, a odwołania do starej implementacji
zgłaszają `IllegalStateException`. Integracja opcjonalna powinna obsłużyć
`PluginDisableEvent`/`ServiceUnregisterEvent` i ponownie pobrać serwis po włączeniu.

## GraveDiggerX

PlotsX udostępnia flagę `grave-create`, domyślnie `true`, z opisem w PL/EN.
Nie włącza to automatycznie współpracy z niezmodyfikowanym GraveDiggerX.

W przeanalizowanym kodzie GraveDiggerX `GraveManager.createGraveAndGetIt` przekazuje
do `SafeGravePlacer.findSafeLocationNear` predykat `isAllowedLocation`, a następnie
ponownie sprawdza ostateczną lokalizację przed postawieniem głowy gracza.
W obu miejscach należy połączyć obecną kontrolę WorldGuard z kontrolą PlotsX:

```kotlin
fun plotsAllowGrave(target: Location): Boolean {
    val world = target.world ?: return false
    return api.evaluateFlag(world.name, target.blockX, target.blockZ,
        "grave-create", null) == FlagDecision.ALLOW
}

// Predykat przekazywany do findSafeLocationNear:
isAllowedLocation = { target ->
    regionOwnershipChecker.canPlaceGrave(player, target) && plotsAllowGrave(target)
}

// Kontrola ostatecznej lokalizacji, także gdy safe-placement jest wyłączone:
if (!regionOwnershipChecker.canPlaceGrave(player, location) || !plotsAllowGrave(location)) {
    return null
}
```

Kontrola samego miejsca śmierci nie wystarcza, ponieważ wyszukiwarka przesuwa grób
na sąsiednie bloki. Odrzucenie musi nastąpić przed modyfikacją świata i zapisaniem
ekwipunku. Obecny `GraveDeathListener` zostawia standardowe dropy i XP, gdy
`createGraveAndGetIt` zwraca `null`. Przywracanie grobów z backupu to osobna ścieżka,
która również powinna sprawdzać docelową lokalizację, jeśli blokada ma ją obejmować.

Repozytorium integracji: [GraveDiggerX](https://github.com/SyntaxDevTeam/GraveDiggerX).
Powyższy przykład wymaga dostosowania i testu po stronie GraveDiggerX; jego pliki
nie są zmieniane przez dodanie tego API do PlotsX.
