# Karte (über MRadmin)

Mit **[MRadmin](https://mrtechnology.marcelr.at/wiki/mradmin/)** hat die Spielerseite einen Tab **Karte** mit echtem Minecraft-Gelände. Was Spieler dort sehen, steht unter [Im Browser](browser.md).

| Ebene / Ansicht | Was | Einstellung |
|---|---|---|
| Meine Chunkloader | eigene Loader und die, zu denen man eingeladen ist - in der Farbe ihres Zustands, Rechtsklick schaltet an/aus | `player-map.own` |
| Andere Chunkloader | Loader aller anderen Spieler (Name, Besitzer, Größe), ohne Aktionen | `player-map.others` |
| Umgebung | kleine Karte in der geöffneten Loader-Karte der Spielerseite | `player-map.loader-preview` |

Die Karte gehört ganz zu MRadmin: MRadmin installiert und aktualisiert squaremap selbst, richtet es ein und zeigt die Karte nur in der eigenen Weboberfläche (siehe Anleitung von MRadmin). Ohne MRadmin gibt es keine Karte - alles andere funktioniert trotzdem.

Der Knopf **Webkarte** in `/chunkloader` (`mrchunks.map`) schickt den Link zur Karte in den Chat.

> [!WARNING]
> **Update von MRchunks 1.1 oder älter:** Die Webkarte ist zu MRadmin umgezogen. Aktualisiere **MRadmin und MRchunks gemeinsam**. Die öffentliche squaremap-Webseite mit allen Chunkloadern gibt es nicht mehr, die Einstellungen `squaremap.*` fallen weg (MRadmin übernimmt Adresse und Einrichtung). `/mrchunks map` und `mrchunks.admin.map` gibt es nicht mehr, stattdessen `/mradmin map` (`mradmin.map.admin`).
