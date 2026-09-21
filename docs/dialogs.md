# Dialogi działek

PlotsX używa natywnego dialogu wyłącznie wtedy, gdy interakcja wymaga wpisania tekstu, obecnie przy zmianie nazwy działki. Dialog jest wybierany, jeśli serwer udostępnia Paper Dialog API (wprowadzone w Paper 1.21.7). Na starszych serwerach, w tym 1.20.6 i 1.21.6, zmiana nazwy odbywa się przez czat. Minimum w `paper-plugin.yml` pozostaje 1.20.6, a bytecode Java 21.

Potwierdzenia tworzenia, usuwania i rozszerzania działki oraz usunięcia członka i przekazania własności zawsze pozostają w menu ekwipunku. Natywny dialog nie zastępuje ekranów, które nie wymagają wprowadzenia treści. Istniejące komendy z kompletnymi argumentami zachowują dotychczasowe działanie.

```yaml
interactions:
  mode: auto
  translated-clients: false
```

`mode: legacy` wymusza dotychczasowy interfejs. Lokalne ViaVersion, ViaBackwards, ProtocolSupport, Geyser-Spigot i floodgate powodują użycie tego interfejsu dla wszystkich graczy, również nowych klientów. Jeżeli translacja działa wyłącznie na proxy, administrator powinien ustawić `translated-clients: true`. Nie próbujemy zgadywać wersji klienta na podstawie wersji serwera.

Nowe komunikaty znajdują się w sekcji `dialogs` plików językowych PL i EN. Nazwa ma maksymalnie 255 znaków, zgodnie ze schematem bazy. Czat i dialog stosują tę samą walidację, filtr CleanerX i kontrolę uprawnień. Zapis obecnej nazwy jest dozwolony.

Formularz jest związany z graczem, wygasa po 60 sekundach i może zostać zatwierdzony tylko raz. Kolejny formularz, otwarcie ekwipunku lub wyjście gracza unieważniają poprzednią sesję. Anulowanie nie wykonuje operacji.

Adapter dialogowy jest ładowany refleksyjnie; wspólna fasada nie zawiera typów Dialog API. Błąd wyświetlenia przywraca obsługiwane menu lub czat. Przełączanie na dialog i obsługa odpowiedzi używają schedulera gracza. Nie jest to audyt zgodności całego pluginu z Folia — istniejące operacje bazy/cache i wizualizacje zawierają również starsze wywołania schedulerów.

## Weryfikacja

Automatyczne testy `DialogSessionsTest` obejmują stare formularze, obcego gracza, upływ czasu, rozłączenie, wyłączenie oraz równoczesne próby zatwierdzenia. `./gradlew test shadowJar` buduje artefakt bez wdrażania na serwer.

Przed wydaniem należy wykonać testy w grze (nie są zastąpione przez testy jednostkowe):

- Paper 1.20.6 i 1.21.6: start JAR bez błędów brakujących klas, wszystkie operacje w starym interfejsie.
- Paper 1.21.7, 1.21.11 i wspierane 26.x: dialog zmiany nazwy, zapis, Anuluj/Escape i timeout; pozostałe operacje w menu ekwipunku.
- `mode: legacy` oraz konfiguracja translatora: działający fallback, w tym obsługa kliknięć w menu.
- Odebranie uprawnień, usunięcie działki lub zmiana właściciela przy otwartym formularzu: odrzucenie nieaktualnej operacji.
- Rozszerzenie w menu ekwipunku: zmiana ceny/poziomu, podwójne zatwierdzenie, brak środków i poprawne rozliczenie.
- Zmiana nazwy: pusta/niedozwolona nazwa, duplikat, bieżąca nazwa i awaria filtra; błędy zachowują wpis w formularzu.
- Folia: osobny test całych operacji z istniejącymi usługami pluginu.
