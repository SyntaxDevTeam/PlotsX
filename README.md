# PlotsX

### Twój teren. Twoja ekipa. Twoje zasady.

Dom, wspólna baza, farma czy cały port — wybierz miejsce w świecie i zadbaj o to, żeby inni grali według Twoich zasad. **PlotsX** pozwala graczom tworzyć chronione działki, zapraszać znajomych i zabezpieczać prywatne pojemniki. Najważniejsze opcje są dostępne w menu pod `/plot`.

**Dla serwerów survivalowych i wspólnego budowania.** Gracze nie potrzebują żadnych modów.

[Pobierz PlotsX 1.0.0](https://github.com/SyntaxDevTeam/PlotsX/releases) · [Poradnik po polsku](docs/wiki/Home.md) · [Pomoc na Discordzie](https://discord.gg/Zk6mxv7eMh)

## Co zyskują gracze?

- **Własne miejsce w świecie.** Zajmij wolny teren przez `/claim`, zatwierdź wybór i zacznij budować.
- **Wspólną bazę ze znajomymi.** Zapraszaj członków, przydzielaj im role i decyduj, do czego mają dostęp.
- **Prywatne skrzynie.** Chroń skrzynie, beczki i shulker boxy. Wybranym osobom możesz udostępnić konkretny pojemnik.
- **Więcej miejsca, kiedy go potrzebują.** Rozszerzaj działkę o sąsiedni teren. Ceny i limity ustala administracja.
- **Własne zasady ochrony.** Ustawiaj dostęp do budowania, pojemników, walki, zwierząt i innych aktywności.
- **Wygodny panel.** Zmieniaj nazwy, oglądaj granice i teleportuj się na swoje działki.

## Dwa sposoby zajmowania terenu

Administrator wybiera sposób tworzenia nowych działek:

| Działki klasyczne | Działki chunkowe |
| --- | --- |
| Zaczynasz od kwadratu wokół wybranego miejsca — domyślnie 33 × 33 bloki. | Zaczynasz od jednego chunka, czyli obszaru 16 × 16 bloków. |
| Rozbudowujesz teren o sąsiednie kwadraty tego samego rozmiaru. | Dokupujesz sąsiednie chunki i układasz z nich kształt swojej bazy. |

Oba typy obejmują całą wysokość świata. Zmiana trybu nie usuwa istniejących działek ani ich ochrony.

## Pierwsza działka w minutę

1. Znajdź wolne miejsce w świecie przeznaczonym na działki.
2. Wpisz `/claim` i zatwierdź utworzenie w menu.
3. Otwórz `/plot`, aby zobaczyć ustawienia swojej działki.
4. Zaproś znajomego: `/plot add Alex`.

Utworzenie działki jest bezpłatne. Rozszerzenia mogą kosztować walutę serwera. Dostęp do komend, rozmiary i limity zależą od ustawień administracji.

[Pełny poradnik pierwszej działki →](docs/wiki/Pierwsze-kroki.md)

## Dla administratorów

PlotsX oferuje limity dla graczy i rang, wybór światów, polskie i angielskie wiadomości oraz lokalny zapis danych w SQLite. Możesz też korzystać z MySQL, MariaDB, PostgreSQL lub H2.

**Platformy:** Paper i serwery zgodne z jego API, w tym Purpur; obsługa harmonogramów Folii. Plugin wymaga Javy 21 lub nowszej, zgodnie z wymaganiami używanego serwera. Szczegółowy zakres zgodności znajdziesz w [instrukcji instalacji](docs/wiki/Instalacja.md).

### Współpraca z innymi pluginami

| Dodatek | Co daje? |
| --- | --- |
| **Vault / VaultUnlocked + ekonomia** | Płatne rozszerzanie działek. Cena `0` pozwala korzystać z rozszerzeń bez ekonomii. |
| **WorldGuard** | Kontrola zakładania i rozszerzania działek na chronionych regionach. |
| **LuckPerms** | Wygodne nadawanie uprawnień i limitów rangom. |
| **CoreProtect** | Integracja z historią działań na serwerze. |
| **CleanerX** | Sprawdzanie nowych nazw działek pod kątem zakazanych słów. |

Podstawowe działki działają bez tych dodatków. Na Folii wybierz integracje obsługujące tę platformę.

### Instalacja

1. Pobierz główny plik `PlotsX-Paper-1.0.0.jar` z wydań projektu.
2. Przy wyłączonym serwerze umieść go w folderze `plugins`.
3. Uruchom serwer, ustaw światy i język w `plugins/PlotsX/config.yml`, a następnie nadaj graczom uprawnienia.
4. Uruchom serwer ponownie. Zwykłym kontem sprawdź tworzenie działki i ochronę przed gościem.

[Instalacja](docs/wiki/Instalacja.md) · [Uprawnienia](docs/wiki/Uprawnienia.md) · [Konfiguracja](docs/wiki/Konfiguracja.md) · [Integracje](docs/wiki/Integracje.md)

## Poznaj wszystkie możliwości

[Panel działki](docs/wiki/Panel-dzialki.md) · [Wspólna gra](docs/wiki/Wspolna-gra.md) · [Prywatne skrzynie](docs/wiki/Prywatne-skrzynie.md) · [Rozszerzanie](docs/wiki/Rozszerzanie.md) · [Ochrona i flagi](docs/wiki/Ochrona-i-flagi.md) · [Komendy](docs/wiki/Komendy.md)

## Pomoc i rozwój

Masz pytanie? Zajrzyj do [FAQ](docs/wiki/FAQ.md) lub odwiedź [Discord](https://discord.gg/Zk6mxv7eMh). Błędy zgłaszaj w [GitHub Issues](https://github.com/SyntaxDevTeam/PlotsX/issues), podając wersję pluginu, serwera i opis sytuacji.

Tworzysz własny dodatek? PlotsX udostępnia [API do integracji i własnych flag](docs/api.md).
