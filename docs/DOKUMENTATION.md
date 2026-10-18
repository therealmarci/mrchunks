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
| Abhängigkeiten | keine Pflicht. Optional squaremap (`compileOnly squaremap-api`, `softdepend`) |

## 2. Projektstruktur
```text
src/main/java/at/marcelr/mrchunks/
├─ MRChunks.java          Hauptklasse: Lebenszyklus, Prüf-Task, verbindet alles
├─ Settings.java          Record mit allen Config-Werten (validiert)
├─ ConfigUpdater.java     Config laden (BOM) und migrieren          ┐
├─ Messages.java          Sprachdateien lang/*.yml (MiniMessage)    │ aus MRsleep
├─ DiscordWebhook.java    asynchrone Discord-Logs mit Diagnose      │ übernommen
├─ UpdateChecker.java     GitHub/Modrinth-Updateprüfung             ┘
├─ Loader.java            ein gesetzter Chunkloader
├─ LoaderManager.java     Loader, Chunk-Tickets, Limits, Sperren, data.yml
├─ LoaderItem.java        das markierte Chunkloader-Item
├─ LoaderListener.java    Setzen, Unzerstörbarkeit, Rechtsklick, Welt laden
├─ BorderDisplay.java     Grenze als leuchtende Block-Displays (nur für den Betrachter)
├─ AdminActions.java      alle Aktionen (Befehle und Menüs nutzen dieselben)
├─ Texts.java             gemeinsame Platzhalter (<name>, <number>, <world>, <x> …)
├─ command/               /chunkloader, /mrchunks
├─ gui/                   Menu, MenuListener, Items, Pages, LoaderIcons, PlayerIcons,
│                         LoaderMenu, MyLoadersMenu, AdminMenu, PlayerLoadersMenu
├─ map/                   MapHooks (ohne squaremap-Typen), SquaremapHook (Ebene, Marker, Icon,
│                         Raster, Spieler ausblenden), SquaremapSetup (squaremap-Config einrichten)
└─ util/                  DurationUtil, Versions (mit Tests)
src/main/resources/
├─ config.yml             UTF-8 mit BOM!
├─ plugin.yml, build-info.properties
└─ web/squaremap-grid.js  Raster + ausgeblendete Spielerliste für squaremap
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
| `BlockPlaceEvent` (HIGH) | Prüfung von Permission, Welt, Sperre, Limit und Überschneidung (`loaders.overlap`: deny-own/deny-all). Bei Erfolg folgt die Registrierung erst bei MONITOR, wenn kein Schutz-Plugin abgebrochen hat |
| `BlockBreakEvent` | abgebrochen, mit Hinweis |
| `EntityExplodeEvent`, `BlockExplodeEvent` | Loader-Blöcke werden aus der Blockliste entfernt |
| `BlockPistonExtend/RetractEvent` | abgebrochen, wenn ein Loader bewegt würde |
| `EntityChangeBlockEvent`, `BlockBurnEvent` | abgebrochen (Wither, Endermen, Feuer bei brennbarem Material) |
| `PrepareItemCraftEvent` | kein Ergebnis, wenn ein Loader-Item im Raster liegt (verhindert Emeraldblock → 9 Smaragde) |
| Prüf-Task (`check-interval-seconds`) | `validate()` stellt fehlende Blöcke wieder her oder löscht den Loader |
| `ChunkLoadEvent` | Loader im geladenen Chunk werden einen Tick später geprüft (`validate(Collection)`), auch deaktivierte, deren Chunks sonst nie geladen sind |

Die Block-Suche läuft über einen Positions-Index (`byPosition`), damit große Explosionen nicht alle Loader durchgehen müssen.

**Überschneidung:** Zwei Bereiche überschneiden sich, wenn die Mittel-Chunks auf beiden Achsen höchstens `2 × radius` auseinander liegen (`LoaderManager#overlapping`). Tickets laufen über den Referenzzähler pro Chunk (`coverage`), deshalb ist Überlappen technisch immer sauber. `loaders.overlap` regelt nur, ob es erlaubt ist: `allow`, `warn` (Hinweis mit `sharedChunks` und Grenze nach dem Setzen), `deny-own`, `deny-all`.

**Namen & Nummern:** `Loader#id` ist intern (Schlüssel in `data.yml`, Marker-Keys). Spieler sehen `Loader#number`, die kleinste freie Nummer des Owners (`nextNumber`), oder den Namen aus dem Umbenennen-Dialog (`gui/RenameDialog`, Paper-Dialog-API mit Textfeld; `AdminActions#rename` entfernt Steuerzeichen und kürzt auf `gui.max-name-length`). Ältere `data.yml` ohne `number` werden beim Laden in Erstellungsreihenfolge durchnummeriert (`assignMissingNumbers`). Admin-Befehle nehmen `<spieler> <nr>`.

**Grenze (`BorderDisplay`):** pro Betrachter eine Sitzung mit `BlockDisplay`-Entities: waagrechte Balken je Chunk der Außenkante auf mehreren Höhen und Pfosten an jeder Chunk-Ecke. Die Entities sind `persistent = false` (werden nie gespeichert) und `visibleByDefault = false` (nur `player.showEntity` macht sie sichtbar), leuchten (`setGlowing`, Farbe aus `border.color`) und sind über ihre Mitte verankert, damit der Client sie lange anzeigt. Alle 10 Ticks folgen sie der Höhe des Spielers (`setTeleportDuration` glättet), fehlende Teile (Chunk entladen) werden neu erzeugt. Ende: Zeit abgelaufen, Button im Menü, Loader gelöscht (`loaderRemoved`), Quit, Weltwechsel, Plugin-Stopp. Beim An/Ausschalten wird sie neu eingefärbt (`loaderChanged`).

## 5. Item (`LoaderItem`)
Material, Name, Lore und Glanz kommen aus der Config. Markiert wird das Item im PersistentDataContainer (`mrchunks:chunkloader`). **Zwei Limits** (`LoaderManager.LimitKind` PLACE/ACTIVE), beide aufgelöst nach Admin-Override → höchste Permission `mrchunks.limit.<n>` bzw. `mrchunks.active.<n>` (nur online) → Config-Standard. Neue Loader starten nur aktiv, wenn `canActivate` gilt. `AdminActions#toggle` lehnt Aktivieren über dem Limit ab (außer mit `mrchunks.admin.manage`). `enforceActiveLimit` deaktiviert beim Senken die neuesten aktiven Loader. In `data.yml` stehen die Overrides unter `limits` bzw. `active-limits`.
**Limit-Logik:** Setzen erlaubt, wenn `gesetzte < Limit`. `give` gibt höchstens `Limit − gesetzte − Items im Inventar` aus. Damit kann niemand Items horten, um das Limit zu umgehen, denn beim Setzen wird immer neu gezählt.

## 6. Daten (`plugins/MRchunks/data.yml`)
```yaml
next-id: 3
loaders:
  '1': { owner: <uuid>, world: <uuid oder Weltname>, world-name: world, x: 100, y: 80, z: 100, created: <ms>, number: 1, name: "Farm", active: true }
limits:
  <spieler-uuid>: 10
bans:
  <spieler-uuid>: { until: <ms | Long.MAX = dauerhaft>, reason: "...", by: "Admin" }
```
`world` darf in einer von Hand bearbeiteten Datei auch ein Weltname sein. Beim nächsten Speichern wird daraus die UUID.

## 7. Menüs (`gui/*`)
- **`LoaderMenu`**: Reihe 2 = An/Aus, Mitglieder, Grenze anzeigen/ausblenden, Umbenennen, Löschen; Teleport nur mit `mrchunks.admin.teleport` unten rechts (Schließen rückt dann nach links). Löschen mit zweitem Klick innerhalb von `gui.confirm-seconds`. Wird der Loader inzwischen gelöscht, springt das Menü zurück.
- **`MyLoadersMenu`**: die eigenen Loader.
- **`AdminMenu`**: Statistik, Info, Filter, Spielerköpfe (auch offline; das Limit offline kennt nur Admin-Limit und Standard, weil Permissions nur bei Online-Spielern abfragbar sind).
- **`PlayerLoadersMenu`**: Limit (links +1, rechts −1, Shift zurücksetzen), alle löschen, Sperren und die Loader des Spielers.
- Alle Aktionen laufen über `AdminActions`, das jeweils selbst die Permission prüft.

## 8. squaremap (`map/MapHooks.java`, `map/SquaremapHook.java`)
- `compileOnly("xyz.jpenilla:squaremap-api:1.4.3")` und `softdepend: [squaremap]`. Dadurch startet squaremap vorher, ist aber nicht Pflicht.
- **`MapHooks`** enthält keinen einzigen squaremap-Typ. Erst wenn `isPluginEnabled("squaremap")` gilt, wird `SquaremapHook` erzeugt (`LinkageError` wird abgefangen). So entsteht ohne squaremap kein `NoClassDefFoundError`.
- **`SquaremapHook`** registriert pro Welt eine `SimpleLayerProvider`-Ebene `mrchunks_loaders` (Name aus `map-layer-name` der Sprachdatei) und ein selbst gezeichnetes 16×16-Icon `mrchunks_loader`. Jeder Loader bekommt ein `Marker.rectangle` in Blockkoordinaten der vollen Chunks und optional `Marker.icon`. Tooltips sind HTML, die Werte werden escaped.
- **Aktualisierung:** `LoaderManager` ruft nach jeder Änderung (setzen, löschen, an/aus) `maps().refreshSoon()` auf. Mehrere Änderungen in einem Tick führen zu einem einzigen Update. Zusätzlich gleicht ein Task alle `squaremap.update-seconds` ab. Fehlt die Ebene, etwa nach `/squaremap reload`, wird sie neu registriert. Bei jedem Update werden alle Marker der Welt neu aufgebaut, das ist einfach und driftet nie.
- **Ohne squaremap gibt es keine Karte.** Die frühere eigene Webseite (Port 8130) ist entfallen.
- **Adresse (`util/MapAddress`, ohne Bukkit-Bezug, mit Tests):** `squaremap.address` mit Schema wird unverändert genutzt, ohne Schema ist es ein Host (`https://host/` mit `reverse-proxy`, sonst `http://host:<internal-webserver.port>/`). Leer: squaremaps `web-address`, falls nicht `http://localhost:8080`, sonst `Bukkit.getIp()`, sonst die öffentliche IP, die `MapHooks#detectPublicIp` einmal asynchron von `checkip.amazonaws.com` holt (`detect-public-ip`). Das Grid-Skript wird relativ eingebunden und funktioniert hinter Proxys und unter Unterpfaden.
- **Automatische Einrichtung (`SquaremapSetup`):** Beim Start schreibt MRchunks, wenn squaremap aktiv ist und `squaremap.auto-configure` an ist, **einmal pro `SquaremapSetup.VERSION`** die empfohlenen Werte in `plugins/squaremap/config.yml`:
  - `background.interval-seconds: 5`, `player-tracker.enabled: false`, `ui.sidebar.pinned: pinned`
  - pro Welt `world-settings.<welt-key>.map.display-name` (Overworld/Nether/End aus `map-dimension-*` der Sprachdatei)
  - im Nether `iterate-up: true` und `max-height: 127`

  Danach folgt `squaremap reload` per Konsole und nach 2 s ein Abgleich, der Ebene, Icon und Raster wieder herstellt, denn squaremap verwirft sie beim Reload. Erledigt ist erledigt: `plugins/MRchunks/squaremap-setup.yml` merkt sich die Version, spätere Änderungen des Admins bleiben also erhalten. `/mrchunks map setup` erzwingt einen neuen Lauf. **Ändern sich die empfohlenen Werte, `VERSION` erhöhen.**
- **Spieler ausblenden (`squaremap.hide-players`):** `PlayerManager#hidden(uuid, true, false)` für alle Online-Spieler, bei jedem Join und bei jedem Abgleich. Damit landen keine Namen in `players.json`. Zusätzlich blendet das Skript die Spielerliste der Seitenleiste per CSS aus (`fieldset#players`). Beim Deaktivieren werden die Spieler wieder eingeblendet.
- **Chunkraster:** squaremap legt seine Leaflet-Karte nicht global ab, nur `window.L`. `SquaremapHook#installGrid` schreibt `web/squaremap-grid.js` (mit den Einstellungen als JSON von Gson, damit nichts aus der Config Skript einschleusen kann) als `mrchunks-grid.js` in `api.webDir()`. In `index.html` kommt eine Zeile mit `<!-- mrchunks-grid -->` **vor** dem Modul-Skript von squaremap.
  - Das Skript fängt per `Object.defineProperty(window, "L")` die Zuweisung von Leaflet ab und registriert `L.Map.addInitHook`, noch bevor squaremap die Karte erzeugt.
  - Zusätzlich fängt `L.Control.Layers.addInitHook` das Ebenen-Menü von squaremap ab. Das Raster wird dort per `addOverlay` eingetragen (`id = "mrchunks_grid"`, `order = 1000`), und squaremap merkt sich die Sichtbarkeit selbst als `localStorage hide_mrchunks_grid`. Findet das Skript nach etwa 10 s kein Menü, zeigt es als Notlösung einen eigenen Schalter.
  - Das Raster ist ein `L.GridLayer` mit Canvas-Kacheln in einem eigenen Pane (z-index 350). Blöcke pro Pixel = `2^(maxNativeZoom − zoom)`, wobei `maxNativeZoom` aus squaremaps Kachel-Ebene kommt. Neu gezeichnet wird nur, wenn sich die Zoomstufe der Welt ändert.
  - Die Einbindung ist idempotent und läuft bei jedem Abgleich, weil squaremap seine Web-Dateien beim (Re)Load neu schreibt. `remove()` entfernt Zeile und Datei wieder.
  - Ein `?v=<hash>` am Skript-Link sorgt dafür, dass Browser nach Config-Änderungen die neue Version laden.
- **Live:** Die Aktualität bestimmt squaremap selbst. `background.interval-seconds` setzt sowohl den Render-Takt als auch `tiles_update_interval` der Webseite. Empfohlen sind 5 s, siehe README.
- Getestet mit squaremap 1.4.3 für Paper 26.3: Die Ebene erscheint in `tiles/minecraft_overworld/markers.json` mit `"id":"mrchunks_loaders"`.

## 9. Entwicklung
```text
gradlew.bat build     Kompilieren + Tests + JAR  → build/libs/MRchunks-mc<minecraft>-v<version>.jar
```
Der Release-Ablauf entspricht MRsleep, siehe **[RELEASES.md](RELEASES.md)**: Versionen heißen `mc<minecraft>-v<version>` (z.B. `mc26.3-v0.2.0`), entwickelt wird auf `beta`, stabile Versionen kommen von `main` oder einem Wartungs-Branch `mc/<version>`, und `release.yml` lädt auf GitHub und Modrinth hoch (Secret `MODRINTH_TOKEN`). Die Konsole ist immer englisch (`Messages#as`, `Messages#console`), Spieler bekommen die Sprache aus `language`.

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
| 10 | Erster Start mit squaremap | Log „squaremap eingerichtet: …“, Dimensionsnamen in `tiles/settings.json`, `players.json` leer |
| 11 | `/mrchunks reload` nach Änderung von `radius` | alte Tickets weg, neue Bereichsgröße aktiv |

## Einladungen & Ausschluss
- **Daten:** `LoaderManager` hält `loaderMembers` (Loader-ID → Spieler → gültig bis) und `ownerMembers` (Owner → Spieler → gültig bis; gilt für alle Loader des Owners, auch künftige). `Long.MAX_VALUE` = dauerhaft. In `data.yml` unter `members.loaders.<id>.<uuid>` bzw. `members.owners.<owner>.<uuid>`. Abgelaufene Einträge fallen beim Lesen (`members`) und beim Speichern weg, die eines gelöschten Loaders mit dem Loader.
- **Rechte (`AdminActions`):** `canToggle` = `canManage` (Owner\/Admin) oder `isMember`. Umbenennen, Löschen und `canManageInvites` bleiben Owner (`mrchunks.invite`) und Admins (`mrchunks.admin.manage`) vorbehalten. Beim Aktivieren durch Eingeladene gilt das Aktiv-Limit des Owners, der Owner wird benachrichtigt. Eingeladene dürfen das Menü öffnen (`canView`).
- **Menüs:** `InvitePickerMenu` (Köpfe der Online-Spieler, Dauer per Uhr wählbar, „Namen eingeben“ öffnet `InviteDialog`), `MembersMenu` (Liste, Klick = entfernen, bei einem Loader werden die Einladungen für alle Loader grau mit angezeigt), `InviteDialog` (Paper-Dialog mit Namensfeld und Dauer-Auswahl aus `invites.durations`), `SharedLoadersMenu` („Mit mir geteilt“). Einstieg: Button **Mitglieder** im `LoaderMenu`, **Mitglieder für alle Loader** und **Mit mir geteilt** in `MyLoadersMenu`.
- **`mrchunks.exempt` (streng):** blockiert `give`, Setzen, Einladen des Spielers und seine Rechte als Eingeladener. `enforceExempt` löscht beim Join und bei `/mrchunks reload` seine Loader und Items. Permissions sind nur online bekannt, Offline-Spieler werden beim nächsten Join bereinigt.
