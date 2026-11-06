# Größe und Chunk-Budget

Jeder aktive Loader kostet so viele Chunks aus deinem **Budget**, wie er groß ist: 7×7 kostet 49, 3×3 kostet 9, 3×15 kostet 45. **Ausgeschaltete Loader kosten nichts.** Mit dem Standard-Budget von 147 Chunks kannst du z.B. drei 7×7-Loader betreiben - oder sechzehn 3×3-Loader.

## Größe einstellen
Im Menü eines Loaders öffnet **Einstellungen → Größe** die Einstellung:
- **Pfeile Norden, Süden, Westen, Osten:** Linksklick +1 Chunk in diese Richtung, Rechtsklick −1. So wird der Bereich zum Rechteck, und der Block muss nicht in der Mitte stehen - z.B. am Rand einer langen Farm.
- **Mitte:** alle vier Richtungen auf einmal.
- Oben steht, wie viel Budget du nutzt und wie viel frei ist. **Grenze anzeigen** zeigt den Rand und folgt jeder Änderung.
- Vergrößern geht nur mit genug freiem Budget und bis zur Höchstgröße des Servers (Standard 15×15). Ist dein Budget voll, schalte einen anderen Loader aus oder verkleinere einen.
- Die Größe ändern darfst nur du - Eingeladene nicht.

> [!TIP]
> Mehr Budget gibt es vom Team (per Rang oder Befehl). Wie viel du hast, steht unten rechts in `/chunkloader`.

## Gut zu wissen
- Neue Loader sind 7×7 groß. Reicht dein freies Budget nicht, bekommen sie das größte Quadrat, das noch passt; ist gar nichts mehr frei, werden sie gesetzt, bleiben aber **aus**.
- Überschneiden sich zwei Loader, zählen die gemeinsamen Chunks im Budget für jeden Loader voll.
- Sitzt ein [Dummy](dummy.md) auf dem Loader, lässt sich die Größe nur gleichmäßig in alle Richtungen ändern.
- Schaltet ein eingeladener Mitspieler deinen Loader an, kostet das **dein** Budget.
