# Überblick

**Chunkloader für Survival-Server:** Ein Block, und deine Farm läuft weiter - auch wenn du offline bist. Mit einem Chunk-Budget, das jeder selbst auf seine Loader verteilt, Einladungen für Mitspieler, Lag-Schutz und einer Karte im Browser (mit MRadmin).

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - LuckPerms ist empfohlen, MRadmin optional (für Weboberfläche und Karte) |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | stabil (1.x) |

## Was kann MRchunks?
- **Chunkloader-Blöcke:** Einmal platziert, bleibt der Bereich drumherum dauerhaft geladen - in allen Dimensionen, auch wenn der Spieler offline ist. → [Chunkloader benutzen](chunkloader.md)
- **Größe frei wählbar:** Jeder Spieler hat ein **Chunk-Budget** (Standard 147 Chunks = drei 7×7-Loader) und stellt die Größe jedes Loaders selbst ein - auch als Rechteck, z.B. 3×15 für eine lange Farm. → [Größe und Chunk-Budget](groesse.md)
- **Mitspieler einladen:** Farm-Partner dürfen deine Loader an- und ausschalten. **Zweit-Accounts** teilen sich Loader und Budget. → [Mitspieler & Zweit-Accounts](mitspieler.md)
- **Dummy-Spieler** (optional): ein künstlicher Spieler auf dem Loader, damit Mobs aktiv bleiben und Farmen wie mit einem echten Spieler daneben laufen. → [Dummy-Spieler](dummy.md)
- **Unzerstörbar:** kein Abbauen, keine Explosionen, Kolben, Wither oder Endermen.
- **Lag-Schutz:** Loader, die den Server ausbremsen, werden erkannt und gesperrt. Ist der Server ausgelastet, schaltet die gestaffelte Abschaltung die aufwendigsten Loader vorübergehend ab. → [Lag-Schutz](lag-schutz.md), [Abschaltung & Pausen](abschaltung.md)
- **Karte** mit echtem Gelände auf der Spielerseite von MRadmin. → [Karte](karte.md)
- **Menüs statt Befehle** für Spieler, ein Admin-Menü für das Team, **Discord-Logs** und **Update-Prüfung**.

## Wo anfangen?
| Du bist … | Lies zuerst |
|---|---|
| **Spieler** | [Chunkloader benutzen](chunkloader.md), dann [Größe und Chunk-Budget](groesse.md) |
| **Server-Betreiber** | [Schnellstart](schnellstart.md), dann [Chunk-Budget](budget.md), [Lag-Schutz](lag-schutz.md) und [Permissions](permissions.md) |
| **Im Team** (Support, Staff) | [Befehle & Admin-Menü](befehle.md) |
