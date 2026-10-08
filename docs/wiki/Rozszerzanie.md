# Większa baza — rozszerzanie i limity

[← Home](Home.md)

Brakuje miejsca na kolejną farmę? Powiększ działkę w jej panelu. Rozszerzenia są dostępne właścicielowi z uprawnieniem `plotsx.plot.expand`.

## Działka klasyczna

1. Stań w tej części swojej działki, od której chcesz dobudować kolejny fragment.
2. Otwórz `/plot` i wybierz rozszerzanie.
3. Wybierz północ, wschód, południe albo zachód.
4. Sprawdź teren i cenę, następnie potwierdź zakup.

Każdy zakup dodaje sąsiedni kwadrat o rozmiarze pierwotnej działki. Przy domyślnym promieniu 16 jest to **33 × 33 bloki**. Możesz przejść do dobudowanej części i rozszerzać od niej dalej, tworząc również kształty L. Puste narożniki poza zajętymi kwadratami pozostają wolne.

Działka nie rośnie jednocześnie w czterech kierunkach. Zajętej sąsiedniej części nie można przeskoczyć.

## Działka chunkowa

Początkowa działka zajmuje jeden chunk, czyli **16 × 16 bloków**. W menu rozszerzania wybierz sąsiedni chunk na mapie i potwierdź zakup. Nowy teren musi łączyć się z działką krawędzią; samo zetknięcie narożnikami nie wystarczy.

Jeżeli administrator pozostawił tę opcję włączoną, możesz też stanąć na wolnym chunku obok własnej działki i użyć `/plot expand`, aby przejść bezpośrednio do jego potwierdzenia.

Oba typy działek obejmują całą wysokość świata.

## Ile kosztują rozszerzenia?

Założenie działki jest bezpłatne. Domyślnie kolejne rozszerzenia jednej działki kosztują **500, 750, 1125, 1687,5…** w walucie serwera. Administrator może zmienić ceny osobno dla działek klasycznych i chunkowych lub ustawić je na `0`.

Płatny zakup wymaga działającej ekonomii przez Vault albo VaultUnlocked. Operatorzy również płacą za płatne rozszerzenia. Nieudany zakup nie zwiększa ceny następnego rozszerzenia.

## Limity

| Limit | Domyślnie |
| --- | --- |
| Liczba działek gracza we wszystkich światach | 5 |
| Najdalsza granica działki klasycznej od pierwotnego środka | 64 bloki |
| Łączna powierzchnia działek klasycznych i mieszanych | 16641 bloków²; limit efektywny może być wyższy dla właścicieli chunków |
| Chunki jednej działki chunkowej | 32 |
| Łączna liczba chunków gracza | 64 |

Uprawnienia rang mogą zmieniać te limity. Dla działek chunkowych limit powierzchni jest automatycznie podnoszony tak, aby skonfigurowana liczba chunków była osiągalna.

## Dlaczego zakup może zostać odrzucony?

Cały nowy teren musi zmieścić się w limitach i nie może nachodzić na inną działkę ani zablokowany region WorldGuard. Plugin nie przycina rozszerzenia do wolnego miejsca.

Jeżeli podczas otwartego menu zmienią się cena, teren, właściciel lub limity, otwórz ofertę ponownie. Pozostań przy właściwym fragmencie działki do potwierdzenia.

Nieudany zapis po potwierdzonym pobraniu pieniędzy uruchamia próbę zwrotu, także po rozłączeniu gracza. Jeśli serwer został przerwany albo odpowiedź ekonomii jest niejednoznaczna, operacja zostaje zapisana do wyjaśnienia przez administrację. Przekaż jej identyfikator z komunikatu o płatności.

Dalej: [konfiguracja cen i limitów](Konfiguracja.md).
