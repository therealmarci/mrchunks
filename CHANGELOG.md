# Changelog

Alle wichtigen Änderungen an MRchunks. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Server-Pause sperrt den Verursacher:** Direkt bevor alle Loader pausieren, wird jeder aktive Loader über einer
  Sperrgrenze sofort gesperrt. Vorher konnte die Pause ihn verstecken (Chunks frei, Lag weg, Pause vorbei, von vorn).
- **MRadmin-Weboberfläche:** Schnittstelle Version 4 (`MRadminHook.web`) liefert Chunkloader, Status, Messwerte,
  Mitglieder und die Zahl der geladenen Chunks (für den Health-Verlauf von MRadmin) als Daten und nimmt Aktionen an
  (ein-/ausschalten, entsperren, löschen); dazu pro Spieler Loader, Limits, Sperre und Einladungen
  (`player`, `flags`) und Spieler-Aktionen (Limits setzen, sperren, entsperren, alle löschen) - der Owner wird wie ingame benachrichtigt, Discord- und Server-Log inklusive.
- **Server-Pause:** Liegt die TPS des Servers 30 Sekunden lang unter 15, geben alle Chunkloader ihre Chunks frei,
  egal woher der Lag kommt. Sobald die TPS 60 Sekunden lang wieder bei mindestens 18 liegt, laden sie automatisch
  wieder. An/aus der Loader bleibt dabei unverändert. Owner aktiver Loader und Admins werden informiert, dazu
  Discord-Logs (`server-paused`, `server-resumed`). Spieler können die Pause nicht umgehen. Auf der Webkarte sind
  pausierte Loader orange (`squaremap.paused-color`), in MRadmin erscheint die Pause auf dem MRchunks-Knopf und im
  Serverzustand. Alles einstellbar unter `server-pause` (Config-Version 6, wird automatisch ergänzt).
- **Ticket bei Lag-Sperre (mit MRadmin):** Wird ein Loader gesperrt, öffnet MRadmin automatisch ein Ticket für den
  Owner mit allen Details der Sperre und schickt ihm den Link im Chat. Nur das Team kann es schließen und den
  Loader über „Chunkloader-Menü“ im Ticket entsperren. Ohne MRadmin ändert sich nichts.
- **Status-Blöcke in allen Menüs** (`/chunkloader`, `/mrchunks gui`, MRadmin): Smaragdblock = OK, grau = aus,
  Magmablock = über der Warnschwelle (mit Messwert in der Beschreibung), Redstone-Block = gesperrt,
  Spinnennetz = Server-Pause.

### Geändert
- **Sperr-Details ohne eigene Ortszeile:** Das Ticket in MRadmin zeigt den Ort schon selbst, er stand dadurch doppelt da.
  Die Sprachdateien werden automatisch aktualisiert (lang-version 10).
- **Kein 30-Minuten-Schutz mehr nach dem Entsperren** (`unlock-grace` entfällt): Eine neue Sperre braucht wie immer
  `lock-after-samples` Messungen hintereinander über der Grenze (Standard 3 x 30 s). Die Config wird automatisch
  auf Version 7 aktualisiert.
- **Gesperrte Loader werden weiter gemessen**, solange jemand ihren Bereich lädt (z.B. ein Admin, der daneben
  steht). So sieht man vor dem Entsperren, ob sich die Lage beruhigt hat. Die Aktivität im Menü zeigt dazu, wie alt
  die Messung ist und wie viele Chunks des Bereichs geladen waren. Gesperrt wird dabei nichts.
- **Spieler haben nur noch `/chunkloader`.** Der Befehl öffnet das Menü mit den eigenen Loadern. Die bisherigen
  Unterbefehle sind Knöpfe in der unteren Leiste: **Chunkloader holen** (`give`), **Chunkloader zurückgeben**
  (`remove`) und **Webkarte** (`map`). `list` ist das Menü selbst, `info` und `version` entfallen (Version für
  Admins: `/mrchunks version`). Die Admin-Befehle unter `/mrchunks` bleiben unverändert.

## [0.3.0] - 2026-10-19
Dritte Testversion für Minecraft **26.3** (`mc26.3-v0.3.0`). Update von 0.2.0: JAR austauschen und neu starten,
Config und Texte werden automatisch ergänzt (Backup `config.v4.backup.yml`).

> Der Lag-Schutz ist nach dem Update **eingeschaltet**. Die Standardgrenzen sind großzügig und gesperrt wird nur,
> während der Server laggt - bei Bedarf unter `lag-protection` anpassen.

### Neu
- **Lag-Schutz:** MRchunks misst im Bereich jedes aktiven Loaders Entities, Items, Redstone, Trichter-Transfers,
  Kolben und Spawns. Auffällige Loader sieht man in `/mrchunks lag` (und in MRadmin); liegt ein Loader mehrmals
  hintereinander über der Sperrgrenze, während der Server laggt, wird er deaktiviert und gesperrt. Der Owner wird
  informiert und sieht den Loader mit 🔒 in seiner Liste; nur Admins können die Sperre aufheben (Menü oder
  `/mrchunks unlock`). Alles einstellbar unter `lag-protection`.
- **Aktivität im Loader-Menü:** die letzte Messung mit Warn- und Sperrgrenze.
- **MRadmin-Anbindung:** Im Admin-Kompass von MRadmin zeigt der MRchunks-Knopf live Chunkloader, aktive Loader,
  geladene Chunks und Sperren; im Spieler-Menü von MRadmin gibt es die Loader-Infos des Spielers und einen Knopf zu
  seinen Chunkloadern (Zurück führt wieder zu MRadmin). Im Serverzustand von MRadmin erscheinen auffällige und
  gesperrte Chunkloader statt der Chunks mit den meisten Entities. Ohne MRadmin ändert sich nichts.

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

### Behoben
- Zu lange Fenstertitel ragten aus dem Menü (z.B. bei langen Loader- oder Spielernamen). Die Titel sind kürzer und
  werden bei Bedarf automatisch mit „…“ gekürzt.

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
