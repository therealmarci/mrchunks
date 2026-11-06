# Lag protection

Every 20 seconds MRchunks measures what happens in the area of every **active** loader. **Every loader is judged on its own**, against the limits of its own area:

- **Up to 3×3** fixed limits apply (`lag-protection.small-loaders`).
- **From 3×3** the limits grow with the size of the loader. The values in `lag-protection.limits` apply to 49 chunks (one 7×7 loader) and are scaled to the area.
- The 3×3 limits are the **minimum**: no loader gets lower limits.
- If a loader exceeds a limit, this affects **only this one loader** - the other loaders of the same player keep running.

## Limits
| Measurement | What is counted | Warn up to 3×3 | Lock up to 3×3 | Warn (per 49 chunks) | Lock (per 49 chunks) |
|---|---|---|---|---|---|
| `entities` | Mobs, animals, minecarts, items … | 40 | 100 | 100 | 250 |
| `items` | Items on the ground | 60 | 200 | 150 | 500 |
| `redstone` | Redstone changes per second | 150 | 600 | 500 | 2000 |
| `hoppers` | Hopper transfers per second | 100 | 400 | 300 | 1200 |
| `pistons` | Piston moves per second | 15 | 50 | 40 | 150 |
| `spawns` | New mobs and items per second | 6 | 20 | 15 | 50 |

**Example entities (lock):** a 3×3 loader 100, a 5×5 loader 128 (250 × 25/49), a 7×7 loader 250, a 9×9 loader 413 (250 × 81/49).

> [!NOTE]
> The limits for entities, items and spawns are calculated so that a 7×7 loader costs about **2 ms** of tick time up to the warning and about **5 ms** up to the lock (measured on a small server: roughly 0.085 ms per loaded loader chunk with farms). Redstone, hoppers and pistons are more generous so that normal farms and sorting systems run. On a stronger server you can raise everything in `lag-protection.limits`.

All limits are under `lag-protection.limits` and `lag-protection.small-loaders.limits`, how many chunks they apply to under `lag-protection.limits-per-chunks` (49) and up to which size the fixed ones apply under `lag-protection.small-loaders.max-chunks` (9). `0` turns a measurement off. All limits are rounded to whole numbers.

## What happens
- **Above "warn":** the loader shows up in `/mrchunks lag` (and in MRadmin), plus a Discord log. Nothing else happens.
- **Above "lock":** a loader is only locked when it is above it for **several measurements in a row** (default 3, so 1 minute) **and the server lags meanwhile**. Then **only this loader** is locked. A big farm the server copes with well is not locked. At **three times** the lock limit it is locked even without server lag (`lag-protection.force-lock-multiplier`, default `3`).
- **Locked means:** the loader is off and can only be unlocked by admins (menu or `/mrchunks unlock`). The owner is informed, with MRadmin they automatically get a **ticket** with all details.
- The menu of every active loader shows the last measurement against the limits of this loader - so players can judge their farm themselves.
- **After the server start** the lag protection waits **3 minutes** before judging anything (`lag-protection.startup-delay-seconds`, default `180`, `0` = right away) - while starting up, the tick time is very high for a short while.

Normal farms stay below these limits, big mob farms stand out. If the server is still under load because many loaders together are too much, the [staged shutdown](abschaltung.md#staged-shutdown) steps in.

## History in MRadmin
In the MRadmin web interface every measurement tile on a loader's page shows its history as a small curve. A click opens the big **chart** with warn and lock limit (time range 1m, 10m, 30m, 1h, 6h, 12h).

- Pause, a bar with all stored data (up to 12 hours), a click jumps there, mouse wheel and dragging move precisely.
- The bigger the time range, the coarser the points (1m every measurement, 10m every 5 s, 30m every 20 s, 1h every minute, 6h every 5 min, 12h every 10 min; every point shows the peak of its time). If the server was offline, that counts as off.
- The history is kept in MRadmin's storage (file, SQLite or MySQL, depending on `storage.type`) and survives restarts.
- Colors: green normal, orange above the warn limit, red above the lock limit; off gray, locked dark red.

## Live view (MRadmin)
While a loader's page is open in the MRadmin web interface (`mrchunks.admin.live`), **exactly this loader** is measured every second. Tiles and charts then run live, visible by the green "Live".

- **Only costs while someone watches:** it runs once per loader, no matter how many watch, and ends a few seconds after the page is closed. A tab in the background does not count.
- **Display only:** live measurements never count for locks. The server keeps them in memory for 15 minutes, they are not stored permanently.
- **Configurable** under `lag-protection.live-view`: `enabled`, `seconds` (interval, 1 to 10), `max-loaders` (at most this many loaders at the same time, default 5).
- `/mrchunks lag` shows how many loaders are measured live right now and what one round costs the server.

## Suspend the limit lock
An admin can temporarily suspend the lock for exceeded limits for **a single loader**, at most **24 hours** (`mrchunks.admin.suspendlock`), e.g. for a big farm during an event.

- **Where:** in the loader menu the clock at the bottom middle, in the MRadmin web interface on the loader's page or with `/mrchunks suspendlock <player> <no> <duration|off>`.
- **If the loader was already locked because of this,** it is unlocked. It stays off until someone switches it on.
- **Still applies:** measuring and warning go on. The staged shutdown, the lock after repeated shutdowns ("Too often") and the server pause also apply to this loader. A loader locked after repeated shutdowns cannot be suspended, only unlocked.
- **End:** the suspension survives restarts, ends by itself and can be ended early. After that it again takes the usual measurements in a row until the loader is locked. The owner is informed, plus a Discord log. `/mrchunks lag` shows all running suspensions.
- **Choice of duration:** `lag-protection.lock-suspension-durations` (default `1h`, `3h`, `6h`, `12h`, `24h`; from `1m` to `24h`). The longest entry is also the upper limit for the command.
