# Chunk-Budget

Jeder Spieler hat ein **Budget in Chunks**: So viele Chunks dürfen alle seine **aktiven** Loader zusammen geladen halten. Wie er es aufteilt - wenige große oder viele kleine Loader, Quadrate oder Rechtecke -, entscheidet er selbst im Menü **Größe** (siehe [Größe und Chunk-Budget](groesse.md)).

| | Chunk-Budget |
|---|---|
| Standard (Config) | `loaders.default-budget` (147 = drei 7×7-Loader) |
| Per Permission | `mrchunks.budget.<n>` |
| Unbegrenzt | `mrchunks.budget.unlimited` |
| Per Admin | `/mrchunks limit <spieler> budget <n\|reset>`, Admin-Menü oder MRadmin-Weboberfläche |

- **Kosten:** Ein aktiver Loader kostet Breite × Länge, also 49 für 7×7. Ausgeschaltete Loader kosten nichts. Überlappende Chunks zählen für jeden Loader voll.
- **Neue Loader** sind `loaders.default-reach` groß (3 = 7×7). Reicht das freie Budget nicht, bekommen sie das größte Quadrat, das noch passt; ist gar nichts mehr frei, werden sie gesetzt, bleiben aber **aus**.
- **Höchstgröße:** `loaders.max-reach` Chunks pro Richtung (Standard 7 = bis 15×15).
- **Wie viele Loader** jemand setzen darf, ist für alle gleich: `loaders.max-loaders` (Standard 30).
- **Niemand überschreitet das Budget** - auch das Team nicht beim Einschalten oder Vergrößern fremder Loader. Admins erhöhen stattdessen das Budget.
- Senkt ein Admin das Budget, werden überzählige Loader sofort ausgeschaltet (die zuletzt gesetzten zuerst) und der Owner informiert.
- **Reihenfolge:** Ein Admin-Wert hat Vorrang, danach zählt die höchste Permission-Zahl, mindestens aber der Standard aus der Config.
- **Dummies** haben ein eigenes Limit nach demselben Muster (`dummies.default-limit`, `mrchunks.dummies.<n>`, `/mrchunks limit <spieler> dummy <n>`).

## Überschneidende Loader
`loaders.overlap`: `allow` erlaubt einfach, `warn` (Standard) erlaubt mit Hinweis und zeigt die Grenze, `deny-own` verbietet Überschneidungen mit eigenen Loadern, `deny-all` mit allen - beim Setzen und beim Vergrößern. Jeder Chunk wird ohnehin nur **einmal** geladen gehalten, im Budget zählt er aber für jeden Loader.

## Beispiele
```text
/lp group vip permission set mrchunks.budget.300 true       # VIPs: 300 Chunks (z.B. sechs 7x7-Loader)
/mrchunks limit Steve budget 200                             # einem Spieler 200 Chunks geben
/mrchunks limit Steve budget reset                           # wieder Permission bzw. Standard
```
