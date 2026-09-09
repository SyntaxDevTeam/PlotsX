# Panel działki

[← Home](Home.md)

Najważniejsze opcje masz pod ręką w `/plot`.

Na własnej działce komenda otwiera jej panel. Poza działkami pokazuje listę twoich terenów. Możesz też wybrać własną działkę po nazwie: `/plot Baza`. Stojąc na cudzym terenie, zwykły gracz nie otworzy panelu właściciela.

## Co znajdziesz w menu?

| Opcja | Do czego służy? |
| --- | --- |
| Informacje o działce | Pokazuje właściciela, nazwę, numer działki i datę utworzenia. |
| Kliknięcie informacji | Wyświetla granice przez około 20 sekund. |
| Flagi | Otwiera ustawienia ochrony. |
| Teleportacja | Przenosi na działkę, jeśli uda się znaleźć bezpieczne miejsce. |
| Zmiana nazwy | Otwiera formularz nazwy; na starszych wersjach zbiera nazwę przez czat. |
| Lista działek | Pomaga przełączać się między własnymi terenami. |
| Rozszerzanie | Pokazuje następny poziom, nowy rozmiar i cenę. |

## Nazwa, którą łatwo zapamiętać

Na serwerze z dostępnym Paper Dialog API (od 1.21.7) opcja otwiera formularz z aktualną nazwą oraz przyciskami Zapisz i Anuluj. Błąd walidacji zachowuje wpisaną wartość. Formularz wygasa po 60 sekundach.

Na starszych wersjach lub przy wymuszonym starym interfejsie masz 60 sekund na wpisanie nazwy na czacie. Wiadomość posłuży jako nazwa, zamiast trafić na czat ogólny. Gdy czas minie, otwórz opcję ponownie. Maksymalna długość nazwy wynosi 255 znaków.

Dialogi obsługują także potwierdzenia tworzenia, usuwania i rozszerzania działek oraz usuwania członków i przekazywania własności. Szczegóły wyboru interfejsu i obsługi translatorów protokołu opisuje [konfiguracja dialogów](../dialogs.md).

Nie możesz nadać dwóch swoim działkom tej samej nazwy. Wybieraj proste nazwy, na przykład `Baza`, `Farma` i `Port`. Unikaj nazw `add`, `remove` i `members`, bo są używane przez komendy.

## Powrót do domu

Otwórz panel wybranej działki i kliknij teleportację. Jeśli się nie uda, przygotuj wolne, bezpieczne miejsce na działce i spróbuj ponownie. Ta opcja nie dodaje komendy `/home`.

Dalej: [ochrona i flagi](Ochrona-i-flagi.md).
