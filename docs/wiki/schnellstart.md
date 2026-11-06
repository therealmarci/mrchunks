# Schnellstart

1. **[MRchunks herunterladen](https://mrtechnology.marcelr.at/download/mrchunks/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten. Dabei entstehen `plugins/MRchunks/config.yml` und die Sprachdateien in `plugins/MRchunks/lang/`.
3. **Spielern die Rechte geben** - ohne sie dürfen nur OPs Chunkloader nutzen:
   ```text
   /lp group default permission set mrchunks.player true
   ```
   Fürs Team: `mrchunks.groups.support`, `.staff` bzw. `.admin` - siehe [Permissions](permissions.md).
4. Fertig. Jeder Spieler hat standardmäßig **147 Chunks** Budget (drei 7×7-Loader) und darf bis zu 30 Loader setzen. Anpassen in der Config, dann `/mrchunks reload` (oder mit MRadmin im Browser, siehe [Konfiguration](konfiguration.md)).

> [!WARNING]
> Gruppen mit `*` oder `mrchunks.*` bekommen in LuckPerms auch `mrchunks.exempt` - und deren Loader werden **gelöscht**. Setz dort ausdrücklich `/lp group admin permission set mrchunks.exempt false`. Mehr unter [Wildcards](permissions.md#wildcards).

## Update
Die alte JAR durch die neue ersetzen und den Server neu starten. Die Config wird automatisch angepasst: Deine Werte bleiben, neue Optionen kommen dazu, und die alte Datei wird als `config.v<N>.backup.yml` gesichert.

> [!WARNING]
> **Update von 1.1 oder älter:** Die Webkarte ist zu MRadmin umgezogen - aktualisiere MRadmin und MRchunks gemeinsam. Details unter [Karte](karte.md).

> [!WARNING]
> **Update auf das Chunk-Budget:** Die Limits `mrchunks.limit.<n>`, `mrchunks.active.<n>` (und `.unlimited`) gibt es nicht mehr - stattdessen `mrchunks.budget.<n>`. Vorhandene Loader behalten ihre Größe, und niemand verliert etwas: Das Budget wird beim Update aus dem alten Aktiv-Limit berechnet (3 aktive 7×7-Loader = 147 Chunks), auch für Admin-Limits. **Permissions musst du selbst umstellen**, z.B. `/lp group vip permission unset mrchunks.active.5` und `/lp group vip permission set mrchunks.budget.245 true` (5 × 49). Details unter [Chunk-Budget](budget.md).
