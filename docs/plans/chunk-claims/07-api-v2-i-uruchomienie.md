# 07 — API v2 i uruchomienie trybu chunkowego

> Aktualizacja 14.09.2026: zakup chunków z ekonomią i GUI ekwipunkowym jest podłączony — [iteracja 7 i aktualny zakres](11-zakup-chunka-i-gui.md). Poniższy opis ograniczeń odzwierciedla etap powstania tego dokumentu.

[Spis planu](README.md) · [Postęp](06-postep-implementacji.md)

## Konfiguracja dostępna w kodzie

```yaml
plots:
  claiming:
    mode: classic
  chunks:
    maxPerPlot: 32
    maxTotalOwned: 64
```

`classic` jest domyślny. `chunks` zmienia strategię nowych zajęć na jeden chunk 16 × 16, obejmujący pełną wysokość świata. Typ zapisany w bazie zawsze rozstrzyga ochronę. Zmiana konfiguracji nie konwertuje danych. Zmiana trybu wymaga restartu; `/ptxreload` odrzuca zmianę aktywnej strategii. Nieznany tryb i niepoprawne liczbowe limity powodują błąd walidacji.

Dodatkowe uprawnienia liczbowe:

- `plotsx.plot.max-chunks-per-plot.<liczba>` — maksymalna liczba chunków działki.
- `plotsx.plot.max-chunks.<liczba>` — łączna liczba chunków właściciela.

Wybierana jest najwyższa przyznana wartość. Zero zabrania odpowiedniej operacji. Limity liczby działek i łącznej powierzchni nadal dotyczą obu typów oraz wszystkich światów. Klasyczny limit promienia nie ogranicza geometrii chunkowej.

Potwierdzenie `/claim` porównuje świat i geometrię oferty. Przemieszczenie wewnątrz tego samego chunka jest dopuszczalne; przejście do innego unieważnia ofertę. Potwierdzenie ponownie sprawdza uprawnienia, świat, limity oraz region WorldGuard. Transakcja ponownie kontroluje stan SQL, limity i kolizje obu typów.

## Migracja danych

Start pluginu tworzy brakujące tabele starego schematu, a następnie wykonuje powtarzalną migrację geometrii przed ładowaniem cache i rejestracją ochrony. Rekordy klasyczne zachowują ID, właścicieli, promienie, rozszerzenia, flagi i członków. Chunk ma `radius = NULL`, jawny typ, rewizję i rekordy `plot_chunks`.

Import przez plugin dopuszcza teraz poprawne działki chunkowe. Import i zwykłe mutacje są objęte wspólną barierą ochrony. Backup v1 nadal jest obsługiwany; eksport mieszanych danych używa v2. Cofnięcie do starego pluginu wymaga odtworzenia kopii sprzed migracji; zmiana samego `mode` nie cofa schematu.

## Kontrakt publiczny

`PlotsXApi.apiVersion` wynosi **2**. `PlotSnapshot` udostępnia:

| Pole | `classic` | `chunks` |
| --- | --- | --- |
| `geometryType` | `classic` | `chunks` |
| `radius` | `Int` / `Integer`, promień podstawy | `null` |
| `extensions` | klasyczne rozszerzenia | pusta lista |
| `chunks` | pusta lista | pełna lista `ChunkSnapshot(x, z)` |
| `area` | suma powierzchni segmentów | liczba unikalnych chunków × 256 |
| `geometryRevision` | zapisana rewizja | zapisana rewizja |

`contains(world, x, z)` sprawdza rzeczywisty kształt, w tym ujemne współrzędne i wolne narożniki. `x/y/z` pozostaje punktem działki, a nie jej obwiednią. Snapshoty zwracane przez usługę mają niemodyfikowalne kolekcje.

**API v2 jest świadomie niezgodne binarnie z v1.** Zmiana `getRadius(): int` na `getRadius(): Integer` zmienia deskryptor JVM. Test kompiluje rzeczywistego konsumenta v1 i potwierdza `NoSuchMethodError` przy użyciu starego odwołania do getteru z v2. Nie należy wdrażać nieprzebudowanych integracji, nawet przy `mode: classic`.

Migracja konsumenta:

1. Użyć nowego artefaktu `-api.jar` jako zależności kompilacyjnej; nie dołączać klas API do własnego JAR.
2. Sprawdzić `getApiVersion() == 2` przed korzystaniem ze snapshotów.
3. Zastąpić założenie „każda działka ma promień” rozgałęzieniem po `geometryType` albo wspólnym `contains()` / `area`.
4. W Javie nie rozpakowywać automatycznie `Integer`, zanim nie zostanie sprawdzony typ działki. W Kotlinie obsłużyć `Int?`.
5. Przebudować integrację. Testy `ApiV2JavaConsumerTest` i `ApiV2KotlinConsumerTest` pokazują wywołania dla obu typów.

## Spójność decyzji ochrony

Każde wywołanie handlera `PlotProtectionListener` otrzymuje przypięty snapshot geometrii, flag i członków. Wszystkie odczyty w jego wieloetapowej decyzji widzą tę samą wersję. Kolejny handler tego samego zdarzenia może otrzymać nowszy snapshot — pin dotyczy pojedynczej decyzji handlera, nie całego łańcucha priorytetów wszystkich pluginów.

`ProtectionCoordinator` stosuje **globalną barierę jednego serwera**. Mutacja obejmuje kontrolę SQL, zapis/rollback i synchroniczną publikację cache przed zwrotem wyniku. Podczas zapisu zdarzenia anulowalne obsługiwane przez ochronę są zachowawczo anulowane, także poza zmienianą działką; `evaluateFlag()` zwraca odmowę. Wątek zdarzenia nie czeka na SQL. To zapewnia bezpieczeństwo również dla kolizji mieszanych i wspólnych limitów właściciela, kosztem chwilowego ograniczenia działań w całym serwerze. Nie wdrożono selektywnej rezerwacji pojedynczych regionów.

Błąd publikacji pozostawia ochronę w stanie odmowy i blokuje kolejne mutacje. Udany reload odbudowuje cache i zwalnia blokadę. Samo niepowodzenie SQL z poprawnym rollbackiem i udaną publikacją nie wymaga ręcznego odblokowania. Nie jest obsługiwany równoległy zapis tej samej bazy przez kilka serwerów ani ręczne SQL w czasie pracy.

## Zakres następnego etapu

Zajmowanie jednego chunka, transfer, usunięcie, ochrona, API i podstawowa wizualizacja są podłączone. Rozszerzanie chunkowe z płatnościami i dziennikiem odzyskiwania pozostaje E6. GUI rozszerzania chunków informuje o niedostępności tej operacji. Pełne testy dialogów z klientem, hooków z zewnętrznymi pluginami, tłumaczenia wszystkich języków oraz staging należą do E7/E8. Nie jest to deklaracja gotowości całego wydania.
