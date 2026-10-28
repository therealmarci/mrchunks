# Changelog

Alle wichtigen Änderungen an MRchunks. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

> [!WARNING]
> **Neue Permission-Gruppen:** Das Paket `mrchunks.admin` gibt es nicht mehr. Stattdessen gibt es in allen MRtechnology-Plugins dieselben Gruppen `<plugin>.player` und `<plugin>.groups.support|staff|admin`. Bitte die LuckPerms-Gruppen umstellen, z.B. `/lp group admin permission unset mrchunks.admin` und `/lp group admin permission set mrchunks.groups.admin true`.

### Neu
- **Permission-Gruppen:** `mrchunks.groups.support` (Admin-Menü, Status aller Loader, Teleport), `mrchunks.groups.staff` (+ fremde Loader an/aus, Einladungen verwalten, Accounts verknüpfen und lösen) und `mrchunks.groups.admin` (alles). Jede Gruppe enthält die darunter und `mrchunks.player`.
- **Neue Einzel-Rechte:** `mrchunks.admin.toggle` (fremde Loader an/aus), `mrchunks.admin.invites` (Einladungen fremder Loader), `mrchunks.admin.link` (`/mrchunks link`, `unlink`). `mrchunks.admin.manage` enthält sie weiterhin.
- **Knopf im MRadmin-Dashboard:** öffnet das Admin-Menü jetzt direkt über die Modul-API (nicht mehr über den Befehl).

### Entfernt
- **Paket `mrchunks.admin`:** ersetzt durch `mrchunks.groups.admin`.

### Behoben & geändert
- **`/mrchunks version` nur noch mit Permission:** `mrchunks.version` (in `mrchunks.player`) - bisher durfte das jeder.

## [0.7.0] - 2026-10-27

### Neu
- **Accounts in MRadmin verknüpfen:** Auf der Spielerseite der MRadmin-Weboberfläche lassen sich Accounts jetzt auch verknüpfen (bisher nur lösen) - per Spielername als Zweit- oder Haupt-Account, ohne Zustimmung der Spieler wie `/mrchunks link`. Permission `mrchunks.admin.manage`.
- **Eigener Block pro Chunkloader:** Im Loader-Menü wählt der Besitzer über **Block ändern**, als welcher Block der Loader in der Welt steht (z.B. Diamant-, Gold-, Netherit- oder Amethystblock). Menüs und das Item bleiben beim Smaragdblock. Die Auswahl steht in `item.block-choices` (leere Liste = keine Auswahl; nur feste, ganze Blöcke ohne Schwerkraft und ohne Inventar), Permission `mrchunks.block` (in `mrchunks.player` enthalten). Admins mit `mrchunks.admin.manage` dürfen den Block fremder Loader ändern.
- **Jeder Spieler in seiner Sprache:** Chat, Menüs, Dialoge und der Chunkloader-Gegenstand richten sich jetzt nach der Sprache, die der Spieler in Minecraft eingestellt hat (`language: auto`, neuer Standard): Deutsch (auch Österreich/Schweiz) bekommt Deutsch, Englisch bekommt Englisch, alle anderen `fallback-language` (Standard `en`). `language: de` oder `en` stellt weiterhin eine Sprache für alle ein.
- **Server-Sprache (`server-language`):** Discord-Logs, Beschriftungen auf der Webkarte und Tickets in MRadmin kommen in dieser Sprache (Standard `en`).
- **Automatische Umstellung:** Bestehende Configs bekommen `language: auto`; eine bisher fest eingestellte Sprache wird zur `server-language`, damit z.B. die Discord-Logs eines deutschen Servers deutsch bleiben.
- **Benachrichtigungen in der Sprache des Empfängers:** Meldungen an Owner, Eingeladene und das Team (Lag-Sperre, Einladungen, Limits, Sperren, Umbenennen) bekommt jeder in seiner Sprache - auch wenn ein Admin mit anderer Sprache sie auslöst.

### Behoben & geändert
- **Menütitel in der falschen Sprache:** „Meine Chunkloader“ (über `/chunkloader`), das Loader-Menü per Rechtsklick auf den Block und die Menüs aus MRadmin hatten den Titel in der Server-Sprache statt in der Sprache des Spielers. Jetzt ist auch der Titel in der Sprache des Spielers.
- **Menüs symmetrischer:** In „Meine Chunkloader“ sitzt die Kette (verknüpfte Accounts) ein Feld weiter links, gegenüber dem Schließen-Knopf; die Webkarte rückt in die Mitte der unteren Leiste. Im Loader-Menü steht die Grenze in der Mitte der zweiten Reihe, links davon (mit Abstand) An/Aus, Mitglieder und Dummy, rechts Block ändern, Umbenennen und Löschen.
- **Update-Hinweis bei `/mrchunks version` doppelt:** Fand die Abfrage eine neue Version, kam zuerst der Admin-Hinweis „Für MRchunks gibt es ein Update“ und direkt danach „Neue Version verfügbar“. Jetzt kommt nur noch die Antwort auf den Befehl. Die Download-Links führen auf die Website (https://mrtechnology.marcelr.at/download/mrchunks/).
- **Config und plugin.yml auf Englisch:** Alle Kommentare in der `config.yml` und die Beschreibungen der Befehle und Permissions in der `plugin.yml` sind jetzt englisch. Bestehende Configs bekommen die neuen Kommentare beim nächsten Start automatisch (config-version 10, Sicherung als `config.v<alt>.backup.yml`), alle Einstellungen bleiben erhalten.
- **Config-Kommentare korrigiert:** Sie nennen jetzt die richtigen Befehle `/mrchunks limit <spieler> place|active <anzahl>` (statt des nicht mehr vorhandenen `/mrchunks activelimit`).

## [0.6.0] - 2026-10-27

### Geändert – Lizenz, Doku, Website
- **MRtechnology Lizenz 1.0:** Eine einheitliche Lizenz für alle MRtechnology-Projekte. Ausdrücklich erlaubt ist jetzt auch die gewerbliche Nutzung, Weiterveröffentlichen und Verkaufen bleiben verboten, die Nutzung erfolgt auf eigenes Risiko.
- **README überarbeitet:** Schnellstart, Auf einen Blick, Häufige Fragen, Kurzlinks zur Website (Download, Anleitung, Fehler melden).
- **Links zur Website** [mrtechnology.marcelr.at](https://mrtechnology.marcelr.at): Update-Hinweise in Konsole und Spiel führen zur Download-Seite, `/mrchunks version` zeigt „Website | GitHub“ (statt des Modrinth-Links), `website` in der plugin.yml zeigt auf die Anleitung. Der Text `version-links` wird in den mitgelieferten Sprachdateien automatisch erneuert.

### Geändert
- **MRadmin über die Modul-API:** MRchunks meldet sich als Modul bei MRadmin an (statt der alten `MRadminHook`-Klasse
  und des `LoaderLockEvent`). Braucht MRadmin mit Modul-API; ohne MRadmin ändert sich nichts. Betreff und Notiz der
  Tickets bei Lag-Sperren stehen jetzt in MRchunks' Sprachdatei (`report-lock-subject`, `report-lock-lifted`).
- **Verbindung zu anderen MR-Plugins nur noch über MRadmin:** Dass in der Arena-Welt von MRpvp keine Chunkloader
  gesetzt werden können, funktioniert jetzt nur mit MRadmin (vorher eigene Schnittstelle zu MRpvp). Die Hinweise
  auf der Startseite von MRadmins Weboberfläche (pausiert, gesperrt, auffällig) liefert MRchunks selbst
  (`web-alert-*` in der Sprachdatei). Die README hat einen Abschnitt „Zusammenarbeit mit anderen Plugins“.

### Neu
- **Dummy-Spieler** (#13): Im Menü eines Chunkloaders lässt sich ein Dummy einschalten - ein künstlicher Spieler, der
  (mit dem Skin des Owners) auf dem Block sitzt, solange der Loader aktiv ist. Er zählt als Spieler in der Nähe: Mobs
  bleiben aktiv und laufen z.B. wieder durch Portale (#12), Spawning und Zufalls-Ticks laufen wie mit einem Spieler.
  Der Dummy ist unverwundbar, wird von Mobs ignoriert, steht nicht in der Tab-Liste, zählt nicht zur Spielerzahl und
  belegt keinen Platz. Andere Plugins erkennen ihn an der Metadata `NPC` bzw. am Schlüssel `mrchunks:dummy`.
  Standardmäßig aus (`dummies.enabled`); Limit `dummies.default-limit`, `mrchunks.dummies.<n>`,
  `mrchunks.dummies.unlimited`, `/mrchunks limit <spieler> dummy <n>`. Admins: `/mrchunks dummy <spieler> <nr>`.
  Nutzt Server-Interna von Paper - passt die Version nicht, bleiben Dummies aus, der Rest läuft normal.
- **Verknüpfte Accounts** (#11): Unter `/chunkloader` → Verknüpfte Accounts verknüpft ein Spieler einen anderen
  Account mit dem eigenen, der andere stimmt in einem Dialog zu. Beide teilen sich danach Chunkloader, das Limit des
  Haupt-Accounts, Einladungen und Dummies; eine Sperre gilt für beide. Lösen jederzeit im Menü oder per
  `/mrchunks unlink <spieler>`, Admins verknüpfen direkt mit `/mrchunks link <haupt> <zweit>`. Einstellungen unter
  `linked-accounts`, Permission `mrchunks.link` (in `mrchunks.player`).
- **Limits offline:** Limits aus Permissions merkt sich MRchunks jetzt vom letzten Mal online, statt für Offline-Spieler
  auf den Standard zurückzufallen.
- **MRadmin:** Schnittstelle Version 5 - Dummy an/aus, Dummy-Limit, verknüpfte Accounts und „Verknüpfung lösen“ in der
  Weboberfläche.
- Config-Version 9 (`linked-accounts`, `dummies`, drei Discord-Events) und lang-version 11, beides wird automatisch
  ergänzt.

### Behoben
- Menüs mit Spielerköpfen fragten jede Sekunde erneut bei Mojang nach dem Skin (HTTP 429 im Log, besonders im
  Offline-Mode). Köpfe werden jetzt einmal nachgeschlagen und 10 Minuten zwischengespeichert.

## [0.5.3] - 2026-10-23

### Neu
- **MRpvp:** In der Arena-Welt von MRpvp lassen sich keine Chunkloader setzen (wie in `loaders.disabled-worlds`) -
  MRpvp baut die Arenen dort selbst auf und ab.

## [0.5.2] - 2026-10-21

### Behoben
- Update-Hinweis erschien doppelt im Chat, wenn man kurz nach einem Serverstart joint (Hinweis beim Betreten und
  erste Update-Prüfung). Jetzt höchstens einmal pro Minute.

## [0.5.1] - 2026-10-20
Für Minecraft **26.3** (`mc26.3-v0.5.1`).

### Behoben
- **Config-Update:** `lag-protection.force-lock-multiplier` wird jetzt auch in bestehende `config.yml` eingetragen
  (Config-Version 8, die alte Datei bleibt als `config.v7.backup.yml` erhalten). In 0.5.0 fehlte der Versionssprung,
  der Standardwert 2 galt aber trotzdem.

## [0.5.0] - 2026-10-20
Für Minecraft **26.3** (`mc26.3-v0.5.0`).

### Neu
- **Bedingungslose Lag-Sperre:** `lag-protection.force-lock-multiplier` (Standard 2) - ab dem so vielfachen einer
  Sperrgrenze wird ein Loader auch ohne messbaren Server-Lag gesperrt (nach `lock-after-samples` Messungen).
  `1` = schon ab der Sperrgrenze, `0` = aus (Verhalten wie bisher).

## [0.4.0] - 2026-10-20
Für Minecraft **26.3** (`mc26.3-v0.4.0`).

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

### Behoben
- Auf dem Chunkloader-Block spawnen keine Mobs mehr (natürlich, Spawner, Patrouillen, Raids, Portale ...). Was Spieler
  selbst auslösen (Spawn-Eier, Befehle, Züchten, Golems bauen), bleibt möglich.

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
