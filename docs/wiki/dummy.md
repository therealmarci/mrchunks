# Dummy-Spieler

Ein Chunkloader hält Chunks geladen - für den Server steht dort aber **kein Spieler**. Mobs sind deshalb fast inaktiv, und es spawnt nichts. Ein **Dummy** ist ein künstlicher Spieler, der auf dem Loader sitzt und als Spieler in der Nähe zählt.

- **Einschalten:** Menü des Loaders → **Dummy einschalten**. Er sitzt dort, solange der Loader aktiv ist.
- **Aussehen:** dein Skin, Name `[D<nr>]<dein Name>`, sitzend.
- **Sonst kein Spieler:** unverwundbar, von Mobs ignoriert, nicht in der Tab-Liste und nicht in der Spielerzahl. MRsleep, MRadmin und MRpvp ignorieren ihn.
- **Limit:** standardmäßig 1 pro Spieler, mehr per Permission oder Admin-Befehl.
- **Nur auf quadratischen Loadern ab 5×5, Block in der Mitte:** Ein Dummy simuliert wie ein Spieler ein Quadrat um sich herum. Bei einem Rechteck oder einem kleineren Loader würde er Chunks außerhalb des Bereichs simulieren, die dein Budget gar nicht bezahlt. Sitzt ein Dummy auf dem Loader, lässt sich die Größe deshalb nur gleichmäßig in alle Richtungen ändern.

> [!TIP]
> Der Dummy-Knopf ist grau? Im Menü **Einstellungen → Größe** die Mitte anklicken, damit der Loader quadratisch wird (mindestens 5×5).

## Für Admins
> [!NOTE]
> Dummies sind **standardmäßig ausgeschaltet** (`dummies.enabled: false`). Ein Dummy kostet so viel Leistung wie ein echter Spieler an dieser Stelle. Er nutzt Server-Interna von Paper: Passt die MRchunks-Version nicht genau zur Server-Version, bleiben Dummies aus (Hinweis in der Konsole) - alles andere funktioniert normal.

- Limit: `dummies.default-limit`, Permission `mrchunks.dummies.<n>` bzw. `mrchunks.dummies.unlimited`, oder `/mrchunks limit <spieler> dummy <n>` (siehe [Chunk-Budget](budget.md)).
- Weitere Einstellungen unter `dummies` in der [Konfiguration](konfiguration.md) (Skin, sitzend).
- Ein Dummy blockiert das Entladen einer Welt nicht: Mit `/mv unload <welt> --remove-players` verlässt er die Welt und setzt sich beim Laden wieder auf seinen Loader.
