# MRchunks

Chunkloader-Plugin für **Paper 26.3** (Java 25).

> 🧪 **Pre-Release (0.x):** MRchunks befindet sich in der Testphase. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern. Fehler bitte als [Issue](https://github.com/therealmarci/mrchunks/issues) melden.

- **Chunkloader-Blöcke** (Aussehen: Emeraldblock): Einmal platziert, bleibt ein **7×7-Chunk-Bereich** dauerhaft geladen, auch wenn der Spieler offline ist. Das funktioniert in **allen Dimensionen**.
- **Limit pro Spieler** aus der Config, mehr per Permission oder Admin-Befehl.
- **Unzerstörbar:** kein Abbauen, keine Explosionen, Kolben, Wither oder Endermen.
- **Menü per Rechtsklick:** Infos, Grenze anzeigen, an/aus, löschen.
- **Admin-Menü:** alle Spieler und ihre Chunkloader, mit Teleport, an/aus, löschen, Limit und Sperre.
- **Webkarte mit echtem Gelände** über [squaremap](https://modrinth.com/plugin/squaremap): alle Bereiche, Chunkraster, Overworld/Nether/End. squaremap wird automatisch erkannt und eingerichtet.
- **Discord-Logs**, **Update-Prüfung**, alles einstellbar in einer `config.yml`, eine Permission für jede Funktion.

---

## Installation
1. `MRchunks-<version>.jar` in den Ordner `plugins/` legen und den Server starten.
2. `plugins/MRchunks/config.yml` anpassen und `/mrchunks reload` ausführen.

Bei Updates wird die Config automatisch migriert: Deine Werte bleiben erhalten, und eine Sicherung `config.v<N>.backup.yml` wird angelegt.

---

## So funktioniert's
1. **`/chunkloader give`** legt einen Chunkloader ins Inventar, solange dein Limit nicht erreicht ist.
2. **Platzieren:** Ab sofort bleiben `7×7` Chunks um den Block geladen. Den Radius stellst du über `loaders.radius` ein.
3. **Rechtsklick** auf den Block öffnet das Menü und zeigt dir die Grenze des Bereichs mit Partikeln.
   **Sneak + Rechtsklick** verhält sich normal, du kannst also weiter Blöcke daran bauen.
4. **Löschen** geht nur über das Menü (zweimal klicken). Danach ist der Loader weg und dein Limit wieder frei. Ein Item gibt es nicht zurück, ein neues bekommst du per `/chunkloader give`.

**Was in geladenen Chunks läuft:** Redstone, Hopper, Öfen, Tiere und Entities, Pflanzenwachstum, also alle Zufalls-Ticks.
**Was trotzdem einen Spieler in der Nähe braucht:** natürliches Mob-Spawning und Mob-Spawner. Das ist eine Minecraft-Regel, die kein Chunkloader-Plugin ändert.

Nur Items aus `/chunkloader give` sind Chunkloader. Normale Emeraldblöcke bleiben normale Blöcke, und Chunkloader-Items lassen sich nicht zu Smaragden craften.

---

## Befehle

### Spieler: `/chunkloader` (Aliase `/cl`, `/chunkloaders`)
| Befehl | Beschreibung | Permission |
|---|---|---|
| `/chunkloader give [anzahl]` | Chunkloader ins Inventar (bis zum Limit) | `mrchunks.use` |
| `/chunkloader remove [anzahl\|all]` | Chunkloader-Items aus dem Inventar entfernen | `mrchunks.use` |
| `/chunkloader list` | Menü mit deinen Chunkloadern | `mrchunks.use` |
| `/chunkloader info` | So funktioniert's, dazu deine Chunkloader und dein Limit | `mrchunks.info` |
| `/chunkloader map` | Link zur Webkarte | `mrchunks.map` |

### Admin: `/mrchunks`
| Befehl | Beschreibung | Permission |
|---|---|---|
| `/mrchunks gui` | Admin-Menü | `mrchunks.admin.gui` |
| `/mrchunks list [spieler]` | Chunkloader auflisten | `mrchunks.admin.list` |
| `/mrchunks tp <id>` | Zu einem Chunkloader teleportieren | `mrchunks.admin.teleport` |
| `/mrchunks toggle <id>` | Chunkloader an/aus | `mrchunks.admin.manage` |
| `/mrchunks delete <id>` | Chunkloader löschen | `mrchunks.admin.manage` |
| `/mrchunks limit <spieler> <anzahl\|reset>` | Individuelles Limit | `mrchunks.admin.limit` |
| `/mrchunks ban <spieler> [dauer\|permanent] [grund]` | Sperren: löscht alle Loader des Spielers und verhindert neue | `mrchunks.admin.ban` |
| `/mrchunks unban <spieler>` / `bans` | Sperre aufheben / alle Sperren anzeigen | `mrchunks.admin.ban` |
| `/mrchunks map [setup]` | Status der Webkarte / squaremap neu einrichten | `mrchunks.admin.map` |
| `/mrchunks discord [test]` | Discord-Status / Testnachricht | `mrchunks.admin.discord` |
| `/mrchunks reload` | Config neu laden | `mrchunks.admin.reload` |
| `/mrchunks version` | Version und Update-Status | alle |

Owner können ihre eigenen Loader auch mit `toggle` und `delete` steuern.

---

## Permissions
| Permission | Standard | Beschreibung |
|---|---|---|
| `mrchunks.use` | alle | Chunkloader holen, setzen und die eigenen verwalten |
| `mrchunks.info` | alle | `/chunkloader info` |
| `mrchunks.map` | alle | `/chunkloader map` |
| `mrchunks.view` | alle | Menü fremder Chunkloader ansehen und deren Grenze anzeigen |
| `mrchunks.limit.<anzahl>` | – | Höheres Limit, z.B. `mrchunks.limit.10`. Die höchste Zahl gilt. |
| `mrchunks.limit.unlimited` | niemand | Beliebig viele Chunkloader |
| `mrchunks.admin` | OP | Enthält alle `mrchunks.admin.*` |
| `mrchunks.admin.gui` / `.list` / `.manage` / `.teleport` / `.limit` / `.ban` / `.map` / `.discord` / `.reload` / `.update` | OP | wie in den Tabellen oben |

**Reihenfolge beim Limit:** Ein Admin-Limit (`/mrchunks limit`) hat Vorrang. Danach zählt die höchste `mrchunks.limit.<n>`-Permission, mindestens aber `loaders.default-limit`.

> ⚠️ In LuckPerms enthalten `*` und `mrchunks.*` auch `mrchunks.limit.unlimited`. Wer das nicht will, setzt die Permission bei der Gruppe ausdrücklich auf `false`.

```text
/lp group vip permission set mrchunks.limit.6 true
/lp group mod permission set mrchunks.admin.gui true
/lp group mod permission set mrchunks.admin.teleport true
```

---

## Admin-Menü (`/mrchunks gui`)
- **Oben:** Übersicht (Anzahl Loader, aktive Loader, geladene Chunks, Spieler, Sperren), Version und Webkarten-Status, Filter (Alle mit Loadern, Online, Gesperrte).
- **Spielerköpfe:** Loader und Limit, woher das Limit kommt, online, offline oder gesperrt.
- **Klick auf einen Kopf:** Limit (Linksklick +1, Rechtsklick −1, Shift-Klick zurücksetzen), alle Loader löschen, sperren (1d, 7d, dauerhaft) oder entsperren, dazu alle Loader des Spielers.
- **Klick auf einen Loader:** an/aus, Grenze anzeigen, **hinteleportieren**, löschen.

Alle Menüs aktualisieren sich jede Sekunde. Jede Aktion prüft ihre eigene Permission.

---

## Webkarte (squaremap)
MRchunks zeigt die Chunkloader auf **[squaremap](https://modrinth.com/plugin/squaremap)**, einer Webkarte mit echtem Minecraft-Gelände. **Ohne squaremap gibt es keine Webkarte**, alles andere funktioniert trotzdem ganz normal.

### Einrichten
1. squaremap (Paper, für 26.3 verfügbar) in den Ordner `plugins/` legen und den Server neu starten.
2. **MRchunks erkennt squaremap automatisch und stellt es einmalig passend ein:**
   - Die Karte aktualisiert sich alle 5 Sekunden, Gebautes ist nach ca. 5 bis 10 s sichtbar.
   - Spieler werden weder auf der Karte noch in der Seitenleiste gezeigt.
   - Die Welt-Auswahl (**Overworld**, **Nether**, **End**) ist immer sichtbar.
   - Der Nether wird von innen gezeigt statt der Bedrock-Decke.

   Danach kannst du in `plugins/squaremap/config.yml` alles frei ändern, MRchunks überschreibt das nicht mehr. Erneut ausführen: `/mrchunks map setup`. Abschalten: `squaremap.auto-configure: false`.
3. **Einmal alles rendern.** squaremap zeichnet bestehende Gebiete erst nach einem Full-Render:
   ```text
   /squaremap fullrender minecraft:overworld
   /squaremap fullrender minecraft:the_nether
   /squaremap fullrender minecraft:the_end
   ```
   Weiße Flächen sind Gebiete, die noch nie erzeugt wurden. Für eine vorab gefüllte Karte die Welt vorher mit [Chunky](https://modrinth.com/plugin/chunky) erzeugen (z.B. `/chunky world world_nether`, `/chunky radius 1000`, `/chunky start`).
4. **Port 8080 beim Hoster freigeben** und den öffentlichen Link unter `squaremap.public-url` eintragen, z.B. `http://play.deinserver.de:8080`, oder in der squaremap-Config als `web-address`. Dann bekommen alle Spieler den Link mit **`/chunkloader map`** (auch in `/chunkloader info`).

### Was MRchunks auf der Karte zeigt
- **Ebene „Chunkloader“:** pro Loader der 7×7-Bereich als farbige Fläche, eine Farbe pro Owner, deaktivierte Loader grau. Dazu ein Chunkloader-Symbol und Infos beim Darüberfahren oder Anklicken (Owner, Position, Bereich, Status). Änderungen erscheinen sofort.
- **Ebene „Chunkraster“:** Chunk-Linien ab einer sinnvollen Zoomstufe, gelbe Regionsgrenzen (32 × 32 Chunks) und bei starkem Zoom die Chunk-Koordinaten.
- Beide Ebenen schaltet jeder Besucher im **Ebenen-Menü oben links** ein und aus.
- Farben, Deckkraft, Symbol, Texte, Dimensionsnamen und Raster stellst du unter `squaremap:` in der MRchunks-Config ein. `/mrchunks map` zeigt den Status.

---

## Discord
Webhook in Discord anlegen (*Kanal bearbeiten → Integrationen → Webhooks*), dann `discord.enabled: true` setzen, die `webhook-url` eintragen und `/mrchunks reload` ausführen.
`/mrchunks discord test` prüft die Einrichtung.

Diese Events gibt es: Loader gesetzt, gelöscht, an/aus geschaltet, Block wiederhergestellt oder fehlend, dazu die Admin-Aktionen Löschen, Sperren, Entsperren, Limit und Reload. Texte und Farben stellst du unter `discord.events` ein.

---

## Häufige Fragen
**Ein Loader-Block ist trotzdem verschwunden (z.B. durch WorldEdit).**
MRchunks prüft regelmäßig (`loaders.check-interval-seconds`) und stellt den Block wieder her. Mit `restore-missing-blocks: false` wird der Loader stattdessen gelöscht.

**Funktioniert das im Nether und im End?**
Ja. Hast du in der Paper-Config `disable-world-ticking-when-empty` aktiviert, stell `loaders.load-method: forceload` ein. Dann ticken auch leere Dimensionen weiter.

**Überschneiden sich zwei Loader, sind die Chunks dann doppelt geladen?**
Nein. Jeder Chunk wird nur einmal geladen gehalten und erst freigegeben, wenn kein Loader ihn mehr abdeckt.

---

## Lizenz
MRchunks ist **source-available**, aber nicht Open Source ([MRchunks Lizenz](LICENSE)).

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Auf eigenen Servern nutzen, auch mit Spenden oder Rängen | Weiterveröffentlichen (auch verändert) |
| Für den eigenen Gebrauch verändern | Verkaufen |
| Verbesserungen per Pull Request einreichen | Lizenzhinweis entfernen |

Den Code-Aufbau beschreibt die [Projektdokumentation](docs/DOKUMENTATION.md).
