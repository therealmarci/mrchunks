# Changelog

Alle wichtigen Änderungen an MRchunks. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

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
