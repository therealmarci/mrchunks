# FAQ

### A player cannot get chunk loaders.
They need `mrchunks.use` - easiest is `mrchunks.player` for the default group (see [Quick start](schnellstart.md)).

### An admin's loaders disappear when they join.
Through `*` the group also has `mrchunks.exempt`. Set `mrchunks.exempt` to `false` for the group - see [wildcards](permissions.md#wildcards).

### A loader block disappeared anyway (e.g. through `/fill` or WorldEdit).
Such commands bypass the protection. That is why MRchunks checks regularly and when the chunk loads and restores the block (`loaders.restore-missing-blocks`).

### A player cannot switch on or enlarge their loader.
Their chunk budget is full. The menu **Settings → Size** shows how much they use and how much is free. They can switch off or shrink another loader - or an admin raises their budget (`/mrchunks limit <player> budget <n>` or `mrchunks.budget.<n>`, see [Chunk budget](budget.md)).

### The dummy button is gray / the dummy does not sit down.
A dummy needs a square loader with the block in the middle, at least 5×5. In the menu **Settings → Size** click the middle (all directions evenly). If dummies are off on the server (`dummies.enabled`), the button does not exist.

### My loader is "disabled due to lag" - what does that mean?
The server was under load, and your loader was the most expensive one right then. It stays switched on and loads again by itself after at least 5 minutes, as soon as the server has recovered - you do not have to do anything. If this happens more than twice in an hour, it is locked; then it helps to make the farm smaller. More under [Shutdown & pauses](abschaltung.md).

### What happens if a chunk loader item gets lost?
Nothing bad - only **placed** loaders are counted. You get a new item in the menu under **Get chunk loaders**. By the way, normal emerald blocks are not chunk loaders, only items from the menu.

### Does it work in the Nether and the End?
Yes. If you enabled `disable-world-ticking-when-empty` in Paper, set `loaders.load-method: forceload`.

### I found a bug or have a wish.
Gladly via the **[form on the website](https://mrtechnology.marcelr.at/report/mrchunks/)** - ideally with the output of `/mrchunks version`.
