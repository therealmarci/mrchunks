# MRchunks

**Chunkloader für Survival-Server:** Ein Block, und deine Farm läuft weiter - auch wenn du offline bist. Mit einem Chunk-Budget, das jeder selbst auf seine Loader verteilt, Einladungen für Mitspieler, Lag-Schutz und einer Karte im Browser (mit MRadmin).

**[⬇ Download](https://mrtechnology.marcelr.at/download/mrchunks/)** · **[📖 Wiki](https://mrtechnology.marcelr.at/wiki/mrchunks/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mrchunks/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - LuckPerms ist empfohlen, MRadmin optional (für Weboberfläche und Karte) |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | stabil (1.x) |

## Was kann MRchunks?
- **Chunkloader-Blöcke:** Bereich bleibt dauerhaft geladen - in allen Dimensionen, auch wenn der Spieler offline ist.
- **Chunk-Budget:** Jeder verteilt sein Budget (Standard 147 Chunks) selbst auf seine Loader, auch als Rechteck.
- **Mitspieler einladen**, Zweit-Accounts verknüpfen, optional **Dummy-Spieler** für Mob-Farmen.
- **Lag-Schutz** pro Loader, gestaffelte Abschaltung bei Serverlast, Server-Pause.
- **Menüs statt Befehle**, Admin-Menü, Discord-Logs, Update-Prüfung - mit MRadmin auch im Browser und auf der Karte.

## Schnellstart
1. **[MRchunks herunterladen](https://mrtechnology.marcelr.at/download/mrchunks/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten.
3. Rechte geben:
   ```text
   /lp group default permission set mrchunks.player true
   /lp group supporter permission set mrchunks.groups.support true
   /lp group staff permission set mrchunks.groups.staff true
   /lp group admin permission set mrchunks.groups.admin true
   ```
4. Fertig - jeder Spieler hat 147 Chunks Budget (drei 7×7-Loader), `/chunkloader` öffnet das Menü.

> [!WARNING]
> Gruppen mit `*` bekommen auch `mrchunks.exempt` - und deren Loader werden **gelöscht**: `/lp group admin permission set mrchunks.exempt false`.

**Update:** alte JAR durch die neue ersetzen, Server neu starten - die Config passt sich selbst an. Hinweise zu älteren Versionen im [Schnellstart des Wikis](https://mrtechnology.marcelr.at/wiki/mrchunks/schnellstart/).

## Ganze Anleitung im Wiki
| Für Spieler | Für Admins |
|---|---|
| [Chunkloader benutzen](https://mrtechnology.marcelr.at/wiki/mrchunks/chunkloader/) · [Größe und Budget](https://mrtechnology.marcelr.at/wiki/mrchunks/groesse/) | [Chunk-Budget](https://mrtechnology.marcelr.at/wiki/mrchunks/budget/) · [Lag-Schutz](https://mrtechnology.marcelr.at/wiki/mrchunks/lag-schutz/) · [Abschaltung & Pausen](https://mrtechnology.marcelr.at/wiki/mrchunks/abschaltung/) |
| [Mitspieler & Zweit-Accounts](https://mrtechnology.marcelr.at/wiki/mrchunks/mitspieler/) · [Dummy-Spieler](https://mrtechnology.marcelr.at/wiki/mrchunks/dummy/) | [Befehle](https://mrtechnology.marcelr.at/wiki/mrchunks/befehle/) · [Permissions](https://mrtechnology.marcelr.at/wiki/mrchunks/permissions/) · [Konfiguration](https://mrtechnology.marcelr.at/wiki/mrchunks/konfiguration/) |
| [Im Browser](https://mrtechnology.marcelr.at/wiki/mrchunks/browser/) | [Karte](https://mrtechnology.marcelr.at/wiki/mrchunks/karte/) · [Häufige Fragen](https://mrtechnology.marcelr.at/wiki/mrchunks/faq/) |

Die Seiten liegen als Markdown in [`docs/wiki/`](docs/wiki/) (Seitenbaum: [`docs/wiki/index.yml`](docs/wiki/index.yml)).

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
