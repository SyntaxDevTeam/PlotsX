# Większa baza — rozszerzanie i limity

[← Home](Home.md)

Brakuje miejsca na kolejną farmę? Powiększ działkę w jej panelu.

## Jak powiększyć działkę?

1. Otwórz `/plot` dla swojej działki.
2. Wybierz rozszerzanie.
3. Sprawdź docelowy poziom, promień i cenę.
4. Potwierdź zakup.

Potrzebujesz uprawnienia `plotsx.plot.expand`. Rozszerzanie jest dostępne właścicielowi działki.

## Co się powiększa?

Teren rośnie równo w czterech kierunkach. Środek zostaje w tym samym miejscu. Krok 8 oznacza przesunięcie **każdej granicy o 8 bloków**. Kierunek patrzenia nie ma znaczenia.

Domyślna oferta wygląda tak:

| Poziom | Promień | Wymiary | Powierzchnia | Cena tego ulepszenia |
| --- | --- | --- | --- | --- |
| 0 — nowa działka | 16 | 33 × 33 | 1089 bloków² | Bez opłaty |
| 1 | 24 | 49 × 49 | 2401 bloków² | 500 |
| 2 | 32 | 65 × 65 | 4225 bloków² | 1000 |
| 3 | 48 | 97 × 97 | 9409 bloków² | 2000 |

Ceny są w walucie serwera. Poziomy kupujesz po kolei. Wszystkie trzy domyślne ulepszenia kosztują razem 3500. Administrator może zmienić ofertę.

## Trzy osobne limity

| Limit | Domyślnie |
| --- | --- |
| Liczba działek gracza | 5 |
| Maksymalny promień jednej działki | 64 |
| Łączna powierzchnia działek gracza | 16641 bloków² |

Limit promienia 64 nie dodaje kolejnego ulepszenia. Przy domyślnej ofercie ostatni dostępny poziom daje promień 48.

Powierzchnia działki to długość boku razy długość boku. Bok ma `2 × promień + 1` bloków. Do wspólnego limitu wliczają się wszystkie twoje działki.

## Dlaczego zakup może zostać odrzucony?

Cały nowy teren musi zmieścić się w limitach i nie może nachodzić na inną działkę ani chroniony region WorldGuard. Plugin nie przycina rozszerzenia do wolnego miejsca.

Płatny poziom wymaga działającej ekonomii i odpowiedniego salda. Cena `0` oznacza darmowe ulepszenie bez wymaganej ekonomii. Operatorzy i administratorzy również płacą za płatne poziomy.

Jeśli oferta zmieniła się, gdy menu było otwarte, otwórz je ponownie. Jeśli po pobraniu pieniędzy rozszerzenie się nie powiedzie, plugin próbuje zwrócić opłatę. Komunikat o nieudanym zwrocie przekaż administracji.

Dalej: [ustawienia poziomów](Konfiguracja.md).
