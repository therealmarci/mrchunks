# Permissions

„OP“ heißt: nur Operatoren haben sie automatisch. „niemand“ heißt: muss ausdrücklich vergeben werden. Wie die Gruppen in allen Plugins aufgebaut sind: [Rechte mit LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Spieler
| Permission | Standard | Wofür |
|---|---|---|
| `mrchunks.player` | OP | **Alle Spieler-Rechte** auf einmal (`use`, `resize`, `map`, `view`, `invite`, `link`, `block`, `version`) - am besten der Default-Gruppe geben |
| `mrchunks.use` | OP | Chunkloader holen, setzen und die eigenen verwalten |
| `mrchunks.resize` | OP | Größe der eigenen Loader ändern (im Rahmen des Budgets) |
| `mrchunks.map` | OP | Knopf **Webkarte** |
| `mrchunks.view` | OP | Menü **fremder** Loader ansehen (nur ansehen und Grenze anzeigen) |
| `mrchunks.invite` | OP | Mitspieler zu eigenen Loadern einladen |
| `mrchunks.link` | OP | Zweit-Account verknüpfen |
| `mrchunks.block` | OP | Block der eigenen Loader in der Welt ändern (Auswahl aus `item.block-choices`) |
| `mrchunks.version` | OP | `/mrchunks version` |
| `mrchunks.budget.<anzahl>` | – | Chunk-Budget, z.B. `mrchunks.budget.300` |
| `mrchunks.dummies.<anzahl>` | – | So viele Dummy-Spieler, z.B. `mrchunks.dummies.2` |
| `mrchunks.budget.unlimited` · `dummies.unlimited` | niemand | Ohne Begrenzung |
| `mrchunks.exempt` | niemand | **Komplett ausgeschlossen:** keine Loader, keine Einladungen - vorhandene Loader werden beim Betreten **gelöscht** |

## Team-Gruppen
Jede enthält die darunter (und `mrchunks.player`):

| Gruppe | Standard | Enthält |
|---|---|---|
| `mrchunks.groups.support` | OP | Admin-Menü (alle Loader und ihren Status sehen), zu Loadern teleportieren, **auffällige** Loader (über einer Warngrenze) und ihren Dummy aus- und wieder einschalten, Live-Ansicht im Web |
| `mrchunks.groups.staff` | OP | Support + fremde Loader an/aus schalten, ihre Einladungen verwalten, Accounts verknüpfen und lösen |
| `mrchunks.groups.admin` | OP | Alles: Staff + löschen, umbenennen, Größe fremder Loader ändern, Dummies, Lag-Sperren aufheben, Limit-Sperre aussetzen, Budget und Dummy-Limit, Sperren, Pause und Überwachung, Discord, Reload, Update-Hinweis |

```text
/lp group supporter permission set mrchunks.groups.support true
/lp group staff permission set mrchunks.groups.staff true
/lp group admin permission set mrchunks.groups.admin true
```

## Einzelne Team-Rechte
| Permission | Standard | Wofür |
|---|---|---|
| `mrchunks.admin.gui` | OP | Admin-Menü öffnen (Aktionen darin brauchen ihre eigene Permission) |
| `mrchunks.admin.toggle` | OP | **Fremde** Loader an/aus schalten (im Rahmen des Budgets des Owners, enthält `toggle.conspicuous`) |
| `mrchunks.admin.toggle.conspicuous` | OP | **Fremde** Loader und ihren Dummy aus- und wieder einschalten, solange sie auffällig sind (über einer Warngrenze des Lag-Schutzes; nach dem Ausschalten bis zum nächsten Neustart). Einschalten bleibt im Budget bzw. Dummy-Limit des Owners. Im Spiel und in der MRadmin-Weboberfläche |
| `mrchunks.admin.invites` | OP | Einladungen **fremder** Loader verwalten |
| `mrchunks.admin.link` | OP | Accounts anderer verknüpfen und lösen |
| `mrchunks.admin.manage` | OP | **Fremde** Loader löschen, umbenennen, Größe ändern, Block wählen, Dummies, Lag-Sperren aufheben (enthält `toggle`, `invites`, `link`, `resize`) |
| `mrchunks.admin.resize` | OP | Größe **fremder** Loader ändern (im Spiel und in der MRadmin-Weboberfläche, im Rahmen des Budgets des Owners) |
| `mrchunks.admin.live` | OP | [Live-Ansicht](lag-schutz.md#live-ansicht-mradmin) eines Loaders in der MRadmin-Weboberfläche |
| `mrchunks.admin.suspendlock` | OP | [Limit-Sperre](lag-schutz.md#limit-sperre-aussetzen) eines einzelnen Loaders aussetzen, höchstens 24 Stunden (im Spiel und in der MRadmin-Weboberfläche) |
| `mrchunks.admin.teleport` | OP | Zu Loadern teleportieren |
| `mrchunks.admin.limit` | OP | Chunk-Budget und Dummy-Limit eines Spielers festlegen |
| `mrchunks.admin.ban` | OP | Spieler sperren und entsperren |
| `mrchunks.admin.discord` | OP | `/mrchunks discord` |
| `mrchunks.admin.reload` | OP | `/mrchunks reload` |
| `mrchunks.admin.update` | OP | Update-Hinweis beim Betreten des Servers |
| `mrchunks.admin.pause` | OP | Alle Chunkloader vorübergehend pausieren und die Pause aufheben (MRadmin-Weboberfläche) |
| `mrchunks.admin.monitoring` | OP | Gestaffelte Abschaltung samt harter Grenze aus- und einschalten (MRadmin-Weboberfläche) |

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
