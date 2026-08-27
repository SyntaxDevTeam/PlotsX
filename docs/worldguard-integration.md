# Integracja WorldGuard

WorldGuard jest opcjonalną zależnością PlotsX. Jeśli nie jest zainstalowany, tworzenie działek działa bez dodatkowej kontroli regionów.

## Zachowanie

Przy `/claim` PlotsX tworzy tymczasowy prostopadłościan odpowiadający całej planowanej działce:

- X/Z: środek działki ± skonfigurowany promień;
- Y: od minimalnej do maksymalnej wysokości świata.

WorldGuard sprawdza przecięcie tego obszaru ze wszystkimi fizycznymi regionami świata. Claim jest odrzucany, jeśli choć część działki nachodzi na region WorldGuard. Kontrola odbywa się:

1. przed otwarciem GUI potwierdzenia;
2. ponownie po kliknięciu potwierdzenia.

Region `__global__` jest pomijany, ponieważ nie reprezentuje fizycznego obszaru utworzonego przez `/rg` i w przeciwnym razie blokowałby cały świat.

Jeżeli WorldGuard jest włączony, ale jego `RegionManager` jest niedostępny albo sprawdzenie kończy się wyjątkiem, PlotsX blokuje claim dla bezpieczeństwa i zapisuje ostrzeżenie w konsoli.

## Wersje kompilacyjne

| Paper | Java | WorldGuard | Wynik |
|---|---:|---|---|
| 1.20.6 | 21 | 7.0.13 | sukces |
| 1.21.11 | 21 | 7.0.13 | sukces |
| 26.2 | 25 | 7.0.18 | sukces |

WorldGuard i WorldEdit są zależnościami `compileOnly` i nie są dołączane do JAR-a PlotsX. Muszą być zainstalowane na serwerze przez administratora.

Kompilacja dla Paper 26.2 i WorldGuard 7.0.18:

```text
./gradlew --no-daemon clean compileKotlin \
  -PpaperApiVersion=26.2.build.+ \
  -PworldGuardVersion=7.0.18 \
  -PjavaVersion=25
```
