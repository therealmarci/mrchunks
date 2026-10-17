# MRchunks – Projektdokumentation

Diese Dokumentation richtet sich an alle, die am Code von MRchunks arbeiten. Was das Plugin für Server-Admins kann, steht in der [README](../README.md).
Build, Config-Migration, Discord, Update-Checker, Menü-Grundgerüst und Release-Ablauf stammen aus **MRsleep** und funktionieren genauso. Die dortige Projektdoku beschreibt sie ausführlicher.

---

## 1. Überblick
| | |
|---|---|
| Plattform | Paper 26.3 (`paper-api:26.3.build.200-stable`), Java 25 |
| Build | Gradle 9.8.1 (Wrapper), JUnit 6, Minotaur für Modrinth |
| Paket | `at.marcelr.mrchunks` |
| Abhängigkeiten | keine. Der Webserver ist `com.sun.net.httpserver` aus dem JDK, JSON kommt über Gson aus Paper |

## 2. Projektstruktur
```text
src/main/java/at/marcelr/mrchunks/
├─ MRChunks.java          Hauptklasse: Lebenszyklus, Prüf-Task, verbindet alles
├─ Settings.java          Record mit allen Config-Werten (validiert)
├─ ConfigUpdater.java     Config laden (BOM) und migrieren          ┐
├─ Messages.java          MiniMessage-Texte                         │ aus MRsleep
├─ DiscordWebhook.java    asynchrone Discord-Logs mit Diagnose      │ übernommen
├─ UpdateChecker.java     GitHub/Modrinth-Updateprüfung             ┘
├─ Loader.java            ein gesetzter Chunkloader
├─ LoaderManager.java     Loader, Chunk-Tickets, Limits, Sperren, data.yml
├─ LoaderItem.java        das markierte Chunkloader-Item
├─ LoaderListener.java    Setzen, Unzerstörbarkeit, Rechtsklick, Welt laden
├─ BorderDisplay.java     Partikel-Grenze
├─ AdminActions.java      alle Aktionen (Befehle und Menüs nutzen dieselben)
├─ Texts.java             gemeinsame Platzhalter (<id>, <world>, <x> …)
├─ command/               /chunkloader, /mrchunks
├─ gui/                   Menu, MenuListener, Items, Pages, LoaderIcons, PlayerIcons,
│                         LoaderMenu, MyLoadersMenu, AdminMenu, PlayerLoadersMenu
├─ web/WebMap.java        eingebaute Webkarte
└─ util/                  DurationUtil, Versions (mit Tests)
src/main/resources/
├─ config.yml             UTF-8 mit BOM!
├─ plugin.yml, build-info.properties
└─ web/index.html         die Webkarte (Canvas, ohne externe Ressourcen)
```

## 3. Chunks laden (`LoaderManager`)

### Tickets
- **`ticket`** (Standard): `World#addPluginChunkTicket`. In 26.3 heißt das Ticket-Typ `PLUGIN_TICKET` auf Level 31 (Entity-Ticking) mit den Flags „lädt“ und „simuliert“. Es wird an das Plugin gebunden und beim Deaktivieren entfernt.
- **`forceload`**: `World#setChunkForceLoaded`, wie `/forceload`. Der Ticket-Typ `FORCED` hat zusätzlich *keep dimension active*. Das ist nur nötig, wenn in der Paper-Config `unsupported-settings.disable-world-ticking-when-empty` aktiv ist (sonst geht `ServerLevel` nach 300 leeren Ticks schlafen). MRchunks merkt sich in `forcedByUs`, welche Chunks es selbst geforceloadet hat. Chunks, die vorher schon geforceloadet waren, werden nie entladen.

### Überschneidungen
`coverage: Welt → Chunk-Schlüssel → Anzahl aktiver Loader`. Ein Ticket entsteht beim Wechsel von 0 auf 1 und verschwindet beim Wechsel von 1 auf 0. Radius und Lademethode, mit denen die Tickets erstellt wurden (`appliedRadius`, `appliedMethod`), werden gespeichert. Deshalb löst `reload()` zuerst alle Tickets mit den **alten** Werten (`releaseAll`) und legt sie danach mit den **neuen** an (`applyAll`).

### Was läuft
Laut dekompiliertem `ServerChunkCache` ticken alle Entity-Ticking-Chunks, auch mit Zufalls-Ticks. Mob-Spawning läuft dagegen nur in `spawningChunks`, also in der Nähe von Spielern. Das steht so auch in der README.

### Welten
Loader in einer Welt, die noch nicht geladen ist, bekommen ihre Tickets per `WorldLoadEvent → applyWorld`.

## 4. Schutz (`LoaderListener`)
| Ereignis | Reaktion |
|---|---|
| `BlockPlaceEvent` (HIGH) | Prüfung von Permission, Welt, Sperre und Limit. Bei Erfolg folgt die Registrierung erst bei MONITOR, wenn kein Schutz-Plugin abgebrochen hat |
| `BlockBreakEvent` | abgebrochen, mit Hinweis |
| `EntityExplodeEvent`, `BlockExplodeEvent` | Loader-Blöcke werden aus der Blockliste entfernt |
| `BlockPistonExtend/RetractEvent` | abgebrochen, wenn ein Loader bewegt würde |
| `EntityChangeBlockEvent`, `BlockBurnEvent` | abgebrochen (Wither, Endermen, Feuer bei brennbarem Material) |
| `PrepareItemCraftEvent` | kein Ergebnis, wenn ein Loader-Item im Raster liegt (verhindert Emeraldblock → 9 Smaragde) |
| Prüf-Task (`check-interval-seconds`) | `validate()` stellt fehlende Blöcke wieder her oder löscht den Loader |

Die Block-Suche läuft über einen Positions-Index (`byPosition`), damit große Explosionen nicht alle Loader durchgehen müssen.

## 5. Item (`LoaderItem`)
Material, Name, Lore und Glanz kommen aus der Config. Markiert wird das Item im PersistentDataContainer (`mrchunks:chunkloader`). **Limit-Logik:** Setzen erlaubt, wenn `gesetzte < Limit`. `give` gibt höchstens `Limit − gesetzte − Items im Inventar` aus. Damit kann niemand Items horten, um das Limit zu umgehen, denn beim Setzen wird immer neu gezählt.

## 6. Daten (`plugins/MRchunks/data.yml`)
```yaml
next-id: 3
loaders:
  '1': { owner: <uuid>, world: <uuid oder Weltname>, world-name: world, x: 100, y: 80, z: 100, created: <ms>, active: true }
limits:
  <spieler-uuid>: 10
bans:
  <spieler-uuid>: { until: <ms | Long.MAX = dauerhaft>, reason: "...", by: "Admin" }
```
`world` darf in einer von Hand bearbeiteten Datei auch ein Weltname sein. Beim nächsten Speichern wird daraus die UUID.

## 7. Menüs (`gui/*`)
- **`LoaderMenu`**: Infos, an/aus, Grenze, Teleport (nur mit Permission), Löschen mit zweitem Klick innerhalb von `gui.confirm-seconds`. Wird der Loader inzwischen gelöscht, springt das Menü zurück.
- **`MyLoadersMenu`**: die eigenen Loader.
- **`AdminMenu`**: Statistik, Info, Filter, Spielerköpfe (auch offline; das Limit offline kennt nur Admin-Limit und Standard, weil Permissions nur bei Online-Spielern abfragbar sind).
- **`PlayerLoadersMenu`**: Limit (links +1, rechts −1, Shift zurücksetzen), alle löschen, Sperren und die Loader des Spielers.
- Alle Aktionen laufen über `AdminActions`, das jeweils selbst die Permission prüft.

## 8. Webkarte (`web/WebMap.java`)
- `HttpServer` auf `web.bind:web.port`, 2 Daemon-Threads. Routen: `/` (Seite), `/api/loaders` (JSON). Alles andere bekommt 404, andere Methoden als GET bekommen 405.
- **Threading:** Der Main-Thread baut alle `refresh-seconds` einen JSON-Snapshot (`volatile String`). Die HTTP-Threads lesen nur diesen String.
- **Zugriff:** `?key=` oder Header `X-Access-Key`, Vergleich mit `MessageDigest.isEqual` (zeitkonstant). Header: `no-store`, `nosniff`, `no-referrer`.
- `index.html` ist eigenständig, ohne CDN oder externe Ressourcen. Es zeichnet die Chunk-Raster per Canvas, mit Verschieben, Zoom, Tooltip, Liste und automatischer Aktualisierung. `{{TITLE}}` wird beim Start ersetzt (HTML-escaped).

## 9. Entwicklung
```text
gradlew.bat build     Kompilieren + Tests + JAR  → build/libs/MRchunks-<version>.jar
```
Der Release-Ablauf entspricht MRsleep: Entwickelt wird auf `beta`, stabile Versionen kommen von `main`, getaggt wird mit `v<version>`, und `release.yml` lädt auf GitHub und Modrinth hoch (Secret `MODRINTH_TOKEN`).

### Manuelle Testcheckliste
| # | Test | Erwartung |
|---|---|---|
| 1 | `/chunkloader give`, platzieren | Meldung „Chunkloader #1 gesetzt – 49 Chunks“, Grenze per Rechtsklick sichtbar |
| 2 | Abbauen, TNT daneben, Kolben dagegen | Block bleibt |
| 3 | Limit erreicht, `/chunkloader give` | `give-none-left`. Weiteres Setzen über gehortete Items geht nicht (`place-limit`) |
| 4 | Loader-Item in die Werkbank | kein Ergebnis |
| 5 | Menü → Deaktivieren, `/forceload query` (bei `forceload`) | Chunks frei, Block bleibt |
| 6 | Zwei überlappende Loader, einen löschen | gemeinsame Chunks bleiben geladen |
| 7 | Loader im Nether | Bereich geladen (`execute in minecraft:the_nether if loaded …`) |
| 8 | `/mrchunks ban <spieler>` | alle seine Loader weg, Items aus dem Inventar entfernt, `give` verweigert |
| 9 | `/mrchunks limit <spieler> 5` | Kopf im Admin-Menü zeigt „5 (individuell)“ |
| 10 | Webkarte mit/ohne `?key=` | 200 / 401, Loader und Bereiche sichtbar |
| 11 | `/mrchunks reload` nach Änderung von `radius` | alte Tickets weg, neue Bereichsgröße aktiv |
