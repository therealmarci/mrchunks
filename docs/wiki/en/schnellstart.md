# Quick start

1. **[Download MRchunks](https://mrtechnology.marcelr.at/download/mrchunks/)** and put the JAR into the `plugins/` folder.
2. Start the server. This creates `plugins/MRchunks/config.yml` and the language files in `plugins/MRchunks/lang/`.
3. **Give players the permissions** - without them only OPs may use chunk loaders:
   ```text
   /lp group default permission set mrchunks.player true
   ```
   For the team: `mrchunks.groups.support`, `.staff` or `.admin` - see [Permissions](permissions.md).
4. Done. By default every player has a budget of **147 chunks** (three 7×7 loaders) and may place up to 30 loaders. Adjust it in the config, then `/mrchunks reload` (or in the browser with MRadmin, see [Configuration](konfiguration.md)).

> [!WARNING]
> Groups with `*` or `mrchunks.*` also get `mrchunks.exempt` in LuckPerms - and their loaders are **deleted**. Set `/lp group admin permission set mrchunks.exempt false` explicitly there. More under [wildcards](permissions.md#wildcards).

## Update
Replace the old JAR with the new one and restart the server. The config is adapted automatically: your values stay, new options are added, and the old file is backed up as `config.v<N>.backup.yml`.

> [!WARNING]
> **Update from 1.1 or older:** the web map moved to MRadmin - update MRadmin and MRchunks together. Details under [Map](karte.md).

> [!WARNING]
> **Update to the chunk budget:** the limits `mrchunks.limit.<n>`, `mrchunks.active.<n>` (and `.unlimited`) no longer exist - use `mrchunks.budget.<n>` instead. Existing loaders keep their size, and nobody loses anything: on the update the budget is calculated from the old active limit (3 active 7×7 loaders = 147 chunks), also for admin limits. **You have to change the permissions yourself**, e.g. `/lp group vip permission unset mrchunks.active.5` and `/lp group vip permission set mrchunks.budget.245 true` (5 × 49). Details under [Chunk budget](budget.md).
