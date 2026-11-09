# Passamontagna

Plugin Paper/Spigot che dà un passamontagna realizzato con uno stick (Custom Model Data 64).

## Requisiti

- Paper o Spigot 1.21.x
- Java 21

## Compilazione

```bash
mvn package
```

Il plugin compilato si trova in `target/passamontagna-plugin-1.0.0.jar`. Copialo nella cartella `plugins` del server e riavvia.

## Utilizzo

- `/passamontagna get` — ricevi il passamontagna.
- Per indossarlo, metti lo stick con Custom Model Data 64 nello slot della testa.
- Quando è indossato, gli altri giocatori vedono il nome `Anonimo` in chat e non vedono il nome sopra la testa.
- Staff e operatori continuano a vedere il nome reale. La permission staff è `passamontagna.staff`.

Per visualizzare lo stick come passamontagna serve un resource pack che definisca il modello con Custom Model Data 64.
