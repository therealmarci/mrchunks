# Chunkloader benutzen

## So funktioniert's
1. **`/chunkloader`** (oder `/cl`) öffnet dein Menü. Unten rechts holt **Chunkloader holen** einen Loader ins Inventar (Linksklick 1, Shift bis zu 64 - höchstens 30 Loader insgesamt). Dort siehst du auch dein **Budget**. **Chunkloader zurückgeben** daneben nimmt Items wieder zurück.
2. **Block platzieren** - ab sofort bleiben die `7×7` Chunks rundherum geladen. Reicht dein freies Budget nicht mehr für 7×7, wird der Loader so groß, wie es noch geht.
3. **Rechtsklick** auf den Block öffnet sein Menü: oben die Infos, darunter **Mitglieder**, **an/aus**, **Grenze anzeigen** (Enderperle), **Dummy** und **Einstellungen**. In den Einstellungen liegen **Größe**, **Block ändern**, **Umbenennen** und **Löschen**. **Sneak + Rechtsklick** verhält sich normal, du kannst also weiter Blöcke daran bauen.
4. **Löschen** geht nur über das Menü (zweimal klicken). Gelöscht ist endgültig.

Der Block ist unzerstörbar: kein Abbauen, keine Explosionen, Kolben, Wither oder Endermen.

## Die Menüleiste in /chunkloader
Von links nach rechts:

| Knopf | Wofür |
|---|---|
| **Mitglieder für alle Loader** | Spieler für alle deine Loader einladen - siehe [Mitspieler](mitspieler.md) |
| **Mit mir geteilt** | Loader anderer Spieler, zu denen du eingeladen bist |
| **Verknüpfte Accounts** | Zweit-Account verknüpfen oder lösen |
| **Webkarte** | Link zur Karte der Spielerseite im Chat (nur mit MRadmin) |
| **Chunkloader holen** / **zurückgeben** | Loader-Items holen oder zurückgeben |

Hast du mehr als 45 Loader, erscheinen Pfeile zum Blättern. Je nach Server fehlen Knöpfe, für die du keine Rechte hast.

## Was der Block im Menü bedeutet
| Block | Status |
|---|---|
| Smaragdblock | Alles OK |
| Grauer Beton | Ausgeschaltet |
| Magmablock | Über einer Warnschwelle des Lag-Schutzes - die Farm ist gerade auffällig |
| Redstone-Block | Wegen Lag gesperrt - nur das Team kann ihn entsperren |
| Oranger Beton | Durch Lags deaktiviert: bleibt an, lädt von selbst wieder |
| Spinnennetz | Pausiert, weil der ganze Server laggt oder das Team alle Loader pausiert hat |

Das Menü jedes aktiven Loaders zeigt seine letzte Messung gegen seine Grenzen - so kannst du deine Farm selbst einschätzen.

## Was läuft in geladenen Chunks?
- **Läuft:** Redstone, Trichter, Öfen, Tiere und andere Entities, Pflanzenwachstum - alles, was Zufalls-Ticks braucht.
- **Braucht trotzdem einen Spieler in der Nähe:** natürliches Mob-Spawning, Spawner und volle Mob-KI (Mobs bewegen sich ohne Spieler kaum und laufen z.B. nicht durch Portale). Dafür gibt es [Dummy-Spieler](dummy.md).

## Namen, Block und Grenze
- Jeder Loader bekommt die **kleinste freie Nummer** („Chunkloader 4“). Über **Umbenennen** gibst du ihm einen eigenen Namen - er erscheint im Chat, in Menüs, auf der Karte und in Discord.
- **Block ändern** wählt, als welcher Block dein Loader in der Welt steht (z.B. Diamant-, Gold- oder Netheritblock). Nur der Block in der Welt ändert sich - in Menüs und als Item bleibt es der Smaragdblock. Welche Blöcke es gibt, legt der Server fest.
- **Grenze anzeigen** zeichnet einen leuchtenden Zaun an die Außenkante des Bereichs, sichtbar auch durch Blöcke und nur für dich. Er verschwindet nach einer Weile von selbst.
