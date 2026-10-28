# API — integracje z PlotsX

[← Home](Home.md) · [English](en/API.md)

API pozwala innym pluginom odczytywać działki, sprawdzać ochronę i zarządzać członkami oraz ustawieniami. Dzięki temu dodatkowe menu, mechaniki rozgrywki i narzędzia administracyjne mogą korzystać z tych samych zasad co PlotsX.

Administrator nie musi niczego programować, aby używać samego PlotsX. Ta strona jest przeznaczona dla autorów integracji. Gotowe połączenia opisuje strona [Integracje](Integracje.md).

## Wymagania i zależność

API jest wspólne dla działek klasycznych i chunkowych. Wymaga Java 21 i Paper API; na Folii obowiązują opisane niżej zasady wątków. Numer biblioteki dobierz do zainstalowanej wersji PlotsX. Bieżąca wersja rozwojowa to `1.1.0-R0.1-SNAPSHOT`, a przygotowywane wydanie to **1.1.0**.

Gradle Kotlin DSL:

```kotlin
repositories {
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/")
}

dependencies {
    compileOnly("pl.syntaxdevteam:plotsx-api:1.1.0-R0.1-SNAPSHOT")
}
```

Dla wydań stabilnych wybierz odpowiadającą im wersję bez końcówki `-SNAPSHOT`. W Mavenie użyj tych samych współrzędnych i zakresu `provided`. Możesz też użyć pliku `PlotsX-<wersja>-api.jar` odpowiadającego lokalnej kompilacji pluginu. Nie dołączaj klas API do swojego JAR-a — podczas działania udostępnia je PlotsX.

W `paper-plugin.yml` pluginu korzystającego z opcjonalnej integracji dodaj:

```yaml
dependencies:
  server:
    PlotsX:
      load: BEFORE
      required: false
      join-classpath: true
```

Dla integracji wymaganej ustaw `required: true`. W klasycznym `plugin.yml` odpowiednikami są `softdepend: [PlotsX]` i `depend: [PlotsX]`. Kod opcjonalnej integracji umieść w osobnej klasie i ładuj dopiero po sprawdzeniu, że PlotsX jest włączony.

## Pobranie API

Publiczne klasy znajdują się w `pl.syntaxdevteam.plotsx.api`. Serwis pobieraj przez Bukkit `ServicesManager`. Nie korzystaj z wewnętrznych klas ani bazy danych PlotsX.

Przykład Kotlin, wewnątrz klasy integracji:

```kotlin
import pl.syntaxdevteam.plotsx.api.PlotsXApi

val api = server.servicesManager.load(PlotsXApi::class.java) ?: return
val location = player.location
val plot = api.getPlotAt(location.world.name, location.blockX, location.blockZ)
val owned = api.getOwnedPlots(player.uniqueId)
val accessible = api.getAccessiblePlots(player.uniqueId)
```

Java:

```java
import org.bukkit.Location;
import pl.syntaxdevteam.plotsx.api.PlotsXApi;
import pl.syntaxdevteam.plotsx.api.PlotSnapshot;

PlotsXApi api = getServer().getServicesManager().load(PlotsXApi.class);
if (api == null) return;
Location location = player.getLocation();
PlotSnapshot plot = api.getPlotAt(location.getWorld().getName(),
        location.getBlockX(), location.getBlockZ());
```

## Co można zrobić?

| Obszar | Metody i dane |
| --- | --- |
| Wyszukiwanie działek | `getPlot`, `getPlotAt`, `getPlots`, `getOwnedPlots`, `getAccessiblePlots` |
| Zapisane teleporty | `getOwnedPlotProfiles`, `PlotProfileSnapshot.teleport` |
| Członkowie | `getMembers`, `isMember`, `addMember`, `addMemberAsync`, `removeMember`, `removeMemberAsync` |
| Role i ich uprawnienia | `getRoles`, `getRolePermissions`, `setMemberRole`, `setMemberRoleAsync`, `setRolePermission`, `setRolePermissionAsync` |
| Własność | `transferOwnershipAsync` |
| Ochrona i ustawienia | `getFlags`, `getFlagValue`, `evaluateFlag`, `setFlag`, `setFlagAsync` |
| Uprawnienia zarządzania | `canManage` |
| Własne flagi | `registerFlag`, `unregisterFlag` |
| Powiadomienia o zmianach | `PlotFlagChangedEvent` |

Dotychczasowe synchroniczne metody zapisu również są dostępne. Do nowych integracji wybieraj warianty `Async`, aby zapis w bazie nie blokował rozgrywki.

Tworzenie, usuwanie i rozszerzanie działek nie są częścią tego API. Te operacje obejmują dodatkowe zasady ekonomii, limitów i kolizji terenu.

## Działki, geometria i teleporty

`PlotSnapshot` opisuje stan działki w chwili odczytu. Snapshoty i listy są niemodyfikowalne; zmiana ustawień wymaga wywołania odpowiedniej metody API. Kilka kolejnych odczytów nie stanowi jednej transakcji — pomiędzy nimi gracz może zmienić działkę.

| Właściwość | Działka klasyczna | Działka chunkowa |
| --- | --- | --- |
| `geometryType` | `classic` | `chunks` |
| `radius` | Promień pierwotnego kwadratu | `null` |
| `extensions` | Dokupione kwadratowe segmenty | Pusta lista |
| `chunks` | Pusta lista | Współrzędne zajętych chunków |
| `area` | Suma powierzchni segmentów | Liczba chunków × 256 |

`contains(world, x, z)` sprawdza rzeczywisty kształt działki. `containsArea(...)` sprawdza cały prostokąt, a `containsCircle(...)` cały obszar koła; samo sprawdzenie narożników może nie wykryć wolnej przestrzeni w nieregularnej działce. `geometryRevision` pozwala rozpoznać zmianę geometrii.

`isMember` sprawdza dodanych członków; właściciel jest osobnym polem `owner`. `getAccessiblePlots` zwraca własne działki i członkostwa, bez administracyjnego omijania ochrony.

`getOwnedPlotProfiles` łączy działkę z jej zapisanym punktem teleportacji. `teleport == null` oznacza brak własnego punktu. Odczyt nie ładuje chunków ani nie teleportuje gracza. Zapisane współrzędne nie gwarantują bezpiecznego lądowania.

## Sprawdzanie ochrony

`getFlagValue` zwraca zapisaną wartość flagi lub jej wartość domyślną; dla nieznanej działki albo flagi zwraca `null`. Część starszych flag interpretuje `true` jako blokadę. Do podjęcia decyzji o działaniu używaj `evaluateFlag`, które uwzględnia znaczenie flagi.

```kotlin
import pl.syntaxdevteam.plotsx.api.FlagDecision

val decision = api.evaluateFlag(worldName, blockX, blockZ, flagKey, playerUuid)
val allowed = decision == FlagDecision.ALLOW
```

| Wynik | Znaczenie |
| --- | --- |
| `ALLOW` | Działanie dozwolone; dla znanej flagi także poza działkami. |
| `DENY` | Działanie zabronione. |
| `UNKNOWN_FLAG` | Brak definicji flagi; integracja powinna odmówić działania albo jawnie obsłużyć brak obsługi. |

`subject` to UUID gracza lub `null` dla działania środowiskowego. Metoda nie sprawdza administracyjnych uprawnień serwera. Właściciel i członkowie omijają regułę tylko wtedy, gdy definicja ma `memberBypass=true`. Flaga `grave-create` jest domyślnie włączona i ma `memberBypass=false`.

## Zapis zmian i uprawnienia

Do metod zapisujących przekazuj rzeczywistego inicjatora jako `CommandSender`. PlotsX sprawdza własność, rolę i odpowiednie uprawnienia. Nie zastępuj gracza konsolą: konsola ma dostęp administracyjny.

`addMemberAsync` dodaje gracza z rolą `member`; do zmiany roli służy `setMemberRoleAsync`. Sprawdzaj wynik każdej operacji — wywołanie może zostać odrzucone z powodu braku uprawnień, nieistniejącej działki, zmiany członkostwa lub błędu zapisu.

Przykład wywołany na wątku gracza, wewnątrz klasy pluginu:

```kotlin
api.addMemberAsync(player, plotId, invitedUuid).thenAccept { result ->
    player.scheduler.execute(this, Runnable {
        player.sendMessage("PlotsX: $result")
    }, null, 1L)
}
```

Future kończy się po zapisaniu danych i udostępnieniu nowego stanu ochrony. Nie wywołuj `join()` ani `get()` na wątku serwera lub encji. Wiadomości, GUI i dostęp do świata planuj na odpowiednim schedulerze.

`DATABASE_ERROR` może oznaczać również błąd odświeżenia ochrony po trwałym zapisie. Nie ponawiaj automatycznie operacji bez sprawdzenia bieżącego stanu. Wyłączenie PlotsX kończy oczekujące zapisy tym samym wynikiem.

## Paper i Folia

| Wywołanie | Wymagany kontekst |
| --- | --- |
| Odczyty snapshotów, członków, flag, `evaluateFlag`, `getRolePermissions` | Mogą działać asynchronicznie; nie pobieraj przy tym lokalizacji encji z niewłaściwego wątku. |
| `canManage` i rozpoczęcie zapisów z graczem jako inicjatorem | Wątek encji gracza. |
| Operacje z konsoli | Wątek serwera. |
| `registerFlag`, `unregisterFlag` | Wątek serwera; nie wątek regionu Folii. |

Transfer własności wymaga członkostwa i obecności odbiorcy online oraz sprawdza jego limity. `transferOwnershipAsync` pobiera limity na wątku odbiorcy i zapisuje zmianę w tle. Synchroniczne `transferOwnership` na Folii wymaga dostępu do obu graczy z bieżącego wątku; inaczej zwraca `RECIPIENT_WRONG_THREAD`. Potwierdzenie transferu należy do integracji.

## Własne flagi i zdarzenia

`registerFlag` przyjmuje `FlagDefinition`. Klucz ma postać `nazwapluginu:flaga`, gdzie namespace odpowiada nazwie pluginu zapisanej małymi literami. Dozwolone są litery `a-z`, cyfry, `_`, `-` i jeden `:`; cały klucz może mieć najwyżej 120 znaków. Nie można zastąpić istniejącej definicji.

Własna flaga pojawia się w panelu ustawień i uprawnieniach ról jako `flag.nazwapluginu:flaga`. Jej nazwa i opis są zwykłym tekstem. Rejestracja udostępnia ustawienie; integracja sama sprawdza je przez `evaluateFlag` przy swoich działaniach. Po wyłączeniu providera definicja znika, ale zapisane ustawienia pozostają. Zarejestruj ją ponownie przy kolejnym włączeniu.

`PlotFlagChangedEvent` informuje o udanej zmianie flagi z GUI lub API. Zawiera działkę, klucz flagi, poprzednią i nową wartość oraz UUID inicjatora (`null` dla konsoli). Nie można go anulować. Zapis tej samej wartości nie emituje zdarzenia; import bazy i zmiany uprawnień ról również go nie wywołują.

Po `setFlagAsync` zdarzenie jest planowane na wątku inicjatora. Future nie czeka na jego dostarczenie; rozłączenie gracza lub wyłączenie pluginu może je uniemożliwić.

Po wyłączeniu PlotsX odrzuć zachowaną referencję do serwisu. Po ponownym włączeniu pobierz go jeszcze raz. `apiVersion` pozostaje historyczną rewizją `2`; nie jest numerem wersji zależności ani wyborem osobnego wariantu API.
