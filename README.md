# MRchunks

Chunkloader-Plugin für **Paper 26.3** (Java 25).

> 🧪 **Pre-Release (0.x):** MRchunks befindet sich in der Testphase. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern. Fehler bitte als [Issue](https://github.com/therealmarci/mrchunks/issues) melden.

- **Chunkloader-Blöcke** (Aussehen: Emeraldblock): Einmal platziert, bleibt ein **7×7-Chunk-Bereich** dauerhaft geladen, auch wenn der Spieler offline ist. Das funktioniert in **allen Dimensionen**.
- **Zwei Limits pro Spieler** (setzbar / gleichzeitig aktiv) aus der Config, mehr per Permission oder Admin-Befehl.
- **Mitspieler einladen**, die Loader an- und auszuschalten, für einen oder alle Loader, befristet oder dauerhaft.
- **Unzerstörbar:** kein Abbauen, keine Explosionen, Kolben, Wither oder Endermen.
- **Menü per Rechtsklick:** Infos, umbenennen, Mitglieder, Grenze anzeigen, an/aus, löschen.
- **Admin-Menü:** alle Spieler und ihre Chunkloader, mit Teleport, an/aus, löschen, Limit und Sperre.
- **Webkarte mit echtem Gelände** über [squaremap](https://modrinth.com/plugin/squaremap): alle Bereiche, Chunkraster, Overworld/Nether/End. squaremap wird automatisch erkannt und eingerichtet.
- **Discord-Logs**, **Update-Prüfung**, alles einstellbar in einer `config.yml`, eine Permission für jede Funktion.
- **Sprachdateien** für alle Texte: Deutsch und Englisch mitgeliefert, eigene Sprachen möglich.

---

## Installation
1. Die JAR für deine Minecraft-Version in den Ordner `plugins/` legen, z.B. `MRchunks-mc26.3-v0.2.0.jar` für Minecraft 26.3, und den Server starten.
2. `plugins/MRchunks/config.yml` anpassen und `/mrchunks reload` ausführen.

Bei Updates wird die Config automatisch migriert: Deine Werte bleiben erhalten, und eine Sicherung `config.v<N>.backup.yml` wird angelegt.

### Sprache & Texte
In der `config.yml` steht nur die Sprache: **`language: de`** (Deutsch) oder **`language: en`** (English).
Alle Texte stehen in `plugins/MRchunks/lang/<sprache>.yml`: Chat, Menüs, Item-Name und -Beschreibung,
Texte auf der Webkarte (Ebenen-Namen, Tooltips, Dimensionsnamen), Discord-Logs und das Datumsformat.

- Texte in der Sprachdatei ändern und `/mrchunks reload` ausführen. Ein leerer Text (`""`) schaltet die Nachricht ab.
- **Eigene Sprache:** `lang/en.yml` kopieren, z.B. als `lang/fr.yml`, übersetzen und `language: fr` setzen.
- Neue Texte aus Updates werden automatisch in deiner Sprachdatei ergänzt, deine Änderungen bleiben erhalten.

---

## So funktioniert's
1. **`/chunkloader give`** legt einen Chunkloader ins Inventar, solange dein Limit nicht erreicht ist. Es gibt zwei Limits: wie viele du **setzen** darfst und wie viele davon **gleichzeitig aktiv** sein dürfen (siehe [Limits](#limits-setzen-und-gleichzeitig-aktiv)).
2. **Platzieren:** Ab sofort bleiben `7×7` Chunks um den Block geladen. Den Radius stellst du über `loaders.radius` ein.
3. **Rechtsklick** auf den Block öffnet das Menü. Dort zeigt **Grenze anzeigen** den Bereich als leuchtende Linien (automatisch beim Öffnen mit `border.show-on-open: true`).
   **Sneak + Rechtsklick** verhält sich normal, du kannst also weiter Blöcke daran bauen.
4. **Löschen** geht nur über das Menü (zweimal klicken). Danach ist der Loader weg und dein Limit wieder frei. Ein Item gibt es nicht zurück, ein neues bekommst du per `/chunkloader give`.

### Namen & Nummern
- Jeder Chunkloader bekommt pro Spieler die **kleinste freie Nummer**: Wer 8 gesetzt hat, bekommt als Nächstes „Chunkloader 9“. Wird Nr. 9 gelöscht, heißt der nächste wieder „Chunkloader 9“.
- Im Menü kannst du ihn über **Umbenennen** frei benennen (höchstens `gui.max-name-length` Zeichen, leer = wieder Standardname). Der Name erscheint im Chat, in Menüs, auf der Webkarte und in den Discord-Logs.
- Admin-Befehle sprechen einen Loader über Spieler und Nummer an: `/mrchunks tp Steve 3`.

### Mitspieler einladen
Owner können andere Spieler einladen, ihre Chunkloader **an- und auszuschalten**, z.B. Farm-Partner, damit die Farm auch läuft, wenn der Owner offline ist.
- **Ein Loader:** Menü des Loaders → **Mitglieder** → **Spieler einladen**.
- **Alle Loader** (auch künftige): `/chunkloader list` → **Mitglieder für alle Loader**.
- **Spieler einladen** zeigt die Köpfe der Online-Spieler: Dauer mit der Uhr wählen (`invites.durations`, Standard 1d / 7d / 30d / dauerhaft), dann auf einen Kopf klicken. Für Offline-Spieler gibt es **Namen eingeben** (Dialog). Ein Klick auf einen Kopf in der Mitgliederliste nimmt die Einladung zurück, befristete laufen von selbst ab.
- **Eingeladene** finden die Loader unter `/chunkloader list` → **Mit mir geteilt** und dürfen sie **nur an- und ausschalten**. Umbenennen, Löschen und Einladen bleibt dem Owner (und Admins).
- Einladungen zählen bei **niemandem** zum Limit. Schaltet ein Eingeladener einen Loader an, gilt das **Aktiv-Limit des Owners**, und der Owner bekommt eine Nachricht.
- Höchstens `invites.max-members` Eingeladene pro Liste. Abschalten: `invites.enabled: false`. Permission zum Einladen: `mrchunks.invite` (alle).

### Spieler ausschließen (`mrchunks.exempt`)
Wer `mrchunks.exempt` hat, ist **komplett vom Chunkloading ausgeschlossen**, z.B. Zweit-Accounts in einer eigenen LuckPerms-Rolle: kein `give`, kein Setzen, keine Einladungen, auch nicht als Eingeladener. Vorhandene Loader und Chunkloader-Items werden beim Betreten des Servers (und bei `/mrchunks reload`) **gelöscht**.

> ⚠️ **Streng:** Auch Admins sind betroffen. LuckPerms-Gruppen mit `*` oder `mrchunks.*` bekommen `mrchunks.exempt` mit, dort also ausdrücklich `/lp group admin permission set mrchunks.exempt false` setzen, sonst werden die Loader dieser Admins gelöscht.

### Grenze anzeigen
Die Grenze ist ein leuchtender „Zaun“ an jedem Chunk der Außenkante, sichtbar auch durch Blöcke. Nur du siehst sie, sie folgt deiner Höhe und verschwindet nach `border.duration-seconds`, über **Grenze ausblenden** im Menü oder sobald der Loader gelöscht wird. Farbe, Block, Dicke und Höhe stellst du unter `border` ein.

### Überschneidende Chunkloader
Jeder Chunk wird nur **einmal** geladen gehalten, auch wenn mehrere Loader ihn abdecken. Wird ein Loader deaktiviert oder gelöscht, bleiben die Chunks geladen, die ein anderer **aktiver** Loader abdeckt, egal wem er gehört. Was beim Setzen in einen bestehenden Bereich passiert, legt `loaders.overlap` fest:

| Wert | Verhalten |
|---|---|
| `allow` | einfach erlauben |
| `warn` (Standard) | erlauben, aber Hinweis mit den betroffenen Loadern und der Zahl doppelt abgedeckter Chunks, dazu wird die Grenze angezeigt |
| `deny-own` | verbieten, wenn er sich mit eigenen Loadern überschneidet |
| `deny-all` | verbieten, wenn er sich mit irgendeinem Loader überschneidet |

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
| `/chunkloader info` | Befehlshilfe, zeigt nur die Befehle, die du nutzen darfst | – |
| `/chunkloader version` | Version des Plugins | – (alle) |
| `/chunkloader map` | Link zur Webkarte | `mrchunks.map` |

### Admin: `/mrchunks`
| Befehl | Beschreibung | Permission |
|---|---|---|
| `/mrchunks gui` | Admin-Menü | `mrchunks.admin.gui` |
| `/mrchunks tp <spieler> <nr>` | Zu einem Chunkloader teleportieren | `mrchunks.admin.teleport` |
| `/mrchunks toggle <spieler> <nr>` | Chunkloader an/aus | `mrchunks.admin.manage` |
| `/mrchunks delete <spieler> <nr>` | Chunkloader löschen | `mrchunks.admin.manage` |
| `/mrchunks limit <spieler> <place\|active> <anzahl\|reset>` | `place` = wie viele Loader der Spieler setzen darf, `active` = wie viele davon gleichzeitig aktiv sein dürfen | `mrchunks.admin.limit` |
| `/mrchunks ban <spieler> [dauer\|permanent] [grund]` | Sperren: löscht alle Loader des Spielers und verhindert neue | `mrchunks.admin.ban` |
| `/mrchunks unban <spieler>` / `bans` | Sperre aufheben / alle Sperren anzeigen | `mrchunks.admin.ban` |
| `/mrchunks map [setup]` | Status der Webkarte / squaremap neu einrichten | `mrchunks.admin.map` |
| `/mrchunks discord [test]` | Discord-Status / Testnachricht | `mrchunks.admin.discord` |
| `/mrchunks reload` | Config neu laden | `mrchunks.admin.reload` |
| `/mrchunks version` | Version und Update-Status | alle |

Owner können ihre eigenen Loader auch mit `toggle` und `delete` steuern.

---

## Permissions
Alle Permissions mit ihrem Standard. „OP“ = nur Operatoren haben sie automatisch, „niemand“ = muss vergeben werden.
**Spieler haben ohne Zuweisung keine Rechte**, nur `/chunkloader version` funktioniert. Am einfachsten bekommt die Default-Gruppe in LuckPerms `mrchunks.player` (enthält use, map, view und invite).

### Spieler
| Permission | Standard | Erlaubt |
|---|---|---|
| `mrchunks.player` | OP | **Alle Spieler-Rechte** auf einmal: `use`, `map`, `view`, `invite` |
| `mrchunks.use` | OP | `/chunkloader give\|remove\|list`, Chunkloader setzen, eigene an/aus schalten, umbenennen, löschen |
| `mrchunks.map` | OP | `/chunkloader map` (Link zur Webkarte) |
| `mrchunks.view` | OP | Menü **fremder** Chunkloader öffnen: nur ansehen und Grenze anzeigen |
| `mrchunks.invite` | OP | Andere Spieler zu den eigenen Loadern einladen (Eingeladene dürfen nur an/aus schalten) |
| `mrchunks.limit.<anzahl>` | – | So viele Loader darf der Spieler **setzen**, z.B. `mrchunks.limit.15` (höchste Zahl zählt, mindestens `loaders.default-limit`) |
| `mrchunks.active.<anzahl>` | – | So viele dürfen **gleichzeitig aktiv** sein, z.B. `mrchunks.active.3` (mindestens `loaders.default-active-limit`) |
| `mrchunks.limit.unlimited` | niemand | Beliebig viele Loader setzen |
| `mrchunks.active.unlimited` | niemand | Beliebig viele gleichzeitig aktiv |
| `mrchunks.exempt` | niemand | **Ausschluss:** keine Loader, keine Einladungen, vorhandene Loader und Items werden beim Join gelöscht |

`/chunkloader info`, `/chunkloader version` und `/mrchunks version` brauchen keine Permission.

### Admin
| Permission | Standard | Erlaubt |
|---|---|---|
| `mrchunks.admin` | OP | **alle** folgenden `mrchunks.admin.*` auf einmal |
| `mrchunks.admin.gui` | OP | `/mrchunks gui` öffnen (die Aktionen darin brauchen ihre eigene Permission) |
| `mrchunks.admin.manage` | OP | **Fremde** Loader an/aus schalten, umbenennen, löschen, ihre Einladungen verwalten, über das Aktiv-Limit hinaus aktivieren (`toggle`, `delete`, „alle löschen“ im Menü) |
| `mrchunks.admin.teleport` | OP | `/mrchunks tp` und Teleport-Button im Menü |
| `mrchunks.admin.limit` | OP | `/mrchunks limit` und Limit-Buttons im Admin-Menü |
| `mrchunks.admin.ban` | OP | `/mrchunks ban\|unban\|bans` und Sperr-Buttons |
| `mrchunks.admin.map` | OP | `/mrchunks map [setup]` |
| `mrchunks.admin.discord` | OP | `/mrchunks discord [test]` |
| `mrchunks.admin.reload` | OP | `/mrchunks reload` und Reload-Button |
| `mrchunks.admin.update` | OP | Update-Hinweis beim Betreten des Servers |

### Beispiel LuckPerms
```text
# Alle Spieler dürfen Chunkloader nutzen (use, map, view, invite)
/lp group default permission set mrchunks.player true

# Mehr Loader für VIPs: 15 setzen, davon 3 gleichzeitig aktiv
/lp group vip permission set mrchunks.limit.15 true
/lp group vip permission set mrchunks.active.3 true

# Moderatoren: Menü, Liste, Teleport, Sperren - aber keine fremden Loader löschen
/lp group mod permission set mrchunks.admin.gui true
/lp group mod permission set mrchunks.admin.teleport true
/lp group mod permission set mrchunks.admin.ban true

# Admins: alles
/lp group admin permission set mrchunks.admin true

# Zweit-Accounts komplett ausschließen
/lp group alt permission set mrchunks.exempt true
```

> ⚠️ **Gruppen mit `*` oder `mrchunks.*`** bekommen in LuckPerms **alles**, auch `mrchunks.exempt` (Ausschluss, Loader werden gelöscht!) und `mrchunks.limit.unlimited`. Dort deshalb ausdrücklich setzen:
> ```text
> /lp group admin permission set mrchunks.exempt false
> ```
> und, falls die Limits trotzdem gelten sollen, `mrchunks.limit.unlimited false` und `mrchunks.active.unlimited false`.

---

## Limits: setzen und gleichzeitig aktiv
Jeder Spieler hat **zwei Limits**:

| | Setzbare Loader | Gleichzeitig aktive Loader |
|---|---|---|
| Standard (Config) | `loaders.default-limit` | `loaders.default-active-limit` |
| Per Permission | `mrchunks.limit.<n>` | `mrchunks.active.<n>` |
| Unbegrenzt | `mrchunks.limit.unlimited` | `mrchunks.active.unlimited` |
| Per Admin | `/mrchunks limit <spieler> place <n\|reset>` | `/mrchunks limit <spieler> active <n\|reset>` |

**Beispiel:** 15 Loader setzen, davon 3 gleichzeitig nutzen: `/mrchunks limit Steve place 15` und `/mrchunks limit Steve active 3`, oder für eine Gruppe `mrchunks.limit.15` und `mrchunks.active.3`.

- **Setzen:** Ist noch ein Aktiv-Platz frei, startet der neue Loader aktiv. Sonst wird er gesetzt, bleibt aber **deaktiviert**, mit Hinweis.
- **Aktivieren** (Menü oder `/mrchunks toggle`): Ist das Aktiv-Limit erreicht, wird abgelehnt, man muss zuerst einen anderen ausschalten. Admins mit `mrchunks.admin.manage` dürfen darüber hinaus aktivieren.
- **Admin senkt das Aktiv-Limit:** Überzählige Loader werden sofort deaktiviert, die zuletzt gesetzten zuerst, und der Owner wird benachrichtigt.
- **Reihenfolge:** Ein Admin-Limit hat Vorrang. Danach zählt die höchste Permission-Zahl, mindestens aber der Standard aus der Config. Permissions kennt der Server nur bei Online-Spielern, für Offline-Spieler gelten Admin-Limit oder Standard.

> ⚠️ In LuckPerms enthalten `*` und `mrchunks.*` auch `mrchunks.limit.unlimited` und `mrchunks.active.unlimited`. Wer das nicht will, setzt beide bei der Gruppe ausdrücklich auf `false`.

```text
/lp group vip permission set mrchunks.limit.15 true
/lp group vip permission set mrchunks.active.3 true
/lp group mod permission set mrchunks.admin.gui true
/lp group mod permission set mrchunks.admin.teleport true
```

---

## Admin-Menü (`/mrchunks gui`)
- **Oben:** Übersicht (Anzahl Loader, aktive Loader, geladene Chunks, Spieler, Sperren), Version und Webkarten-Status, Filter (Alle mit Loadern, Online, Gesperrte).
- **Spielerköpfe:** Loader und Limit, woher das Limit kommt, online, offline oder gesperrt.
- **Klick auf einen Kopf:** beide Limits, setzbar (Erfahrungsflasche) und gleichzeitig aktiv (Leuchtfeuer), jeweils Linksklick +1, Rechtsklick −1, Shift-Klick zurücksetzen. Dazu: alle Loader löschen, sperren (1d, 7d, dauerhaft) oder entsperren, dazu alle Loader des Spielers.
- **Klick auf einen Loader:** an/aus, umbenennen, Grenze anzeigen, **hinteleportieren**, löschen.

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
4. **Adresse festlegen**, damit Spieler mit **`/chunkloader map`** einen funktionierenden Link bekommen (siehe unten).

### Adresse der Karte & Reverse Proxy
`squaremap.address` in der MRchunks-Config bestimmt den Link:

| `address` | `reverse-proxy` | Link |
|---|---|---|
| `""` (Standard) | – | automatisch: squaremaps `web-address` (falls geändert), sonst `server-ip` aus der `server.properties`, sonst die öffentliche IP → `http://1.2.3.4:8080/` |
| `map.deinserver.de` | `false` | `http://map.deinserver.de:8080/` (Port von squaremaps internem Webserver) |
| `map.deinserver.de` | `true` | `https://map.deinserver.de/` |
| `https://deinserver.de/karte/` | egal | genau so, z.B. für einen Proxy mit Pfad |

- **Ohne Proxy:** Port 8080 (bzw. `internal-webserver.port` in `plugins/squaremap/config.yml`) beim Hoster freigeben.
- **Öffentliche IP:** Wird nur ermittelt, wenn `address` leer ist und keine `server-ip` gesetzt ist, einmal beim Start über `checkip.amazonaws.com`. Abschalten: `squaremap.detect-public-ip: false`.
- **Reverse Proxy (nginx, Caddy, Traefik, Cloudflare Tunnel …):** Der Proxy leitet auf squaremaps Port weiter. Die Karte und das Chunkraster laden alles über relative Pfade und funktionieren deshalb auch unter einem Unterpfad wie `/karte/`. Läuft der Proxy auf demselben Rechner, squaremap am besten nur lokal lauschen lassen (`internal-webserver.bind: 127.0.0.1`), dann ist Port 8080 von außen zu. Beispiel nginx:
  ```nginx
  server {
      server_name map.deinserver.de;
      # listen 443 ssl; + Zertifikat (z.B. per certbot)
      location / {
          proxy_pass http://127.0.0.1:8080/;
          proxy_set_header Host $host;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
      }
  }
  ```
  Unter einem Pfad: `location /karte/ { proxy_pass http://127.0.0.1:8080/; }`, dazu in MRchunks `address: "https://deinserver.de/karte/"`.
- **Eigener Webserver statt squaremaps internem:** Liefert nginx o.ä. den Ordner `plugins/squaremap/web` direkt aus (`internal-webserver.enabled: false`), funktioniert das Chunkraster ebenfalls, weil MRchunks seine Datei direkt in diesen Ordner schreibt.

### Was MRchunks auf der Karte zeigt
- **Ebene „Chunkloader“:** pro Loader der 7×7-Bereich als farbige Fläche, eine Farbe pro Owner, deaktivierte Loader grau. Dazu ein Chunkloader-Symbol und Infos beim Darüberfahren oder Anklicken (Owner, Position, Bereich, Status). Änderungen erscheinen sofort.
- **Ebene „Chunkraster“:** Chunk-Linien ab einer sinnvollen Zoomstufe, gelbe Regionsgrenzen (32 × 32 Chunks) und bei starkem Zoom die Chunk-Koordinaten.
- Beide Ebenen schaltet jeder Besucher im **Ebenen-Menü oben links** ein und aus.
- Farben, Deckkraft, Symbol, Texte, Dimensionsnamen und Raster stellst du unter `squaremap:` in der MRchunks-Config ein. `/mrchunks map` zeigt den Status.

---

## Discord
Webhook in Discord anlegen (*Kanal bearbeiten → Integrationen → Webhooks*), dann `discord.enabled: true` setzen, die `webhook-url` eintragen und `/mrchunks reload` ausführen.
`/mrchunks discord test` prüft die Einrichtung.

Diese Events gibt es: Loader gesetzt, gelöscht, an/aus geschaltet, umbenannt, Einladung erteilt und zurückgenommen, Spieler per exempt bereinigt, Block wiederhergestellt oder fehlend, dazu die Admin-Aktionen Löschen, Sperren, Entsperren, Limit und Reload. An/aus und Farben stellst du unter `discord.events` in der Config ein, die Texte unter `discord` in der Sprachdatei.

---

## Häufige Fragen
**Ein Loader-Block ist trotzdem verschwunden (z.B. durch `/fill`, `/setblock` oder WorldEdit).**
Solche Befehle umgehen den Schutz, weil sie keine Events auslösen. MRchunks prüft deshalb regelmäßig (`loaders.check-interval-seconds`) und zusätzlich jedes Mal, wenn ein Chunk mit einem Loader geladen wird, und stellt den Block wieder her. Mit `restore-missing-blocks: false` wird der Loader stattdessen gelöscht. Wird der Block per `/fill … destroy` zerstört, fällt nur ein normaler Emeraldblock heraus, kein Chunkloader-Item.

**Was passiert, wenn ein Chunkloader-Item verloren geht?**
Nichts Schlimmes: Items sind nur „Gutscheine“. Gezählt werden die **gesetzten** Loader. Ein verlorenes Item holst du dir einfach neu mit `/chunkloader give`. Items in Kisten oder bei anderen Spielern zählen nicht. Wer ein Item setzt, dem gehört der Loader, und es zählt zu seinem Limit.

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
