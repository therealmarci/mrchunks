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
├─ dummy/                 Dummies (Dummy-Spieler: Lebenszyklus, Events, Skins), DummyBackend (Schnittstelle)
│  └─ nms/                NmsDummyBackend, FakeConnection - einzige Stelle mit Server-Interna
├─ link/                  LinkRequests (Anfrage + Zustimmungs-Dialog für verknüpfte Accounts)
├─ gui/                   Menu, MenuListener, Items, Heads, Pages, LoaderIcons, PlayerIcons,
│                         LoaderMenu, LoaderSettingsMenu, BlockMenu, MyLoadersMenu, AdminMenu, PlayerLoadersMenu, LinkMenu, LinkPickerMenu
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
Laut dekompiliertem `ServerChunkCache` ticken alle Entity-Ticking-Chunks, auch mit Zufalls-Ticks. Mob-Spawning läuft dagegen nur in `spawningChunks`, also in der Nähe von Spielern. Dazu kommt Papers *Entity Activation Range*: Mobs ohne Spieler in der Nähe bekommen nur seltene, reduzierte Ticks (keine KI, kaum Bewegung, keine Portale, siehe Issue #12). Beides lösen die Dummy-Spieler (Abschnitt 10).

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

**Bereich:** Jeder Loader hat vier Reichweiten (`Loader.Direction` NORTH = −z, SOUTH, WEST = −x, EAST) ab dem Chunk seines Blocks; daraus ergeben sich `minChunkX/maxChunkX/minChunkZ/maxChunkZ`, `width`, `length` und `area` (= Kosten im Budget). Geändert wird nur über `LoaderManager#resize` (Tickets: erst `uncover` mit der alten, dann `cover` mit der neuen Fläche, danach Karte, Grenze, Lag-Index und Dummy aktualisieren). Die Prüfungen (Budget, `max-reach`, Überlappung nur für **neu** überlappte Loader, Dummy nur gleichmäßig und ≥ 2) stehen in `AdminActions#resizeProblem` - Spiel (`gui/SizeMenu`) und Web (`webResize`) nutzen dieselbe Methode.

**Überschneidung:** Zwei Bereiche überschneiden sich, wenn sich ihre Rechtecke einen Chunk teilen (`Loader#overlaps`, `LoaderManager#overlapping`; beim Setzen mit der Fläche, die der neue Loader bekäme). Tickets laufen über den Referenzzähler pro Chunk (`coverage`), deshalb ist Überlappen technisch immer sauber. `loaders.overlap` regelt nur, ob es erlaubt ist: `allow`, `warn` (Hinweis mit `sharedChunks` und Grenze nach dem Setzen), `deny-own`, `deny-all`.

**Namen & Nummern:** `Loader#id` ist intern (Schlüssel in `data.yml`, Marker-Keys). Spieler sehen `Loader#number`, die kleinste freie Nummer des Owners (`nextNumber`), oder den Namen aus dem Umbenennen-Dialog (`gui/RenameDialog`, Paper-Dialog-API mit Textfeld; `AdminActions#rename` entfernt Steuerzeichen und kürzt auf `gui.max-name-length`). Ältere `data.yml` ohne `number` werden beim Laden in Erstellungsreihenfolge durchnummeriert (`assignMissingNumbers`). Admin-Befehle nehmen `<spieler> <nr>`.

**Grenze (`BorderDisplay`):** pro Betrachter eine Sitzung mit `BlockDisplay`-Entities: waagrechte Balken je Chunk der Außenkante auf mehreren Höhen und Pfosten an jeder Chunk-Ecke. Die Entities sind `persistent = false` (werden nie gespeichert) und `visibleByDefault = false` (nur `player.showEntity` macht sie sichtbar), leuchten (`setGlowing`, Farbe aus `border.color`) und sind über ihre Mitte verankert, damit der Client sie lange anzeigt. Alle 10 Ticks folgen sie der Höhe des Spielers (`setTeleportDuration` glättet), fehlende Teile (Chunk entladen) werden neu erzeugt. Ende: Zeit abgelaufen, Button im Menü, Loader gelöscht (`loaderRemoved`), Quit, Weltwechsel, Plugin-Stopp. Beim An/Ausschalten wird sie neu eingefärbt (`loaderChanged`).

## 5. Item (`LoaderItem`)
Material, Name, Lore und Glanz kommen aus der Config. Markiert wird das Item im PersistentDataContainer (`mrchunks:chunkloader`).

**Chunk-Budget** (`LoaderManager.LimitKind.BUDGET`, daneben `DUMMY`): aufgelöst nach Admin-Override → höchste Permission `mrchunks.budget.<n>` (nur online, offline der gemerkte Wert aus `known-limits`) → `loaders.default-budget`. `usedBudget` = Summe der `area` aller aktiven Loader des Owners, `freeBudget`/`fitsBudget` prüfen davor. Einschalten (`AdminActions#toggle`, `webToggle`) und Vergrößern eines aktiven Loaders gehen nur im Budget - für alle, auch das Team. `enforceBudget` schaltet beim Senken die neuesten aktiven Loader aus. Neue Loader: `LoaderListener#placementReach` nimmt `default-reach` oder das größte Quadrat, das ins freie Budget passt; passt nichts, wird der Loader aus gesetzt.
**Höchstzahl:** `loaders.max-loaders` für alle gleich (keine Permission). Setzen erlaubt, wenn `gesetzte < max-loaders`. `give` gibt höchstens `max-loaders − gesetzte − Items im Inventar` aus. Damit kann niemand Items horten, denn beim Setzen wird immer neu gezählt.

**Migration (config-version 12, lang-version 13):** `ConfigUpdater` übernimmt `loaders.radius` als `default-reach` und `limits-per-chunks` (alte Fläche) und setzt `default-budget = default-active-limit × alte Fläche`. `LoaderManager#load` gibt Loadern ohne `reach` die `default-reach` und rechnet alte `active-limits` in `budgets` um (× Fläche von `default-reach`), danach wird sofort gespeichert. `budgets` wird immer geschrieben, damit die Umrechnung nur einmal passiert.

## 6. Daten (`plugins/MRchunks/data.yml`)
```yaml
next-id: 3
loaders:
  '1': { owner: <uuid>, world: <uuid oder Weltname>, world-name: world, x: 100, y: 80, z: 100, created: <ms>, number: 1, name: "Farm", active: true, dummy: true, block: DIAMOND_BLOCK, reach: { north: 3, south: 3, west: 1, east: 6 } }
# block nur, wenn der Besitzer einen anderen Block als item.material gewählt hat (Block ändern, item.block-choices)
# Chunk-Budget pro Spieler (Admin-Wert); alte active-limits werden beim ersten Laden hierher umgerechnet
budgets:
  <spieler-uuid>: 300
dummy-limits: { <spieler-uuid>: 2 }
# Limits aus Permissions vom letzten Mal online (budget | dummy)
known-limits: { budget: { <spieler-uuid>: 300 } }
# Verknüpfte Accounts: Zweit-Account -> Haupt-Account
links:
  <zweit-uuid>: <haupt-uuid>
bans:
  <spieler-uuid>: { until: <ms | Long.MAX = dauerhaft>, reason: "...", by: "Admin" }
```
`world` darf in einer von Hand bearbeiteten Datei auch ein Weltname sein. Beim nächsten Speichern wird daraus die UUID.

## 7. Menüs (`gui/*`)
- **`LoaderMenu`**: symmetrisch - Reihe 2 = `. Mitglieder . An/Aus Grenze Dummy . Einstellungen .` (Mitglieder nur mit Einladungen, Dummy nur mit `dummies.enabled`, sonst Glas); Reihe 3 Zurück links, Schließen in der Mitte, mit `mrchunks.admin.teleport` Schließen links und Kompass rechts der Mitte. Wird der Loader inzwischen gelöscht, springt das Menü zurück.
- **`LoaderSettingsMenu`**: Größe, Block ändern (nur mit `item.block-choices`), Umbenennen, Löschen mittig in Reihe 2 (4 oder 3 Knöpfe). Löschen mit zweitem Klick innerhalb von `gui.confirm-seconds`, danach zurück dorthin, wo das Loader-Menü herkam. `RenameDialog` öffnet danach wieder das aufrufende Menü.
- **`BlockMenu`**: Auswahl des Blocks in der Welt (`item.material` + `item.block-choices`, Namen per `Component.translatable` = Sprache des Clients). `LoaderManager#setBlock` speichert ihn am Loader (`null` = `item.material`) und tauscht den Block sofort; `validate` tauscht andere Loader-Blöcke still zum gewählten, statt den Loader als fehlend zu behandeln. `Settings` lässt nur feste, ganze Blöcke ohne Schwerkraft und ohne Block-Entity zu.
- **`MyLoadersMenu`**: die eigenen Loader.
- **`AdminMenu`**: Statistik, Info, Filter, Spielerköpfe (auch offline; das Limit offline kennt nur Admin-Limit und Standard, weil Permissions nur bei Online-Spielern abfragbar sind).
- **`SizeMenu`**: Größe eines Loaders - Pfeile N/S/W/O (Links +1, Rechts −1), Mitte = alle Richtungen, oben das Budget des Owners, unten Grenze anzeigen. Mit Dummy nur die Mitte. Ändern über `AdminActions#resize`.
- **`PlayerLoadersMenu`**: Budget (links +10, rechts −10, Shift zurücksetzen), Dummy-Limit (±1), alle löschen, Sperren und die Loader des Spielers.
- Alle Aktionen laufen über `AdminActions`, das jeweils selbst die Permission prüft.

## 7a. Lag-Schutz (`lag/LagMonitor`)
- **Messung:** alle `sample-seconds` pro aktivem (und gesperrtem) Loader über alle Chunks seines Rechtecks; Ereignisse (Redstone, Trichter, Kolben, Spawns) zählt ein Chunk-Index, der bei jeder Messung und nach `resize` (`areaChanged`) neu gebaut wird.
- **Grenzen pro Fläche:** `LagSettings#limitsFor(chunks)`: bis `small-loaders.max-chunks` (9) die festen `small-loaders.limits`, darüber `lag-protection.limits` × `chunks / limits-per-chunks` (`LagLimits#scaled`), mindestens die kleinen (`atLeast`); immer `rounded()` (ganze Zahlen, geprüfte Grenzen ≥ 1). Jede `Activity` eines Loaders ist gegen `limitsFor(loader.area())` gerechnet.
- **Bewertung pro Loader:** `judge(loader, activity, …)` zählt Treffer (`strikes`) pro Loader-ID; nach `lock-after-samples` Messungen über einer Sperrgrenze (nur bei Server-Lag oder ab `force-lock-multiplier`) wird **nur dieser Loader** gesperrt. Auffällig (`conspicuous(loader)`) ist ein Loader über einer Warngrenze seiner Fläche. `lockOffendersBeforePause` sperrt vor einer Server-Pause jeden aktiven Loader über einer Sperrgrenze (außer im Cooldown nach dem Entsperren). Bis 2026-10-29 gab es einen Pool pro Owner (Summe aller Loader) - abgeschafft, weil eine Überschreitung nur den einen Loader betreffen soll.

## 7b. Gestaffelte Abschaltung (`lag/LoadShedder`)
- **Messung:** jede Sekunde `Bukkit.getAverageTickTime()` in einen Ring mit `average-seconds` Plätzen; gehandelt wird erst mit vollem Ring (nach Start und Reload). Der Mittelwert entscheidet die Stufe: `> warn-mspt` Warnung (Team mit `mrchunks.admin.gui` + Discord), endet erst nach `min-minutes` ohne Überschreitung und ohne abgeschaltete Loader.
- **Abschalten:** `> shutdown-mspt` und `step-seconds` seit dem letzten Schritt → `heaviest()`: unter den Loadern, die gerade Chunks halten (`LoaderManager#loading`, nicht gesperrt), der mit der höchsten `Activity#lockRatio` aus dem `LagMonitor` (bei Gleichstand der größere). `LoaderManager#shed(loader, since)` nimmt die Tickets weg und den Dummy (`Dummies#wanted` prüft `shed`), `loader.active()` bleibt `true`. Nichts wird gespeichert - ein Neustart bringt alle zurück.
- **Zurück:** `<= warn-mspt` und `step-seconds` → `restoreOne`: unter den abgeschalteten, deren `min-minutes` um sind, der zuletzt abgeschaltete (`unshed` lädt ihn wieder, wenn er noch an ist; ausgeschaltete oder gelöschte werden nur vergessen). Ist die Funktion aus (`enabled: false`), kommen beim (Re)Start alle zurück.
- **Zu oft:** pro Loader eine Liste der Abschaltzeiten innerhalb `window-minutes`; ist sie länger als `max-shutdowns`, ruft der `LoadShedder` statt einer Abschaltung `LagMonitor#lockRepeated` auf. Die Sperre (`LagLock#shutdowns > 0`, in `data.yml` als `shutdowns`) nutzt eigene Texte (`lag-locked-repeat-*`, Discord `loader-lag-repeat-locked`) und zeigt den schlechtesten Messwert der letzten Messung.
- **Tickets:** `LoaderManager` merkt sich in `covered`, welche Loader gerade in `coverage` stehen. `cover` überspringt pausierte, abgeschaltete und schon abgedeckte Loader, `uncover` nur abgedeckte - sonst würde das Ausschalten eines abgeschalteten Loaders Chunks überlappender Loader freigeben. `LagMonitor` bewertet abgeschaltete Loader nicht (sie halten nichts), ihre letzte Messung bleibt sichtbar. Während einer Server-Pause handelt der `LoadShedder` nicht.
- **Startverzögerung:** `MRChunks` merkt sich den ersten Tick (`serverReadyAt`, per `runTask` aus `onEnable` - läuft erst, wenn der Server fertig gestartet ist). Bis `lag-protection.startup-delay-seconds` danach liefert `startupGrace()` true: `LoadShedder` verwirft seine Messungen (`filled = 0`), `LagMonitor` bewertet nichts (wie bei der Server-Pause), `ServerPause` startet keine Pause. Ein `/mrchunks reload` startet die Verzögerung nicht neu.
- **Status:** `LoaderStatus.SHED` (oranger Beton) zwischen `INACTIVE` und `PAUSED`; Web `status: "shed"` und `shed: {since, back}`, Übersicht `shed`, `mspt`, `msptWarning`; MRadmin-Kachel und Hinweise.
- **Harte Grenze:** jede Sekunde (vor dem vollen Fenster) prüft `check` den Mittelwert der letzten `hard-cap-seconds` Werte; ab `hard-cap-mspt` schaltet `hardCap` alle Loader, die Chunks halten, auf einmal ab (ohne Eintrag in die Abschalt-Liste, also ohne Sperre). Rückkehr wie oben, einzeln.
- **Schalter im Web (gespeichert in `data.yml`):** `LoaderManager#monitoringOff` (`monitoring-off: {by, since}`) schaltet den `LoadShedder` ab (`active()` = Config an und nicht abgeschaltet; `setMonitoring` startet neu, aus = alle zurück). `LoaderManager#adminPause` (`admin-pause: {by, since}`) ist eine zweite Pause neben der Server-Pause: `paused()` = `serverPaused || adminPause`, `ServerPause` sieht nur `serverPaused()`. Aktionen `pauseAll` / `monitoring` mit `on` in `WebData#action` vor der Loader-Suche, Rechte `pause` / `monitoring` (`mrchunks.admin.pause` / `.monitoring`); Übersicht liefert `adminPause` und `monitoring {configured, active, warn, shutdown, hardCap, offBy, offSince}`.

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
| 9 | `/mrchunks limit <spieler> budget 200` | Kopf im Admin-Menü zeigt Budget 200 „(individuell)“, überzählige Loader gehen aus |
| 10 | Erster Start mit squaremap | Log „squaremap eingerichtet: …“, Dimensionsnamen in `tiles/settings.json`, `players.json` leer |
| 11 | Loader-Menü → Größe, Osten +1 bei aktivem Loader | neue Chunk-Spalte geladen, Grenze und Karte folgen, Budget steigt um die Länge |
| 12 | Vergrößern mit vollem Budget | abgelehnt mit Hinweis, Größe unverändert |
| 13 | Dummy auf 3×3 oder Rechteck einschalten | abgelehnt („nur quadratisch ab 5×5“) |

## Einladungen & Ausschluss
- **Daten:** `LoaderManager` hält `loaderMembers` (Loader-ID → Spieler → gültig bis) und `ownerMembers` (Owner → Spieler → gültig bis; gilt für alle Loader des Owners, auch künftige). `Long.MAX_VALUE` = dauerhaft. In `data.yml` unter `members.loaders.<id>.<uuid>` bzw. `members.owners.<owner>.<uuid>`. Abgelaufene Einträge fallen beim Lesen (`members`) und beim Speichern weg, die eines gelöschten Loaders mit dem Loader.
- **Rechte (`AdminActions`):** `canToggle` = `canManage` (Owner\/Admin) oder `isMember`. Umbenennen, Löschen und `canManageInvites` bleiben Owner (`mrchunks.invite`) und Admins (`mrchunks.admin.manage`) vorbehalten. Beim Aktivieren durch Eingeladene gilt das Budget des Owners, der Owner wird benachrichtigt. Eingeladene dürfen das Menü öffnen (`canView`).
- **Menüs:** `InvitePickerMenu` (Köpfe der Online-Spieler, Dauer per Uhr wählbar, „Namen eingeben“ öffnet `InviteDialog`), `MembersMenu` (Liste, Klick = entfernen, bei einem Loader werden die Einladungen für alle Loader grau mit angezeigt), `InviteDialog` (Paper-Dialog mit Namensfeld und Dauer-Auswahl aus `invites.durations`), `SharedLoadersMenu` („Mit mir geteilt“). Einstieg: Button **Mitglieder** im `LoaderMenu`, **Mitglieder für alle Loader** und **Mit mir geteilt** in `MyLoadersMenu`.
- **`mrchunks.exempt` (streng):** blockiert `give`, Setzen, Einladen des Spielers und seine Rechte als Eingeladener. `enforceExempt` löscht beim Join und bei `/mrchunks reload` seine Loader und Items. Permissions sind nur online bekannt, Offline-Spieler werden beim nächsten Join bereinigt.

## 10. Dummy-Spieler (`dummy/*`)
- **Warum Server-Interna:** Paper hat keine API für künstliche Spieler. `NmsDummyBackend` (Paket `dummy.nms`, gebaut mit paperweight-userdev gegen das Dev-Bundle aus `paperApi`) erzeugt einen echten `ServerPlayer` und loggt ihn über `PlayerList.placeNewPlayer` mit einer `FakeConnection` ein (offener `EmbeddedChannel`, verwirft jedes Paket). Abmelden wie bei einem Disconnect: `ServerPlayer#disconnect` + `PlayerList#remove`. `Dummies` lädt das Backend per `Class.forName` - passt es nicht zur Server-Version (`LinkageError` o.ä.), sind Dummies aus und nur das wird gemeldet. **Nach jedem MC-Update hier zuerst prüfen.**
- **Lebenszyklus:** `Loader#dummy` (in `data.yml`) ist der Wunsch des Owners; `Dummies#sync(loader)` sorgt dafür, dass der Dummy genau dann da ist, wenn der Loader aktiv, nicht gesperrt oder pausiert, seine Welt geladen und `dummies.enabled` an ist. `LoaderManager` ruft `sync` bei an/aus, Löschen, Pause, Welt laden und `applyAll`. Gespawnt wird einen Tick später (nicht mitten in fremden Events), ein Wächter alle 5 s holt verschwundene, getötete oder abgesetzte Dummies zurück.
- **Eigenschaften:** Kreativmodus (unverwundbar, Mobs ignorieren ihn, keine Phantome), nie mit `doTick` getickt (bewegt sich nicht, isst nicht, sammelt nichts, keine Statistiken), `setPersistent(false)` (Paper speichert keine Spielerdaten), `setSleepingIgnored(true)`, Simulationsdistanz = Reichweite des Loaders (mind. 2), Sichtdistanz eins mehr, Sende-Distanz 2. Ein Dummy sitzt nur auf Loadern, die `Dummies#fits` erfüllen (zentriertes Quadrat, Reichweite ≥ 2), sonst würde er Chunks außerhalb des Bereichs simulieren. Sitzt auf einem unsichtbaren `ItemDisplay` (`dummies.sit`). UUID = Namens-UUID aus der Loader-ID, Name `[D<nr>]<owner>` (Klammern gibt es in echten Namen nicht, also nie eine Namensgleichheit).
- **Für den Rest unsichtbar:** Join/Quit/Advancement/Tod ohne Meldung, Tab-Liste per `Player#unlistPlayer`, Serverliste (`PaperServerListPingEvent`) und „Server voll“ (`PlayerServerFullCheckEvent`) ohne Dummies. Kick, Teleport, Absetzen, Spielmodus-Wechsel und Schaden durch andere werden abgebrochen. Markierung für andere Plugins: Metadata `NPC` (Citizens-Konvention) und PDC-Schlüssel `mrchunks:dummy`; MRsleep und MRpvp prüfen beides (jeweils Klasse `Npcs`), MRadmin fragt über die Modul-API (`MRadminModule#isNpc`).
- **Skin:** Eigenschaft `textures` des Owners - beim Join des Owners aktualisiert, sonst einmal per `PlayerProfile#update` geholt, gespeichert in `skins.yml`.

## 11. Verknüpfte Accounts
- **Daten:** `LoaderManager.links` (Zweit → Haupt, keine Ketten). `main(uuid)` liefert den Haupt-Account, `group(uuid)` alle Accounts. Loader gehören immer dem Haupt-Account: `AdminActions.accountOf(player)` ist überall maßgeblich, wo früher die UUID des Spielers stand (Owner-Prüfung, Setzen, Zählen, Einladungen, Menüs). Limits werden immer für den Haupt-Account berechnet, `groupBan` prüft die ganze Gruppe, `notifyOwner` erreicht alle Online-Accounts der Gruppe.
- **Ablauf:** `LinkRequests#request` (Haupt-Account fragt) → Dialog und Chat-Buttons beim anderen (läuft nach `linked-accounts.request-seconds` ab) → `AdminActions#link` prüft alles erneut (`linkProblem`) → `LoaderManager#link` übernimmt Loader (mit neuen Nummern) und Einladungen für alle Loader und entfernt Einladungen innerhalb der Gruppe.
- **Limit offline:** Weil der Zweit-Account das Budget des (vielleicht offline) Haupt-Accounts nutzt, merkt sich `limit()` Permission-Limits in `known-limits` und verwendet sie, solange der Account offline ist.

## 12. MRadmin (`api/MRadminLink`, `api/MRadminModule`)
- **`MRadminLink`** hat keine MRadmin-Typen und fängt `LinkageError` ab - ohne MRadmin läuft MRchunks normal. **`MRadminModule`** wird nur geladen, wenn MRadmin läuft, und baut gegen `mradminApi` aus `gradle.properties` (zur Zeit `api-1.2.0`).
- **Was MRchunks liefert:** Dashboard-Knopf und Menüs (`summary`, `openMenu`, `playerSummary`, `openPlayerMenu`), auffällige Loader im Serverzustand (`health`, `openHealth`), Web-Daten und -Aktionen (`WebData`), Hinweise und Markierungen (`alerts`, `flags`), seit API 3 auch die Startseiten-Kachel (`tile`), die Push-Art `chunks-locked` (`pushKinds`), den Namen der Ticket-Kategorie `chunkloader-lock` (`reportCategories`), die geladenen Chunks (`loadedChunks`) und die Dummies als NPCs (`isNpc`). Die Texte dafür kommen aus den Sprachdateien in `server-language`.
- **Tickets:** `reportLock` / `resolveLock` bei Lag-Sperre und Entsperren.
- Ein älteres MRadmin (API < 3) ruft die neuen Methoden nie auf; die Web-Anfrage `chunks` bleibt für solche Versionen.
