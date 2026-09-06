# Prywatne skrzynie

[← Home](Home.md)

Wspólny teren nie musi oznaczać wspólnego magazynu. Chroń swoje przedmioty i udostępniaj je wtedy, kiedy chcesz.

Ochrona obejmuje skrzynie, skrzynie-pułapki, beczki i shulker boxy na działkach.

## Zabezpiecz pojemnik

Domyślnie pojemnik postawiony przez właściciela lub członka działki automatycznie staje się prywatny. Administrator może wyłączyć tę opcję.

Starszy, niezabezpieczony pojemnik możesz przypisać do siebie:

1. Stań w odległości najwyżej 6 bloków.
2. Spójrz na pojemnik znajdujący się na działce, której jesteś właścicielem lub członkiem.
3. Wpisz `/pchest lock`.

## Udostępnij przedmioty

Patrząc na swój pojemnik, wpisz `/pchest trust Alex`. Alex musi być właścicielem lub członkiem tej samej działki.

Udostępnienie pozwala korzystać z zawartości. Nie daje prawa do zniszczenia pojemnika ani zmiany jego ochrony. Właściciel działki również potrzebuje dostępu do prywatnego pojemnika innej osoby.

## Wszystkie opcje

Każda z tych komend wymaga patrzenia na pojemnik na działce.

| Komenda | Działanie |
| --- | --- |
| `/pchest` | Pokazuje sposób użycia. |
| `/pchest lock` | Zabezpiecza nieprzypisany pojemnik. |
| `/pchest unlock` | Usuwa prywatną ochronę. |
| `/pchest trust <gracz>` | Udostępnia pojemnik. |
| `/pchest share <gracz>` | To samo co `trust`. |
| `/pchest untrust <gracz>` | Odbiera udostępnienie. |
| `/pchest unshare <gracz>` | To samo co `untrust`. |
| `/pchest info` | Pokazuje właściciela i udostępnienia. |

Zamiast `/pchest` możesz zawsze wpisać `/privatechest`. Zdejmowanie ochrony i zmiana udostępnień należą do właściciela pojemnika lub administratora z odpowiednim dostępem.

Obecnie także odebranie udostępnienia wymaga, żeby wskazana osoba nadal należała do działki. Zrób to przed `/plot remove`.

## Dwie warstwy ochrony

Flaga skrzyń dotyczy dostępu na działce, a prywatna ochrona zabezpiecza konkretny pojemnik. Otwarcie działki dla gości nie odblokowuje automatycznie prywatnych skrzyń. Po `unlock` nadal obowiązują zasady działki.

Automatyczne przenoszenie przedmiotów do lub z prywatnych pojemników jest blokowane. Jeśli budujesz magazyn z lejkami, uwzględnij też flagę przenoszenia przedmiotów.

Dalej: [wspólna gra](Wspolna-gra.md).
