# Uzgadnianie płatności przez administratora

Stan: implementacja i testy repozytorium wykonane 19 września 2026 r. Akceptacja komend na działającym Paper i porównanie z historią rzeczywistego dostawcy pozostają otwarte.

## Dostęp i procedura

Wymagane są oba uprawnienia: `plotsx.cmd.ptx` oraz `plotsx.admin.payments` (nowe uprawnienie domyślnie dla operatorów). Rozstrzygnięcia mogą zapisywać gracze i konsola serwera. Audyt zapisuje UUID administratora; UUID zerowy oznacza konsolę.

1. `/ptx payments list [strona]` wyświetla nierozliczone operacje, po 10 na stronę.
2. `/ptx payments show <UUID>` pokazuje właściciela, aktora zakupu, kwotę, walutę, dostawcę, działkę, świat, chunki, rewizję oraz stan i `updatedAt`.
3. Administrator sprawdza historię właściwego konta, świata i waluty u wskazanego dostawcy. Przy potwierdzonym obciążeniu bez przyznania terenu zwrot należy wykonać i zweryfikować narzędziami dostawcy przed zamknięciem sprawy.
4. Decyzję zapisuje komenda:

```text
/ptx payments resolve <UUID> <stan> <updatedAt> <NO_DEBIT|REFUND_CONFIRMED> confirm <uzasadnienie>
```

Stan i znacznik czasu należy skopiować z aktualnego podglądu. Uzasadnienie ma 8–120 znaków, nie może zawierać znaków sterujących i powinno wskazywać dowód, np. numer transakcji zwrotu lub zgłoszenia. Nie należy wpisywać sekretów dostawcy ani danych uwierzytelniających.

| Stan wejściowy | Decyzja | Stan końcowy |
| --- | --- | --- |
| `UNCERTAIN` | `NO_DEBIT` — potwierdzono brak obciążenia | `CANCELLED` |
| `UNCERTAIN` | `REFUND_CONFIRMED` — potwierdzono zwrot | `REFUNDED` |
| `REFUND_REQUIRED` | `REFUND_CONFIRMED` | `REFUNDED` |

Pozostałe przejścia są odrzucane. Operacje zakończone nie mogą zostać ponownie rozliczone. Jeżeli brakuje dowodów, wpis pozostaje nierozliczony i nadal blokuje kolejny zakup właściciela lub działki. Komenda nie wywołuje API ekonomii ani nie przyznaje chunka. Potwierdzona płatność bez terenu jest rozliczana przez zwrot, po którym gracz może rozpocząć nowy zakup.

## Spójność i audyt

SQL działa na wątku roboczym. Zapis przechodzi przez ten sam koordynator co zakupy i zmiany działek; aktywny zakup uniemożliwia równoległe uzgodnienie. Stan i `updatedAt` są porównywane również w warunku SQL UPDATE. Zmiana stanu i dopisanie audytu zatwierdzają się razem; błąd audytu wycofuje decyzję.

Audyt korzysta z istniejącej tabeli `plot_logs`: `plot_id`, UUID administratora, czas oraz akcja `RECONCILE <UUID operacji> <stan poprzedni>-><stan końcowy> <uzasadnienie>`. Mieści się w limicie 255 znaków. Tabela nie ma kaskadowego usuwania razem z działką i jest już objęta backupem. Nie wprowadzono kolejnej migracji ani wersji formatu kopii. Pełne odtworzenie backupu zastępuje historię zgodnie z dotychczasowym kontraktem importu.

Błąd odpowiedzi po commit może oznaczać, że zapis jednak się powiódł. Komunikat poleca ponowny podgląd; nie ma automatycznego ponowienia decyzji. Terminalny stan odrzuci powtórzenie.

## Weryfikacja i ograniczenia

Dodano cztery testy, każdy uruchamiany na SQLite i H2:

- Audyt decyzji, zachowanie geometrii oraz odrzucenie nieaktualnego i powtórzonego potwierdzenia.
- Odrzucenie braku obciążenia przy potwierdzonym długu oraz nieprawidłowego uzasadnienia.
- Rollback decyzji przy błędzie audytu, także po usunięciu działki.
- Zamknięcie niepewnej operacji bez obciążenia, zwolnienie właściciela i odtworzenie stanu wraz z audytem z backupu.

Pełny zestaw: **140 testów, bez błędów i pominięć**; `shadowJar` zbudowany. Testy nie potwierdzają wykonania przelewu u rzeczywistego dostawcy — komenda rejestruje odpowiedzialną decyzję administratora na podstawie zewnętrznych dowodów. Podgląd pobiera obecnie cały dziennik przed filtrowaniem; stronicowanie SQL i indeksy pozostają optymalizacją E8. Komunikaty administracyjne są obecnie angielskie, zgodnie z istniejącymi komendami importu/eksportu.
