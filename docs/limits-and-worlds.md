# Limity rang i światy

```yaml
plots:
  claiming:
    mode: classic # classic | chunks; zmiana wymaga restartu
  chunks:
    maxPerPlot: 32
    maxTotalOwned: 64
    expansion:
      price: 500.0
      priceMultiplier: 1.5
  maxPlots: 5
  radius: 16
  world: ["world", "survival", "world_nether"]
```

`maxPlots` liczy działki łącznie we wszystkich światach. `claiming.mode` wybiera
geometrię wyłącznie nowych działek. `classic` używa promienia, a `chunks` tworzy
działkę z całego chunka 16 × 16. Istniejące działki zachowują swój typ po zmianie
configu i są nadal chronione. `radius` dotyczy tylko nowych działek klasycznych.

Przykładowe uprawnienia do nadania grupie VIP:

| Uprawnienie | Efekt |
| --- | --- |
| `plotsx.plot.max-plots.10` | Limit 10 działek, także przy przekazywaniu własności odbiorcy. |
| `plotsx.plot.radius.24` | Początkowy promień 24. |
| `plotsx.plot.size.96` | Maksymalny promień 96. |
| `plotsx.plot.max-area.100000` | Łączna powierzchnia do 100000 bloków. |
| `plotsx.plot.max-chunks-per-plot.64` | Do 64 chunków w jednej działce chunkowej. |
| `plotsx.plot.max-chunks.128` | Łącznie do 128 posiadanych chunków. |

Wygrywa najwyższa przyznana wartość danego uprawnienia; bez niej obowiązuje config.
Wartości ujemne i nienumeryczne są pomijane. `max-plots.0` blokuje nowe działki;
promień jest ograniczony od dołu do 1. Początkowy promień nadal podlega limitowi
rozmiaru i powierzchni. Zmiany nie usuwają ani nie zmniejszają istniejących działek.
Limity powierzchni i liczby działek są wspólne dla obu geometrii. Limity chunków są
dodatkowe i nie wpływają na działki klasyczne. `maxPerPlot: 0` lub
`maxTotalOwned: 0` blokuje dalsze dodawanie chunków.

`plots.world` przyjmuje listę nazw. Stary zapis `world: "world"` nadal działa.
`["*"]` dopuszcza wszystkie światy, `[]` blokuje tworzenie działek. Dla zgodności
pusty wpis tekstowy i brak klucza dopuszczają wszystkie światy.
Światy muszą być załadowane na serwerze, np. przez Multiverse-Core. PlotsX używa
nazwy świata bieżącej lokalizacji i nie potrzebuje do tego osobnego API Multiverse.
Nie tworzy ani nie ładuje światów samodzielnie.

Lista do publikacji: [granty i wszystkie wbudowane flagi](wiki/Ochrona-i-flagi.md).
