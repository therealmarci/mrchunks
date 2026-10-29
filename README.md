# MRchunks

**Chunkloader für Survival-Server:** Ein Block, und deine Farm läuft weiter - auch wenn du offline bist. Mit einem Chunk-Budget, das jeder selbst auf seine Loader verteilt, Einladungen für Mitspieler, Lag-Schutz und einer Webkarte.

**[⬇ Download](https://mrtechnology.marcelr.at/download/mrchunks/)** · **[📖 Anleitung online](https://mrtechnology.marcelr.at/docs/mrchunks/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mrchunks/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - LuckPerms ist empfohlen, [squaremap](https://modrinth.com/plugin/squaremap) für die Webkarte, MRadmin optional |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | Vorabversion (0.x) - Config, Befehle und Permissions können sich bis 1.0.0 noch ändern |

## Was kann MRchunks?
- **Chunkloader-Blöcke:** Einmal platziert, bleibt der Bereich drumherum dauerhaft geladen - in allen Dimensionen, auch wenn der Spieler offline ist.
- **Größe frei wählbar:** Jeder Spieler hat ein **Chunk-Budget** (Standard 147 Chunks = drei 7×7-Loader) und stellt die Größe jedes Loaders selbst ein - auch als Rechteck, z.B. 3×15 für eine lange Farm. Wenige große oder viele kleine Loader, wie jeder will. Mehr Budget per Permission oder Admin-Befehl.
- **Mitspieler einladen:** Farm-Partner dürfen deine Loader an- und ausschalten.
- **Dummy-Spieler** (optional): ein künstlicher Spieler auf dem Loader, damit Mobs aktiv bleiben und Farmen wie mit einem echten Spieler daneben laufen.
- **Zweit-Accounts verknüpfen:** Haupt- und AFK-Account teilen sich Loader und Budget.
- **Unzerstörbar:** kein Abbauen, keine Explosionen, Kolben, Wither oder Endermen.
- **Lag-Schutz:** Loader, die den Server ausbremsen, werden erkannt und gesperrt. Laggt der ganze Server, pausieren alle Loader automatisch.
- **Webkarte** mit echtem Gelände über squaremap, automatisch eingerichtet.
- **Menüs statt Befehle** für Spieler, ein Admin-Menü für das Team, **Discord-Logs** und **Update-Prüfung**.

## Schnellstart
1. **[MRchunks herunterladen](https://mrtechnology.marcelr.at/download/mrchunks/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten. Dabei entstehen `plugins/MRchunks/config.yml` und die Sprachdateien in `plugins/MRchunks/lang/`.
3. **Spielern die Rechte geben** - ohne sie dürfen nur OPs Chunkloader nutzen:
   ```text
   /lp group default permission set mrchunks.player true
   ```
4. Fertig. Jeder Spieler hat standardmäßig **147 Chunks** Budget (drei 7×7-Loader) und darf bis zu 30 Loader setzen. Anpassen in der Config, dann `/mrchunks reload`.

> [!WARNING]
> Gruppen mit `*` oder `mrchunks.*` bekommen in LuckPerms auch `mrchunks.exempt` - und deren Loader werden **gelöscht**. Setz dort ausdrücklich `/lp group admin permission set mrchunks.exempt false`. Mehr unter [Permissions](#permissions).

**Update:** Die alte JAR durch die neue ersetzen und den Server neu starten. Die Config wird automatisch angepasst: Deine Werte bleiben, neue Optionen kommen dazu, und die alte Datei wird als `config.v<N>.backup.yml` gesichert.

> [!WARNING]
> **Update auf das Chunk-Budget:** Die Limits `mrchunks.limit.<n>`, `mrchunks.active.<n>` (und `.unlimited`) gibt es nicht mehr - stattdessen `mrchunks.budget.<n>`. Vorhandene Loader behalten ihre Größe, und niemand verliert etwas: Das Budget wird beim Update aus dem alten Aktiv-Limit berechnet (3 aktive 7×7-Loader = 147 Chunks), auch für Admin-Limits. **Permissions musst du selbst umstellen**, z.B. `/lp group vip permission unset mrchunks.active.5` und `/lp group vip permission set mrchunks.budget.245 true` (5 × 49). Details unter [Chunk-Budget](#chunk-budget).

---

## So funktioniert's (für Spieler)
1. **`/chunkloader`** (oder `/cl`) öffnet dein Menü. Unten rechts holt **Chunkloader holen** einen Loader ins Inventar (Linksklick 1, Shift bis zu 64 - höchstens 30 Loader insgesamt). Dort siehst du auch dein **Budget**. **Chunkloader zurückgeben** daneben nimmt Items wieder zurück.
2. **Block platzieren** - ab sofort bleiben die `7×7` Chunks rundherum geladen. Reicht dein freies Budget nicht mehr für 7×7, wird der Loader so groß, wie es noch geht.
3. **Rechtsklick** auf den Block öffnet sein Menü: oben die Infos, darunter **Mitglieder**, **an/aus**, **Grenze anzeigen** (Enderperle), **Dummy** und **Einstellungen**. In den Einstellungen liegen **Größe**, **Block ändern**, **Umbenennen** und **Löschen**. **Sneak + Rechtsklick** verhält sich normal, du kannst also weiter Blöcke daran bauen.
4. **Löschen** geht nur über das Menü (zweimal klicken).

### Größe und Chunk-Budget
Jeder aktive Loader kostet so viele Chunks aus deinem Budget, wie er groß ist: 7×7 kostet 49, 3×3 kostet 9, 3×15 kostet 45. **Ausgeschaltete Loader kosten nichts.** Mit dem Standard-Budget von 147 Chunks kannst du z.B. drei 7×7-Loader betreiben - oder sechzehn 3×3-Loader.

Im Menü eines Loaders öffnet **Einstellungen → Größe** die Einstellung:
- **Pfeile Norden, Süden, Westen, Osten:** Linksklick +1 Chunk in diese Richtung, Rechtsklick −1. So wird der Bereich zum Rechteck, und der Block muss nicht in der Mitte stehen - z.B. am Rand einer langen Farm.
- **Mitte:** alle vier Richtungen auf einmal.
- Oben steht, wie viel Budget du nutzt und wie viel frei ist. **Grenze anzeigen** zeigt den Rand und folgt jeder Änderung.
- Vergrößern geht nur mit genug freiem Budget und bis zur Höchstgröße des Servers (Standard 15×15). Ist dein Budget voll, schalte einen anderen Loader aus oder verkleinere einen.
- Die Größe ändern darfst nur du (`mrchunks.resize`) - Eingeladene nicht.

Die Menüleiste in `/chunkloader` von links nach rechts:

| Knopf | Wofür | Permission |
|---|---|---|
| **Mitglieder für alle Loader** | Spieler für alle deine Loader einladen | `mrchunks.invite` |
| **Mit mir geteilt** | Loader anderer Spieler, zu denen du eingeladen bist | – |
| **Verknüpfte Accounts** | Zweit-Account verknüpfen oder lösen | `mrchunks.link` |
| **Webkarte** | Link zur Webkarte im Chat (nur mit squaremap) | `mrchunks.map` |
| **Chunkloader holen** / **zurückgeben** | Loader-Items holen oder zurückgeben | `mrchunks.use` |

Hast du mehr als 45 Loader, erscheinen Pfeile zum Blättern.

### Was der Block im Menü bedeutet
| Block | Status |
|---|---|
| Smaragdblock | Alles OK |
| Grauer Beton | Ausgeschaltet |
| Magmablock | Über einer Warnschwelle des [Lag-Schutzes](#lag-schutz) |
| Redstone-Block | Wegen Lag gesperrt |
| Spinnennetz | Pausiert, weil der ganze Server laggt |

### Was läuft in geladenen Chunks?
- **Läuft:** Redstone, Trichter, Öfen, Tiere und andere Entities, Pflanzenwachstum - alles, was Zufalls-Ticks braucht.
- **Braucht trotzdem einen Spieler in der Nähe:** natürliches Mob-Spawning, Spawner und volle Mob-KI (Mobs bewegen sich ohne Spieler kaum und laufen z.B. nicht durch Portale). Dafür gibt es [Dummy-Spieler](#dummy-spieler).

### Namen, Nummern und Grenze
- Jeder Loader bekommt die **kleinste freie Nummer** („Chunkloader 4“). Über **Umbenennen** gibst du ihm einen eigenen Namen - er erscheint im Chat, in Menüs, auf der Webkarte und in Discord.
- **Block ändern** wählt, als welcher Block dein Loader in der Welt steht (z.B. Diamant-, Gold- oder Netheritblock). Nur der Block in der Welt ändert sich - in Menüs und als Item bleibt es der Smaragdblock. Die Auswahl legt der Server fest (`item.block-choices`), du brauchst `mrchunks.block`.
- **Grenze anzeigen** zeichnet einen leuchtenden Zaun an die Außenkante des Bereichs, sichtbar auch durch Blöcke und nur für dich. Er verschwindet nach einer Weile von selbst.

### Mitspieler einladen
Lade Farm-Partner ein, damit sie deine Loader **an- und ausschalten** können, auch wenn du offline bist:
- **Ein Loader:** Menü des Loaders → **Mitglieder** → **Spieler einladen**.
- **Alle Loader** (auch künftige): `/chunkloader` → **Mitglieder für alle Loader**.
- Dauer mit der Uhr wählen (1 Tag, 7 Tage, 30 Tage oder dauerhaft), dann auf einen Kopf klicken. Für Offline-Spieler gibt es **Namen eingeben**.
- Eingeladene finden die Loader unter **Mit mir geteilt**. Umbenennen, löschen und einladen darfst nur du.
- Einladungen zählen bei niemandem zum Limit. Schaltet ein Eingeladener einen Loader an, kostet das **dein** Budget.

### Zweit-Accounts verknüpfen
Hast du einen Zweit-Account (z.B. zum AFK-Farmen), verknüpf ihn mit deinem Haupt-Account. Danach verhalten sich beide wie ein Spieler:
- Beide sehen und verwalten **dieselben Loader**. Was der Zweit-Account setzt, gehört dem Haupt-Account.
- Es gilt **ein gemeinsames Budget** - das des Haupt-Accounts.
- **Verknüpfen:** `/chunkloader` → **Verknüpfte Accounts** → **Account verknüpfen**. Der andere Account muss online sein und zustimmen.
- **Lösen** kann jeder der beiden jederzeit im selben Menü. Die Loader bleiben beim Haupt-Account.
- **Admins** verknüpfen und lösen Accounts auch ohne Zustimmung: mit `/mrchunks link <haupt> <zweit>` bzw. `/mrchunks unlink <spieler>` oder in der MRadmin-Weboberfläche auf der Spielerseite (`mrchunks.admin.link`).
- Erlaubt ist standardmäßig ein Zweit-Account (`linked-accounts.max-accounts`). Eine Sperre gilt für alle verknüpften Accounts.

### Im Browser (mit MRadmin)
Ist MRadmin installiert und die Spielerseite eingeschaltet, gibt `/web` im Spiel einen Link zu deiner eigenen Seite. Unter **Chunkloader** siehst du oben dein Budget und Warnungen (z.B. Lag-Sperre), darunter jeden Loader als Karte mit Status, Größe und Auslastung - mit einem Knopf zum Ein- und Ausschalten. Ein Klick auf eine Karte zeigt die Grenzwerte des Lag-Schutzes (Warnung und Sperre), eine Live-Karte rund um den Loader (mit squaremap) und die Knöpfe zum Umbenennen, für den Dummy und zum Löschen. Darunter lädst du Spieler für alle Loader ein und siehst, wer eingeladen ist (Dauer aus `invites.durations`); Einladungen für einen einzelnen Loader stehen in seiner Karte. Nur die **Größe** änderst du im Spiel. Braucht MRadmin ab der Version mit Modul-API 5.

### Dummy-Spieler
Ein Chunkloader hält Chunks geladen - für den Server steht dort aber **kein Spieler**. Mobs sind deshalb fast inaktiv, und es spawnt nichts. Ein **Dummy** ist ein künstlicher Spieler, der auf dem Loader sitzt und als Spieler in der Nähe zählt.

- **Einschalten:** Menü des Loaders → **Dummy einschalten**. Er sitzt dort, solange der Loader aktiv ist.
- **Aussehen:** dein Skin, Name `[D<nr>]<dein Name>`, sitzend.
- **Sonst kein Spieler:** unverwundbar, von Mobs ignoriert, nicht in der Tab-Liste und nicht in der Spielerzahl. MRsleep, MRadmin und MRpvp ignorieren ihn.
- **Limit:** standardmäßig 1 pro Spieler, mehr per Permission oder Admin-Befehl.
- **Nur auf quadratischen Loadern ab 5×5, Block in der Mitte:** Ein Dummy simuliert wie ein Spieler ein Quadrat um sich herum. Bei einem Rechteck oder einem kleineren Loader würde er Chunks außerhalb des Bereichs simulieren, die dein Budget gar nicht bezahlt. Sitzt ein Dummy auf dem Loader, lässt sich die Größe deshalb nur gleichmäßig in alle Richtungen ändern.

> [!NOTE]
> Dummies sind **standardmäßig ausgeschaltet** (`dummies.enabled: false`). Ein Dummy kostet so viel Leistung wie ein echter Spieler an dieser Stelle. Er nutzt Server-Interna von Paper: Passt die MRchunks-Version nicht genau zur Server-Version, bleiben Dummies aus (Hinweis in der Konsole) - alles andere funktioniert normal.

---

## Befehle (Admin)
Spieler brauchen keine Befehle außer `/chunkloader` - alles läuft über Menüs. Für das Team gibt es `/mrchunks`:

| Befehl | Was er tut | Permission |
|---|---|---|
| `/mrchunks gui` | Admin-Menü: alle Spieler und ihre Loader | `mrchunks.admin.gui` |
| `/mrchunks tp <spieler> <nr>` | Zu einem Chunkloader teleportieren | `mrchunks.admin.teleport` |
| `/mrchunks toggle <spieler> <nr>` | Chunkloader an/aus | `mrchunks.admin.toggle` |
| `/mrchunks delete <spieler> <nr>` | Chunkloader löschen | `mrchunks.admin.manage` |
| `/mrchunks unlock <spieler> <nr>` | Lag-Sperre aufheben | `mrchunks.admin.manage` |
| `/mrchunks lag` | Auffällige und gesperrte Loader anzeigen | `mrchunks.admin.manage` |
| `/mrchunks dummy <spieler> <nr>` | Dummy eines Loaders an/aus (auch über das Limit hinaus) | `mrchunks.admin.manage` |
| `/mrchunks link <haupt> <zweit>` | Accounts ohne Zustimmung verknüpfen | `mrchunks.admin.link` |
| `/mrchunks unlink <spieler>` | Verknüpfung lösen | `mrchunks.admin.link` |
| `/mrchunks limit <spieler> <budget\|dummy> <anzahl\|reset>` | Chunk-Budget oder Dummy-Limit eines Spielers setzen (siehe [Chunk-Budget](#chunk-budget)) | `mrchunks.admin.limit` |
| `/mrchunks ban <spieler> [dauer\|permanent] [grund]` | Sperren: löscht alle Loader und verhindert neue | `mrchunks.admin.ban` |
| `/mrchunks unban <spieler>` · `bans` | Sperre aufheben · alle Sperren anzeigen | `mrchunks.admin.ban` |
| `/mrchunks map [setup]` | Status der Webkarte · squaremap neu einrichten | `mrchunks.admin.map` |
| `/mrchunks discord [test]` | Discord-Status · Testnachricht | `mrchunks.admin.discord` |
| `/mrchunks reload` | Config und Sprachdatei neu laden | `mrchunks.admin.reload` |
| `/mrchunks version` | Version und Update-Status | `mrchunks.version` (in `mrchunks.player`) |

Owner dürfen ihre eigenen Loader auch mit `toggle` und `delete` steuern.

### Admin-Menü
`/mrchunks gui` zeigt oben eine Übersicht (Loader, aktive Loader, geladene Chunks, Sperren) und darunter alle Spieler mit Loadern als Köpfe.
- **Klick auf einen Kopf:** Budget mit Links-/Rechtsklick ändern (±10 Chunks, Shift = zurücksetzen), Dummy-Limit (±1), alle Loader löschen, sperren oder entsperren - und darunter alle Loader des Spielers.
- **Klick auf einen Loader:** an/aus, Größe, umbenennen, Grenze anzeigen, hinteleportieren, löschen.

Alle Menüs aktualisieren sich jede Sekunde. Jede Aktion prüft ihre eigene Permission.

## Permissions
„OP“ heißt: nur Operatoren haben sie automatisch. „niemand“ heißt: muss ausdrücklich vergeben werden.

**Spieler**

| Permission | Standard | Wofür |
|---|---|---|
| `mrchunks.player` | OP | **Alle Spieler-Rechte** auf einmal (`use`, `resize`, `map`, `view`, `invite`, `link`, `block`) - am besten der Default-Gruppe geben |
| `mrchunks.use` | OP | Chunkloader holen, setzen und die eigenen verwalten |
| `mrchunks.resize` | OP | Größe der eigenen Loader ändern (im Rahmen des Budgets) |
| `mrchunks.map` | OP | Knopf **Webkarte** |
| `mrchunks.view` | OP | Menü **fremder** Loader ansehen (nur ansehen und Grenze anzeigen) |
| `mrchunks.invite` | OP | Mitspieler zu eigenen Loadern einladen |
| `mrchunks.link` | OP | Zweit-Account verknüpfen |
| `mrchunks.block` | OP | Block der eigenen Loader in der Welt ändern (Auswahl aus `item.block-choices`) |
| `mrchunks.budget.<anzahl>` | – | Chunk-Budget, z.B. `mrchunks.budget.300` |
| `mrchunks.dummies.<anzahl>` | – | So viele Dummy-Spieler, z.B. `mrchunks.dummies.2` |
| `mrchunks.budget.unlimited` · `dummies.unlimited` | niemand | Ohne Begrenzung |
| `mrchunks.exempt` | niemand | **Komplett ausgeschlossen:** keine Loader, keine Einladungen - vorhandene Loader werden beim Betreten **gelöscht** |

**Team-Gruppen** - in allen MRtechnology-Plugins gleich aufgebaut, jede enthält die darunter (und `mrchunks.player`):

| Gruppe | Standard | Enthält |
|---|---|---|
| `mrchunks.groups.support` | OP | Admin-Menü (alle Loader und ihren Status sehen), zu Loadern teleportieren |
| `mrchunks.groups.staff` | OP | Support + fremde Loader an/aus schalten, ihre Einladungen verwalten, Accounts verknüpfen und lösen |
| `mrchunks.groups.admin` | OP | Alles: Staff + löschen, umbenennen, Größe fremder Loader ändern, Dummies, Lag-Sperren aufheben, Budget und Dummy-Limit, Sperren, Webkarte, Discord, Reload, Update-Hinweis |

```text
/lp group supporter permission set mrchunks.groups.support true
/lp group staff permission set mrchunks.groups.staff true
/lp group admin permission set mrchunks.groups.admin true
```

**Einzelne Team-Rechte**

| Permission | Standard | Wofür |
|---|---|---|
| `mrchunks.admin.gui` | OP | Admin-Menü öffnen (Aktionen darin brauchen ihre eigene Permission) |
| `mrchunks.admin.toggle` | OP | **Fremde** Loader an/aus schalten (im Rahmen des Budgets des Owners) |
| `mrchunks.admin.invites` | OP | Einladungen **fremder** Loader verwalten |
| `mrchunks.admin.link` | OP | Accounts anderer verknüpfen und lösen |
| `mrchunks.admin.manage` | OP | **Fremde** Loader löschen, umbenennen, Größe ändern, Block wählen, Dummies, Lag-Sperren aufheben (enthält `toggle`, `invites`, `link`, `resize`) |
| `mrchunks.admin.resize` | OP | Größe **fremder** Loader ändern (im Spiel und in der MRadmin-Weboberfläche, im Rahmen des Budgets des Owners) |
| `mrchunks.admin.teleport` | OP | Zu Loadern teleportieren |
| `mrchunks.admin.limit` | OP | Chunk-Budget und Dummy-Limit eines Spielers festlegen |
| `mrchunks.admin.ban` | OP | Spieler sperren und entsperren |
| `mrchunks.admin.map` | OP | `/mrchunks map` |
| `mrchunks.admin.discord` | OP | `/mrchunks discord` |
| `mrchunks.admin.reload` | OP | `/mrchunks reload` |
| `mrchunks.admin.update` | OP | Update-Hinweis beim Betreten des Servers |

> [!WARNING]
> **Wildcards:** `*` und `mrchunks.*` enthalten in LuckPerms **alles** - auch `mrchunks.exempt` (Loader werden gelöscht!) und die `unlimited`-Rechte. Für solche Gruppen:
> ```text
> /lp group admin permission set mrchunks.exempt false
> ```
> Soll das Budget trotzdem gelten, auch `mrchunks.budget.unlimited` auf `false` setzen.

<details>
<summary>LuckPerms-Beispiele</summary>

```text
# Alle Spieler dürfen Chunkloader nutzen
/lp group default permission set mrchunks.player true

# VIPs: 300 Chunks Budget (z.B. sechs 7x7-Loader)
/lp group vip permission set mrchunks.budget.300 true

# Moderatoren: wie Staff (Loader an/aus, Einladungen, Accounts), dazu sperren - aber nichts löschen
/lp group mod permission set mrchunks.groups.staff true
/lp group mod permission set mrchunks.admin.ban true

# Admins: alles
/lp group admin permission set mrchunks.groups.admin true

# Zweit-Accounts komplett ausschließen
/lp group alt permission set mrchunks.exempt true
```
</details>

## Chunk-Budget
Jeder Spieler hat ein **Budget in Chunks**: So viele Chunks dürfen alle seine **aktiven** Loader zusammen geladen halten. Wie er es aufteilt - wenige große oder viele kleine Loader, Quadrate oder Rechtecke -, entscheidet er selbst im Menü **Größe**.

| | Chunk-Budget |
|---|---|
| Standard (Config) | `loaders.default-budget` (147 = drei 7×7-Loader) |
| Per Permission | `mrchunks.budget.<n>` |
| Unbegrenzt | `mrchunks.budget.unlimited` |
| Per Admin | `/mrchunks limit <spieler> budget <n\|reset>`, Admin-Menü oder MRadmin-Weboberfläche |

- **Kosten:** Ein aktiver Loader kostet Breite × Länge, also 49 für 7×7. Ausgeschaltete Loader kosten nichts. Überlappende Chunks zählen für jeden Loader voll.
- **Neue Loader** sind `loaders.default-reach` groß (3 = 7×7). Reicht das freie Budget nicht, bekommen sie das größte Quadrat, das noch passt; ist gar nichts mehr frei, werden sie gesetzt, bleiben aber **aus**.
- **Höchstgröße:** `loaders.max-reach` Chunks pro Richtung (Standard 7 = bis 15×15).
- **Wie viele Loader** jemand setzen darf, ist für alle gleich: `loaders.max-loaders` (Standard 30).
- **Niemand überschreitet das Budget** - auch das Team nicht beim Einschalten oder Vergrößern fremder Loader. Admins erhöhen stattdessen das Budget.
- Senkt ein Admin das Budget, werden überzählige Loader sofort ausgeschaltet (die zuletzt gesetzten zuerst) und der Owner informiert.
- **Reihenfolge:** Ein Admin-Wert hat Vorrang, danach zählt die höchste Permission-Zahl, mindestens aber der Standard aus der Config.
- **Dummies** haben ein eigenes Limit nach demselben Muster (`dummies.default-limit`, `mrchunks.dummies.<n>`, `/mrchunks limit <spieler> dummy <n>`).

## Lag-Schutz
MRchunks misst alle 30 Sekunden, was im Bereich jedes **aktiven** Loaders passiert. **Jeder Loader wird für sich bewertet**, gegen die Grenzen seiner eigenen Fläche:

- **Bis 3×3** gelten feste Grenzen (`lag-protection.small-loaders`).
- **Ab 3×3** wachsen die Grenzen mit der Größe des Loaders. Die Werte in `lag-protection.limits` gelten für 49 Chunks (ein 7×7-Loader) und werden auf die Fläche umgerechnet.
- Die 3×3-Grenzen sind das **Minimum**: Kein Loader bekommt niedrigere Grenzen.
- Überschreitet ein Loader eine Grenze, betrifft das **nur diesen einen Loader** - die anderen Loader desselben Spielers laufen weiter.

| Messwert | Was gezählt wird | Warnung bis 3×3 | Sperre bis 3×3 | Warnung (pro 49 Chunks) | Sperre (pro 49 Chunks) |
|---|---|---|---|---|---|
| `entities` | Mobs, Tiere, Loren, Items … | 100 | 250 | 300 | 800 |
| `items` | Items am Boden | 80 | 300 | 200 | 1000 |
| `redstone` | Redstone-Änderungen pro Sekunde | 150 | 600 | 500 | 2000 |
| `hoppers` | Trichter-Transfers pro Sekunde | 100 | 400 | 300 | 1200 |
| `pistons` | Kolben-Bewegungen pro Sekunde | 15 | 50 | 40 | 150 |
| `spawns` | neue Mobs und Items pro Sekunde | 8 | 25 | 20 | 80 |

**Beispiel Entities (Sperre):** ein 3×3-Loader 250, ein 5×5-Loader 408 (800 × 25/49), ein 7×7-Loader 800, ein 9×9-Loader 1322 (800 × 81/49).

- **Über „Warnung“:** Der Loader taucht in `/mrchunks lag` auf (und in MRadmin), dazu ein Discord-Log. Sonst passiert nichts.
- **Über „Sperre“:** Gesperrt wird erst, wenn der Loader **mehrere Messungen hintereinander** darüber liegt (Standard 3, also 1,5 Minuten) **und der Server dabei laggt**. Dann wird **nur dieser Loader** gesperrt. Eine große Farm, mit der der Server gut zurechtkommt, wird nicht gesperrt. Beim Doppelten der Sperrgrenze wird auch ohne Server-Lag gesperrt.
- **Gesperrt heißt:** Der Loader ist aus und kann nur von Admins entsperrt werden (Menü oder `/mrchunks unlock`). Der Owner wird informiert, mit MRadmin bekommt er automatisch ein **Ticket** mit allen Details.
- Das Menü jedes aktiven Loaders zeigt die letzte Messung gegen die Grenzen dieses Loaders - so können Spieler ihre Farm selbst einschätzen.
- Alle Grenzen stehen unter `lag-protection.limits` und `lag-protection.small-loaders.limits`, für wie viele Chunks sie gelten unter `lag-protection.limits-per-chunks` (49) und bis zu welcher Größe die festen gelten unter `lag-protection.small-loaders.max-chunks` (9). `0` schaltet eine Messung ab. Alle Grenzen werden auf ganze Zahlen gerundet.

Die Standardwerte sind bewusst großzügig: Normale Farmen bleiben weit darunter, außer Kontrolle geratene Maschinen nicht.

### Server-Pause
Laggt der **ganze Server** - egal warum -, pausiert MRchunks **alle** Loader:
- **Pause**, wenn die TPS 30 Sekunden lang unter 15 liegt. Kurze Lag-Spitzen lösen nichts aus. Direkt davor werden alle Loader noch einmal gemessen, und klare Verursacher werden gesperrt.
- **Weiter**, wenn die TPS 60 Sekunden lang wieder bei mindestens 18 liegt. Niemand muss etwas neu einschalten.
- Owner und Admins werden informiert, im Menü steht „⏸ Pausiert“. Einstellbar unter `server-pause`.

---

## Konfiguration
Alles steht in `plugins/MRchunks/config.yml`. Jede Option ist in der Datei kommentiert. Änderungen übernimmst du mit `/mrchunks reload`.

| Abschnitt | Die wichtigsten Einstellungen |
|---|---|
| `loaders` | `default-budget` (147 Chunks), `default-reach` (3 = neue Loader 7×7), `max-reach` (7 = höchstens 15×15), `max-loaders` (30), `overlap`, `load-method` |
| `invites` | an/aus, Dauer-Auswahl, höchstens so viele Mitglieder |
| `linked-accounts` | an/aus, wie viele Zweit-Accounts |
| `dummies` | an/aus (Standard aus), Limit, Skin, sitzend |
| `lag-protection` · `server-pause` | Mess- und Sperrgrenzen, TPS-Schwellen |
| `item` · `border` | Aussehen des Loaders und der Grenze, `item.block-choices`: Blöcke, die Spieler für ihre Loader wählen dürfen (leere Liste `[]` = keine Auswahl) |
| `squaremap` | Adresse der Webkarte, Farben (auch nach Status), getrennte Ebenen, Dummy-Symbol, Raster |
| `discord` · `update-checker` · `gui` | Logs, Update-Hinweise, Menüs |

**Überschneidende Loader** (`loaders.overlap`): `allow` erlaubt einfach, `warn` (Standard) erlaubt mit Hinweis und zeigt die Grenze, `deny-own` verbietet Überschneidungen mit eigenen Loadern, `deny-all` mit allen - beim Setzen und beim Vergrößern. Jeder Chunk wird ohnehin nur **einmal** geladen gehalten, im Budget zählt er aber für jeden Loader.

### Sprache & Texte
- **Jeder Spieler bekommt seine Sprache:** Mit `language: auto` (Standard) richtet sich MRchunks nach der Sprache, die der Spieler in Minecraft eingestellt hat - Deutsch (auch Österreich und Schweiz) bekommt Deutsch, Englisch bekommt Englisch. Für Sprachen ohne eigene Datei gilt `fallback-language` (Standard Englisch). Mit `language: de` oder `en` bekommen alle dieselbe Sprache.
- **Server-Sprache:** Discord-Logs, die Webkarte und Tickets in MRadmin haben keinen einzelnen Empfänger - sie kommen in `server-language` (Standard `en`). Auf einem deutschen Server also `server-language: de` setzen.
- Die Konsole ist immer Englisch.
- Alle Texte - Chat, Menüs, Item, Webkarte, Discord - stehen in `plugins/MRchunks/lang/<sprache>.yml`. Ein leerer Text (`""`) schaltet die Nachricht ab.
- **Eigene Sprache:** `lang/en.yml` kopieren (z.B. als `lang/fr.yml`) und übersetzen - mit `language: auto` bekommen französische Spieler sie automatisch. Neue Texte aus Updates werden automatisch ergänzt.

### Discord
Webhook anlegen (*Kanal bearbeiten → Integrationen → Webhooks*), `discord.enabled: true` setzen, die `webhook-url` eintragen, `/mrchunks reload` und mit `/mrchunks discord test` prüfen. Welche Ereignisse gesendet werden (Loader gesetzt, gelöscht, gesperrt, Einladungen, Admin-Aktionen …), stellst du unter `discord.events` ein.

---

## Webkarte (squaremap)
MRchunks zeigt alle Chunkloader auf **[squaremap](https://modrinth.com/plugin/squaremap)**, einer Webkarte mit echtem Minecraft-Gelände. Ohne squaremap gibt es keine Webkarte - alles andere funktioniert trotzdem.

1. squaremap in `plugins/` legen und den Server neu starten. **MRchunks erkennt es und richtet es einmalig passend ein** (schnelle Aktualisierung, keine Spieler auf der Karte, Weltauswahl Overworld/Nether/End). Danach kannst du squaremap frei anpassen.
2. **Einmal alles rendern**, damit bestehende Gebiete erscheinen:
   ```text
   /squaremap fullrender minecraft:overworld
   /squaremap fullrender minecraft:the_nether
   /squaremap fullrender minecraft:the_end
   ```
3. **Adresse festlegen** (`squaremap.address`), damit Spieler über den Knopf **Webkarte** einen funktionierenden Link bekommen. Leer lassen = MRchunks ermittelt sie selbst (`http://<server-ip>:8080/`). Port 8080 muss dann beim Hoster freigegeben sein.

Auf der Karte gibt es Ebenen, die jeder Besucher oben links ein- und ausschaltet:
- **Chunkloader** (aktive Loader in einer Farbe pro Owner), **Chunkloader (aus)** (grau) und **Chunkloader (gesperrt)** (rot) - mit `squaremap.split-layers: false` alles in einer Ebene. Gesperrte Loader sind immer zu sehen.
- **Farbe nach Status** (`squaremap.status-colors`): gesperrte Loader rot, auffällige (über einer Warngrenze) orange, während einer Server-Pause orange-gelb. Farben unter `locked-color`, `warn-color`, `paused-color`.
- **Beim Anklicken:** Owner, Position, Größe und Kosten im Budget, Status - bei gesperrten und auffälligen Loadern mit Grund (z.B. „gesperrt (Entities 900, Grenze 800)“) - und ob ein Dummy sitzt. Text in den Sprachdateien (`map-click-tooltip`, `map-tooltip-*`).
- **Dummy-Symbol:** Sitzt ein Dummy auf dem Loader, zeigt die Karte eine kleine Figur statt des Loader-Symbols (`squaremap.show-dummy-icon`).
- **Chunkraster** (Chunk-Linien, Regionsgrenzen, Koordinaten).

<details>
<summary>Eigene Domain, Reverse Proxy (nginx, Caddy, Cloudflare Tunnel)</summary>

| `address` | `reverse-proxy` | Link |
|---|---|---|
| `""` (Standard) | – | automatisch → `http://1.2.3.4:8080/` |
| `map.deinserver.de` | `false` | `http://map.deinserver.de:8080/` |
| `map.deinserver.de` | `true` | `https://map.deinserver.de/` |
| `https://deinserver.de/karte/` | egal | genau so, z.B. für einen Proxy mit Pfad |

Der Proxy leitet auf squaremaps Port weiter. Läuft er auf demselben Rechner, lass squaremap nur lokal lauschen (`internal-webserver.bind: 127.0.0.1` in `plugins/squaremap/config.yml`). Beispiel nginx:

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

Unter einem Pfad: `location /karte/ { proxy_pass http://127.0.0.1:8080/; }` und in MRchunks `address: "https://deinserver.de/karte/"`. Die öffentliche IP wird nur ermittelt, wenn weder `address` noch `server-ip` gesetzt ist - abschaltbar mit `squaremap.detect-public-ip: false`.
</details>

---

## Zusammenarbeit mit anderen Plugins
MRchunks läuft **allein**. Zusammen mit anderen MRtechnology-Plugins kann es mehr - die Plugins sprechen dafür über **MRadmin** miteinander:

| Funktion | Braucht |
|---|---|
| MRchunks im Dashboard, Spieler-Menü und Serverzustand von MRadmin, Seite **Chunkloader** in der Weboberfläche | MRadmin |
| Eigene Chunkloader im Browser verwalten (`/web`, Spielerseite) | MRadmin |
| Automatisches Ticket für den Owner bei einer Lag-Sperre | MRadmin |
| In der Arena-Welt von MRpvp lassen sich keine Loader setzen | MRadmin + MRpvp |
| Webkarte | squaremap |
| Dummy-Spieler zählen bei MRsleep, MRadmin und MRpvp nicht als Spieler | nichts |

## Häufige Fragen
**Ein Spieler kann keine Chunkloader holen.**
Er braucht `mrchunks.use` - am einfachsten `mrchunks.player` für die Default-Gruppe (siehe [Schnellstart](#schnellstart)).

**Die Loader eines Admins verschwinden beim Betreten.**
Die Gruppe hat über `*` auch `mrchunks.exempt`. Setz `mrchunks.exempt` für die Gruppe auf `false`.

**Ein Loader-Block ist trotzdem verschwunden (z.B. durch `/fill` oder WorldEdit).**
Solche Befehle umgehen den Schutz. MRchunks prüft deshalb regelmäßig und beim Laden des Chunks und stellt den Block wieder her (`loaders.restore-missing-blocks`).

**Ein Spieler kann seinen Loader nicht einschalten oder vergrößern.**
Sein Chunk-Budget ist voll. Im Menü **Einstellungen → Größe** steht, wie viel er nutzt und wie viel frei ist. Er kann einen anderen Loader ausschalten oder verkleinern - oder ein Admin erhöht sein Budget (`/mrchunks limit <spieler> budget <n>` oder `mrchunks.budget.<n>`).

**Der Dummy-Knopf ist grau / der Dummy setzt sich nicht.**
Ein Dummy braucht einen quadratischen Loader mit dem Block in der Mitte, mindestens 5×5. Im Menü **Einstellungen → Größe** die Mitte anklicken (alle Richtungen gleichmäßig).

**Was passiert, wenn ein Chunkloader-Item verloren geht?**
Nichts Schlimmes - gezählt werden nur **gesetzte** Loader. Ein neues Item holst du dir im Menü unter **Chunkloader holen**. Normale Smaragdblöcke sind übrigens keine Chunkloader, nur Items aus dem Menü.

**Funktioniert das im Nether und im End?**
Ja. Hast du in Paper `disable-world-ticking-when-empty` aktiviert, stell `loaders.load-method: forceload` ein.

**Ich habe einen Fehler gefunden oder einen Wunsch.**
Gern über das **[Formular auf der Website](https://mrtechnology.marcelr.at/report/mrchunks/)** - am besten mit der Ausgabe von `/mrchunks version`.

---

## Lizenz
MRchunks steht unter der **[MRtechnology Lizenz](LICENSE)** (source-available, nicht Open Source). Kurz gesagt:

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Nutzen - privat und gewerblich, auch auf Servern mit Spenden, Rängen oder Shops | Weiterveröffentlichen (auch verändert), z.B. auf Modrinth, Spigot, in Modpacks |
| Verändern und neu kompilieren | Verkaufen oder gegen Geld anbieten |
| An dein Team oder deinen Hoster weitergeben | Als eigenes Werk ausgeben, Lizenzhinweis entfernen |
| Verbesserungen per Pull Request einreichen | |

Die Nutzung erfolgt auf eigenes Risiko - leg vor Updates eine Sicherung an. Maßgeblich ist der vollständige Text in [LICENSE](LICENSE).

## Für Entwickler
Selbst bauen mit Java 25: `gradlew build` → `build/libs/MRchunks-mc<minecraft>-v<version>.jar`. Aufbau des Codes: [docs/DOKUMENTATION.md](docs/DOKUMENTATION.md), Branches und Releases: [docs/RELEASES.md](docs/RELEASES.md).
