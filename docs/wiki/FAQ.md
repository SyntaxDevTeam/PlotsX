# Pytania i problemy

[← Home](Home.md)

## Nie mogę założyć działki

Sprawdź, czy jesteś w dozwolonym świecie, masz `plotsx.cmd.claim` i nie wykorzystałeś limitu działek lub powierzchni. Cały teren musi być wolny od innych działek i regionów WorldGuard. Sam wolny blok pod nogami nie wystarczy.

## Mam mniej niż pięć działek, a limit nadal mnie blokuje

Liczba działek i suma ich powierzchni to dwa różne limity. Kilka dużych działek może wykorzystać cały dostępny teren.

## Czy działka chroni kopalnię pod domem?

Tak. Ochrona obejmuje całą wysokość świata na obszarze działki. Nie można utworzyć drugiej działki bezpośrednio nad nią lub pod nią.

## Jak zobaczyć granice?

Otwórz panel i kliknij informacje o działce. Granice pojawią się na około 20 sekund.

## Jak wrócić do bazy?

Otwórz panel własnej działki po nazwie lub przez listę i wybierz teleportację. Jeśli nie ma bezpiecznego miejsca, teleportacja może zostać odrzucona.

## Znajomy jest członkiem, ale nie otwiera skrzyni

Prywatne pojemniki wymagają osobnego udostępnienia. Ich właściciel powinien spojrzeć na pojemnik i wpisać `/pchest trust <gracz>`.

## Jestem właścicielem działki, a nie mogę otworzyć pojemnika członka

Własność działki nie daje automatycznego dostępu do cudzych prywatnych pojemników. Poproś właściciela pojemnika o udostępnienie.

## Nie mogę odebrać skrzyni osobie usuniętej z działki

Zwykłe cofanie udostępnienia wyszukuje aktualnego właściciela i członków. Najlepiej cofnąć udostępnienie przed usunięciem członka. Przy pozostawionej prywatnej skrzyni poproś administratora o pomoc.

## Rośliny nie rosną albo woda nie płynie

Domyślnie włączone są blokady wzrostu i przepływu. Ustaw `cant-grow = NIE` i, jeśli potrzebujesz przepływu, `flow = NIE`. Sprawdź też [pozostałe flagi](Ochrona-i-flagi.md).

## Lejek nie przenosi przedmiotów

Sprawdź `item-transfer` oraz prywatną ochronę obu pojemników. Samo wyłączenie blokady przenoszenia nie otwiera prywatnych skrzyń dla lejków.

## Mam Vault, ale zakup rozszerzenia nie działa

Potrzebny jest też plugin ekonomii obsługujący saldo przez Vault lub VaultUnlocked. Sprawdź swoje pieniądze, limity działki i dostępność kolejnego poziomu.

## Mam limit promienia 64, ale działka kończy się na 48

Domyślna oferta ma trzy ulepszenia i kończy się promieniem 48. Limit to górna granica, a nie dodatkowy poziom do kupienia.

## Powiększenie jest blokowane przez spawn, chociaż go nie dotykam

Liczy się cały obszar po rozszerzeniu. Region WorldGuard może być większy niż widoczna budowla albo znajdować się nad lub pod tobą.

## Flaga ma TAK, a czynność nadal jest zablokowana

Część flag włącza właśnie blokadę. Sprawdź kolumnę „Co oznacza TAK?” na stronie [ochrony](Ochrona-i-flagi.md). Dodatkowe ograniczenia mogą pochodzić także z innych pluginów.

## Dlaczego administrator buduje mimo blokady?

Operator i osoba z dostępem do omijania ochrony mogą przechodzić przez wiele ograniczeń. Do testów używaj zwykłego konta bez członkostwa w działce.

## Zmieniłem alias albo język i nic się nie stało

Wykonaj pełny restart serwera. `/ptx reload` odczytuje config, ale nie odtwarza wszystkich ustawień uruchamianych przy starcie.

## Czy usunięcie działki usuwa dom?

Nie. `/unclaim` zwalnia teren i usuwa ochronę działki, a budowla zostaje w świecie. Zabierz cenne rzeczy przed potwierdzeniem.

## Czy mogę sprzedać działkę, połączyć ją z inną albo przekazać znajomemu?

Obecnie nie ma takich komend ani opcji w panelu PlotsX.

## Czy mogę zrobić kopię komendą export?

W tym wydaniu nie jest to poprawna kopia działek. Skorzystaj z instrukcji [kopii zapasowych](Administracja.md).

## Gdzie pobrać plugin lub zapytać o pomoc?

[Pobieranie](https://github.com/SyntaxDevTeam/PlotsX/releases) · [Discord](https://discord.gg/Zk6mxv7eMh) · [Zgłoszenia problemów](https://github.com/SyntaxDevTeam/PlotsX/issues)
