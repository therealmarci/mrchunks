# Changelog

Alle wichtigen Änderungen an MRchunks. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

## [0.2.0] - 2026-10-18
Zweite Testversion für Minecraft **26.3** (`mc26.3-v0.2.0`) mit den Rückmeldungen aus dem Alpha-Test.

### Neu
- **Einladen per Klick:** „Spieler einladen“ zeigt die Köpfe der Online-Spieler, die Dauer wird per Uhr gewählt.
  Offline-Spieler weiterhin über „Namen eingeben“ (#7).
- `/chunkloader version` für alle Spieler, auch ohne Rechte (#8).
- Sammel-Permission `mrchunks.player` (use, map, view, invite), z.B. für die Default-Gruppe.

### Geändert
- **Spieler-Rechte nur noch nach Zuweisung:** `mrchunks.use`, `map`, `view` und `invite` haben standardmäßig nur
  noch OPs. Ohne Rechte funktioniert nur `/chunkloader version`; die Befehlshilfe zeigt nur erlaubte Befehle (#6).
  ⚠️ Nach dem Update in LuckPerms z.B. `/lp group default permission set mrchunks.player true` setzen.
- **Admin-Befehle zusammengefasst:** `/mrchunks limit <spieler> <place|active> <anzahl|reset>` ersetzt `limit` und
  `activelimit`; `/mrchunks list` entfällt (das Admin-Menü zeigt alles), ebenso die Permission `mrchunks.admin.list` (#9).
- **Neue Anordnung im Chunkloader-Menü:** An/Aus, Mitglieder, Grenze, Umbenennen, Löschen; Teleport nur für Admins
  unten rechts (#10).

## [0.1.0] - 2026-10-18
**Erste Testversion (Alpha)** für Minecraft **26.3** (`mc26.3-v0.1.0`).

### Chunkloader
- Chunkloader-Blöcke (Standard: Emeraldblock), die einen 7×7-Chunk-Bereich dauerhaft geladen halten
  (Radius einstellbar), in allen Dimensionen und auch wenn der Owner offline ist.
- Wahlweise Plugin-Tickets oder Vanilla-Forceload (`loaders.load-method`). Jeder Chunk wird nur einmal geladen,
  auch wenn mehrere Loader ihn abdecken; beim Deaktivieren oder Löschen bleiben Chunks geladen, die ein anderer
  aktiver Loader abdeckt.
- **Überschneidungen** wählbar über `loaders.overlap`: `allow`, `warn` (Standard: Hinweis + Grenze), `deny-own`, `deny-all`.
- **Namen & Nummern:** Pro Spieler die kleinste freie Nummer („Chunkloader 9“ - wird Nr. 9 gelöscht, bekommt der
  nächste wieder die 9). Über **Umbenennen** im Menü (Dialog mit Textfeld) eigene Namen, die auch auf der Webkarte
  und in Discord erscheinen.
- **Zwei Limits pro Spieler:** setzbare und gleichzeitig aktive Loader (z.B. 15 setzen, 3 aktiv), jeweils über
  Config, Permission (`mrchunks.limit.<n>` / `mrchunks.active.<n>`) oder Admin-Befehl (`/mrchunks limit` /
  `/mrchunks activelimit`). Senkt ein Admin das Aktiv-Limit, werden die neuesten Loader automatisch deaktiviert.
- **Unzerstörbar:** kein Abbauen, keine Explosionen, Kolben, Wither, Endermen, kein Feuer. Fehlende Blöcke
  (z.B. durch `/fill`, `/setblock` oder WorldEdit) werden regelmäßig und beim Laden ihres Chunks wiederhergestellt.
  Loader-Items lassen sich nicht zu Smaragden craften.

### Bedienung
- Menü per Rechtsklick auf den Block: Infos, umbenennen, Mitglieder, Grenze anzeigen/ausblenden, an/aus,
  löschen (mit Bestätigung).
- **Grenzanzeige:** leuchtende Linien an jedem Chunk der Außenkante, durch Blöcke sichtbar, nur für den Betrachter,
  folgt der Höhe, verschwindet nach Ablauf, per Button oder beim Löschen des Loaders.
- **Einladungen:** Owner laden andere Spieler ein, ihre Chunkloader an- und auszuschalten - für einen Loader oder
  für alle (auch künftige), befristet oder dauerhaft. Eingeladene sehen die Loader unter „Mit mir geteilt“ und
  dürfen nur an/aus schalten; löschen kann nur der Owner. Zählt bei niemandem zum Limit.
- Befehle für Spieler: `/chunkloader give|remove|list|map|info` (`info` = Befehlshilfe).
- Admin-Menü (`/mrchunks gui`): alle Spieler mit Loadern, beide Limits +/−, Sperren, alle Loader löschen, Teleport.
  Admin-Befehle sprechen Loader mit `<spieler> <nr>` an.
- Sperren (`/mrchunks ban`): löscht alle Loader des Spielers und verhindert neue.
- **`mrchunks.exempt`:** schließt Spieler (z.B. Zweit-Accounts) komplett vom Chunkloading aus; vorhandene Loader und
  Items werden beim Join bzw. Reload gelöscht. Gilt streng, auch für `*`.

### Webkarte (squaremap)
- Die Bereiche erscheinen als eigene Ebene „Chunkloader“ auf der echten Webkarte (Fläche, Symbol, Name, Tooltip)
  und aktualisieren sich bei jeder Änderung sofort.
- Chunkraster mit Regionsgrenzen und Chunk-Koordinaten als Ebene „Chunkraster“ im Ebenen-Menü.
- squaremap wird automatisch erkannt und einmalig eingerichtet: 5-s-Aktualisierung, keine Spieler auf der Karte,
  Welt-Auswahl mit „Overworld“, „Nether“ und „End“, Nether von innen. Erneut mit `/mrchunks map setup`.
- `/chunkloader map` schickt einen anklickbaren Link. Adresse über `squaremap.address` (Domain/FQDN, IP oder
  komplette URL), sonst automatisch; `squaremap.reverse-proxy: true` für https ohne Port, auch mit Unterpfad.

### Allgemein
- **Sprachdateien** `lang/de.yml` und `lang/en.yml` für alle Texte, in der Config nur `language`. Eigene Sprachen
  möglich, neue und geänderte Texte werden bei Updates automatisch ergänzt.
- Konsole immer auf Englisch.
- Discord-Logs für alle Aktionen, Update-Prüfung über GitHub Releases (nur Versionen derselben Minecraft-Version),
  `/mrchunks version`, automatische Config-Migration (UTF-8 mit BOM).
