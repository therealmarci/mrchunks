# Changelog

Alle wichtigen Änderungen an MRchunks. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]
### Neu
- **Namen & Nummern:** Chunkloader heißen nicht mehr nach einer fortlaufenden globalen Nummer, sondern bekommen pro
  Spieler die kleinste freie Nummer („Chunkloader 9“ - wird Nr. 9 gelöscht, bekommt der nächste wieder die 9).
  Über **Umbenennen** im Menü (Dialog mit Textfeld) gibt man ihnen eigene Namen, die auch auf der Webkarte und in
  Discord erscheinen. Neues Discord-Event `loader-renamed`. Bestehende Loader werden automatisch durchnummeriert.
- **Neue Grenzanzeige:** leuchtende Linien an jedem Chunk der Außenkante, durch Blöcke sichtbar, nur für den
  Betrachter. Sie folgt der Höhe, lässt sich im Menü ausblenden und verschwindet beim Löschen des Loaders.
  Neue Optionen unter `border` (Farbe, Block, Dicke, Höhe), `particle` und `spacing` entfallen.
- **Einladungen:** Owner laden andere Spieler ein, ihre Chunkloader an- und auszuschalten - für einen Loader
  (Menü → Mitglieder) oder für alle (`/chunkloader list` → Mitglieder für alle Loader), befristet oder dauerhaft.
  Eingeladene sehen die Loader unter „Mit mir geteilt“ und dürfen nur an/aus schalten; löschen kann nur der Owner.
  Zählt bei niemandem zum Limit, das Aktiv-Limit des Owners gilt. Neue Optionen unter `invites`, Permission
  `mrchunks.invite`, Discord-Events `loader-invite` und `loader-uninvite`.
- **`mrchunks.exempt`:** schließt Spieler (z.B. Zweit-Accounts) komplett vom Chunkloading aus. Vorhandene Loader und
  Items werden beim Join bzw. Reload gelöscht (Discord-Event `exempt-cleared`). Gilt streng, auch für `*`.
- **Überschneidungen** wählbar über `loaders.overlap`: `allow`, `warn` (Standard: Hinweis + Grenze), `deny-own`, `deny-all`.
- **Adresse der Webkarte** über `squaremap.address` (Domain/FQDN, IP oder komplette URL), automatisch mit squaremaps
  `web-address`, `server-ip` oder der öffentlichen IP. `squaremap.reverse-proxy: true` für Links ohne Port per https,
  auch Unterpfade hinter einem Proxy funktionieren. Ersetzt `squaremap.public-url` (wird automatisch übernommen).
- Fehlende Loader-Blöcke (z.B. nach `/fill` oder `/setblock`) werden zusätzlich geprüft, sobald ihr Chunk lädt,
  also auch bei deaktivierten Loadern.
- **Sprachdateien:** Alle Texte stehen in `plugins/MRchunks/lang/<sprache>.yml`, mitgeliefert werden Deutsch (`de`)
  und Englisch (`en`). In der `config.yml` wird nur noch `language` gesetzt. Dazu gehören auch Item-Name und -Lore,
  die squaremap-Texte (Ebenen, Tooltips, Dimensionsnamen), Discord-Texte, der Sperrgrund und das Datumsformat.
  Fehlende Texte werden bei Updates automatisch ergänzt.

### Geändert
- `/chunkloader info` ist jetzt eine reine Befehlshilfe; die eigenen Loader zeigt `/chunkloader list`.
  Die Permission `mrchunks.info` entfällt.
- Admin-Befehle `tp`, `toggle` und `delete` nehmen `<spieler> <nr>` statt einer internen ID.
- Config-Version 4.
- **Versionsschema mit Minecraft-Version:** `mc26.3-v0.2.0` (Git-Tag, JAR-Name, Modrinth), im Spiel `0.2.0+mc26.3`.
  Mehrere Minecraft-Versionen lassen sich parallel versorgen (Wartungs-Branches `mc/<version>`, siehe
  `docs/RELEASES.md`). Der Update-Checker meldet nur Versionen für dieselbe Minecraft-Version.
- **Konsole immer auf Englisch:** Log-Ausgaben und Antworten auf Konsolenbefehle, unabhängig von `language`.

### Behoben
- Die Grenze blieb sichtbar, nachdem der Chunkloader gelöscht wurde.
- Die Grenze erscheint beim Öffnen des Menüs nicht mehr automatisch (`border.show-on-open` jetzt standardmäßig `false`).
- Kaputtes „Ã¼“ in der Meldung, wenn ein Loader wegen des Aktiv-Limits deaktiviert gesetzt wird.


## [0.1.0] - 2026-10-18
### Neu
- Chunkloader-Blöcke (Standard: Emeraldblock), die einen 7×7-Chunk-Bereich dauerhaft geladen halten
  (Radius einstellbar), in allen Dimensionen und auch wenn der Owner offline ist.
- Wahlweise Plugin-Tickets oder Vanilla-Forceload (`loaders.load-method`). Überschneidende Bereiche werden pro Chunk
  mitgezählt, damit beim Entfernen eines Loaders keine Chunks verloren gehen.
- `/chunkloader give|remove|list|info|map` für Spieler.
- **Zwei Limits pro Spieler:** setzbare Loader und gleichzeitig aktive Loader (z.B. 15 setzen, 3 aktiv),
  jeweils über Config, Permission (`mrchunks.limit.<n>` / `mrchunks.active.<n>`) oder Admin-Befehl
  (`/mrchunks limit` / `/mrchunks activelimit`). Senkt ein Admin das Aktiv-Limit, werden die neuesten Loader
  automatisch deaktiviert.
- Unzerstörbare Blöcke: kein Abbauen, keine Explosionen, Kolben, Wither, Endermen, kein Feuer. Fehlende Blöcke
  (z.B. durch WorldEdit) werden automatisch wiederhergestellt. Loader-Items lassen sich nicht zu Smaragden craften.
- Menü per Rechtsklick auf den Block: Infos, Grenzanzeige mit Partikeln, an/aus, löschen (mit Bestätigung).
- Admin-Menü (`/mrchunks gui`): alle Spieler mit Loadern, beide Limits +/−, Sperren, alle Loader löschen, Teleport.
- Sperren (`/mrchunks ban`): löscht alle Loader des Spielers und verhindert neue.
- Anbindung an **squaremap**: Die Bereiche erscheinen als eigene Ebene „Chunkloader“ auf der echten Webkarte
  (Fläche, Symbol, Tooltip). Die Ebene aktualisiert sich bei jeder Änderung sofort.
- Chunkraster auf der squaremap mit Regionsgrenzen und Chunk-Koordinaten, als Ebene „Chunkraster“ im Ebenen-Menü
  (`squaremap.chunk-grid`).
- `/chunkloader map` schickt Spielern einen anklickbaren Link zur Webkarte (`squaremap.public-url`).
- squaremap wird automatisch erkannt und einmalig eingerichtet: 5-s-Aktualisierung, keine Spieler auf der Karte,
  Welt-Auswahl mit „Overworld“, „Nether“ und „End“, Nether von innen. Erneut mit `/mrchunks map setup`.
  Ohne squaremap gibt es keine Webkarte.
- Discord-Logs, Update-Prüfung über GitHub Releases, `/mrchunks version`, Config-Migration (UTF-8 mit BOM).
