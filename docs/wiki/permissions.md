# Permissions

„OP“ heißt: nur Operatoren haben sie automatisch. „niemand“ heißt: muss ausdrücklich vergeben werden. Wie die Gruppen in allen Plugins aufgebaut sind: [Rechte mit LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Gruppen
Jede Gruppe enthält die darunter, die Team-Gruppen auch `mrchunks.player` - am besten `mrchunks.player` der Default-Gruppe geben:

<!-- auto:permission-groups -->

```text
/lp group default permission set mrchunks.player true
/lp group supporter permission set mrchunks.groups.support true
/lp group staff permission set mrchunks.groups.staff true
/lp group admin permission set mrchunks.groups.admin true
```

## Einzeln
„Enthalten in“ nennt die kleinste Gruppe, die die Permission mitbringt; „–“ heißt: in keiner Gruppe, nur einzeln vergeben.

<!-- auto:permissions -->

Dazu zwei Permissions mit Zahl, die du selbst wählst:

| Permission | Wofür |
|---|---|
| `mrchunks.budget.<anzahl>` | Chunk-Budget, z.B. `mrchunks.budget.300` (siehe [Chunk-Budget](budget.md)) |
| `mrchunks.dummies.<anzahl>` | So viele Dummy-Spieler, z.B. `mrchunks.dummies.2` |

## Wildcards
> [!WARNING]
> `*` und `mrchunks.*` enthalten in LuckPerms **alles** - auch `mrchunks.exempt` (Loader werden gelöscht!) und die `unlimited`-Rechte. Für solche Gruppen:
> ```text
> /lp group admin permission set mrchunks.exempt false
> ```
> Soll das Budget trotzdem gelten, auch `mrchunks.budget.unlimited` auf `false` setzen.

## Beispiele
```text
# Alle Spieler dürfen Chunkloader nutzen
/lp group default permission set mrchunks.player true

# VIPs: 300 Chunks Budget (z.B. sechs 7x7-Loader)
/lp group vip permission set mrchunks.budget.300 true

# Moderatoren: wie Staff (Loader an/aus, Einladungen, Accounts), dazu sperren - aber nichts löschen
/lp group mod permission set mrchunks.groups.staff true
/lp group mod permission set mrchunks.admin.ban true

# Zweit-Accounts komplett ausschließen
/lp group alt permission set mrchunks.exempt true
```
