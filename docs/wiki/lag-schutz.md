# Lag-Schutz

MRchunks misst alle 20 Sekunden, was im Bereich jedes **aktiven** Loaders passiert. **Jeder Loader wird für sich bewertet**, gegen die Grenzen seiner eigenen Fläche:

- **Bis 3×3** gelten feste Grenzen (`lag-protection.small-loaders`).
- **Ab 3×3** wachsen die Grenzen mit der Größe des Loaders. Die Werte in `lag-protection.limits` gelten für 49 Chunks (ein 7×7-Loader) und werden auf die Fläche umgerechnet.
- Die 3×3-Grenzen sind das **Minimum**: Kein Loader bekommt niedrigere Grenzen.
- Überschreitet ein Loader eine Grenze, betrifft das **nur diesen einen Loader** - die anderen Loader desselben Spielers laufen weiter.

## Grenzen
| Messwert | Was gezählt wird | Warnung bis 3×3 | Sperre bis 3×3 | Warnung (pro 49 Chunks) | Sperre (pro 49 Chunks) |
|---|---|---|---|---|---|
| `entities` | Mobs, Tiere, Loren, Items … | 60 | 150 | 100 | 250 |
| `items` | Items am Boden | 60 | 200 | 150 | 500 |
| `redstone` | Redstone-Änderungen pro Sekunde | 150 | 600 | 500 | 2000 |
| `hoppers` | Trichter-Transfers pro Sekunde | 100 | 400 | 300 | 1200 |
| `pistons` | Kolben-Bewegungen pro Sekunde | 15 | 50 | 40 | 150 |
| `spawns` | neue Mobs und Items pro Sekunde | 6 | 20 | 15 | 50 |

**Beispiel Entities (Sperre):** ein 3×3-Loader 150, ein 5×5-Loader ebenfalls 150 (250 × 25/49 = 128, aber nie unter dem 3×3-Wert), ein 7×7-Loader 250, ein 9×9-Loader 413 (250 × 81/49).

> [!NOTE]
> Die Grenzen für Entities, Items und Spawns sind so gerechnet, dass ein 7×7-Loader bis zur Warnung etwa **2 ms** und bis zur Sperre etwa **5 ms** Tick-Zeit kostet (gemessen auf einem kleinen Server: rund 0,085 ms pro geladenem Loader-Chunk mit Farmen). Redstone, Trichter und Kolben sind großzügiger, damit normale Farmen und Sortieranlagen laufen. Auf einem stärkeren Server kannst du alles in `lag-protection.limits` erhöhen.

Alle Grenzen stehen unter `lag-protection.limits` und `lag-protection.small-loaders.limits`, für wie viele Chunks sie gelten unter `lag-protection.limits-per-chunks` (49) und bis zu welcher Größe die festen gelten unter `lag-protection.small-loaders.max-chunks` (9). `0` schaltet eine Messung ab. Alle Grenzen werden auf ganze Zahlen gerundet.

### Eigene Grenzen für einen Loader

Braucht eine einzelne Farm mehr Luft (oder soll ein Loader strenger sein), legt das Team in der MRadmin-Weboberfläche **dauerhaft eigene Grenzen nur für diesen Loader** fest: Plugins → Chunkloader → Loader öffnen → **Grenzen anpassen**. Pro Messwert gibt es eine Warn- und eine Sperrgrenze; ein leeres Feld nimmt den Wert aus der Config, `0` schaltet die Messung für diesen Loader ab. Die nächste Messung nutzt die neuen Grenzen, die Kachel zeigt dann „eigene Grenzen“. **Wie in der Config** nimmt sie wieder zurück. Recht: `mrchunks.admin.limits` (in `mrchunks.groups.admin`); die Änderung steht im Protokoll und im Discord-Log (`loader-limits`).

## Was passiert
- **Über „Warnung“:** Der Loader taucht in `/mrchunks lag` auf (und in MRadmin), dazu ein Discord-Log. Sonst passiert nichts.
- **Über „Sperre“:** Gesperrt wird erst, wenn der Loader **mehrere Messungen hintereinander** darüber liegt (Standard 3, also 1 Minute) **und der Server dabei laggt**. Dann wird **nur dieser Loader** gesperrt. Eine große Farm, mit der der Server gut zurechtkommt, wird nicht gesperrt. Beim **Dreifachen** der Sperrgrenze wird auch ohne Server-Lag gesperrt (`lag-protection.force-lock-multiplier`, Standard `3`).
- **Gesperrt heißt:** Der Loader ist aus und kann nur von Admins entsperrt werden (Menü oder `/mrchunks unlock`). Der Owner wird informiert, mit MRadmin bekommt er automatisch ein **Ticket** mit allen Details.
- Das Menü jedes aktiven Loaders zeigt die letzte Messung gegen die Grenzen dieses Loaders - so können Spieler ihre Farm selbst einschätzen.
- **Nach dem Serverstart** wartet der Lag-Schutz **3 Minuten**, bevor er etwas bewertet (`lag-protection.startup-delay-seconds`, Standard `180`, `0` = sofort) - beim Hochfahren ist die Tick-Zeit kurz sehr hoch.

Normale Farmen bleiben unter diesen Grenzen, große Mob-Farmen fallen auf. Ist der Server trotzdem ausgelastet, weil viele Loader zusammen zu viel sind, greift die [gestaffelte Abschaltung](abschaltung.md#gestaffelte-abschaltung).

## Verlauf in MRadmin
In der MRadmin-Weboberfläche zeigt jede Messwert-Kachel auf der Seite eines Loaders ihren Verlauf als kleine Kurve. Ein Klick öffnet das große **Diagramm** mit Warn- und Sperrgrenze (Zeitraum 1m, 10m, 30m, 1h, 6h, 12h).

- Pausieren, eine Leiste mit allen gespeicherten Daten (bis 12 Stunden), Klick springt dorthin, Mausrad und Ziehen verschieben genau.
- Je größer der Zeitraum, desto gröber die Punkte (1m jede Messung, 10m alle 5 s, 30m alle 20 s, 1h jede Minute, 6h alle 5 min, 12h alle 10 min; jeder Punkt zeigt die Spitze seiner Zeit). War der Server offline, zählt das als aus.
- Der Verlauf liegt in MRadmins Speicher (Datei, SQLite oder MySQL, je nach `storage.type`) und übersteht Neustarts.
- Farben: grün normal, orange über der Warngrenze, rot über der Sperrgrenze; aus grau, gesperrt dunkelrot.

## Live-Ansicht (MRadmin)
Ist die Seite eines Loaders in der MRadmin-Weboberfläche offen (`mrchunks.admin.live`), wird **genau dieser Loader** jede Sekunde gemessen. Kacheln und Diagramme laufen dann live mit, sichtbar am grünen „Live“.

- **Kostet nur, solange jemand zuschaut:** Es läuft pro Loader einmal, egal wie viele zuschauen, und endet wenige Sekunden nach dem Schließen der Seite. Ein Tab im Hintergrund zählt nicht.
- **Nur Anzeige:** Live-Messungen zählen nie für Sperren. Der Server hält sie 15 Minuten im Arbeitsspeicher, dauerhaft gespeichert werden sie nicht.
- **Einstellbar** unter `lag-protection.live-view`: `enabled`, `seconds` (Abstand, 1 bis 10), `max-loaders` (höchstens so viele Loader gleichzeitig, Standard 5).
- `/mrchunks lag` zeigt, wie viele Loader gerade live gemessen werden und was eine Runde den Server kostet.

## Limit-Sperre aussetzen
Ein Admin kann die Sperre wegen überschrittener Grenzen für **einen einzelnen Loader** vorübergehend aussetzen, höchstens **24 Stunden** (`mrchunks.admin.suspendlock`), z.B. für eine große Farm bei einem Event.

- **Wo:** im Loader-Menü die Uhr unten in der Mitte, in der MRadmin-Weboberfläche auf der Seite des Loaders oder mit `/mrchunks suspendlock <spieler> <nr> <dauer|off>`.
- **War der Loader deswegen schon gesperrt,** wird er entsperrt. Er bleibt aus, bis ihn jemand einschaltet.
- **Weiter gilt:** Gemessen und gewarnt wird weiter. Die gestaffelte Abschaltung, die Sperre nach wiederholter Abschaltung („Zu oft“) und die Server-Pause gelten auch für diesen Loader. Ein nach wiederholter Abschaltung gesperrter Loader lässt sich nicht aussetzen, nur entsperren.
- **Ende:** Die Aussetzung übersteht Neustarts, endet von selbst und lässt sich vorzeitig beenden. Danach braucht es wieder die üblichen Messungen hintereinander, bis der Loader gesperrt wird. Der Owner wird informiert, dazu ein Discord-Log. `/mrchunks lag` zeigt alle laufenden Aussetzungen.
- **Auswahl der Dauer:** `lag-protection.lock-suspension-durations` (Standard `1h`, `3h`, `6h`, `12h`, `24h`; von `1m` bis `24h`). Der längste Eintrag ist auch die Obergrenze für den Befehl.
