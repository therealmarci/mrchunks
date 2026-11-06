# Shutdown & pauses

The [lag protection](lag-schutz.md) judges every loader on its own. This page is about the whole server: when many loaders together are too much or the server lags for other reasons.

## Staged shutdown
Many loaders can slow the server down **together**, even if each of them stays below its limits. For this MRchunks watches the **server's tick time (MSPT)** averaged over one minute - single spikes trigger nothing:

| Average MSPT | What happens |
|---|---|
| up to 40 | everything fine |
| above 40 | **Warning** to the team in chat and on Discord. It stays for at least 5 minutes. |
| above 45 | The loader with the **highest load** (share of its limits) is **"disabled due to lag"**. Every minute the next one follows, until the MSPT is at 40 or below again. |
| from 70 | **Hard cap:** **all** active loaders are disabled due to lag right away (measured over 10 seconds, so it reacts quickly). They come back one by one the same way afterwards. |

- **Disabled due to lag** is **not a lock**: the loader stays switched on but releases its chunks (and its dummy disappears). It stays off for **at least 5 minutes**.
- **Coming back:** when the MSPT is at 40 or below again afterwards, the loaders load again **one after the other** (one per minute, the last one switched off first). If the load rises again meanwhile, the return stops. Menu, `/mrchunks lag`, player page and MRadmin show the expected return time.
- **Too often:** if the same loader is switched off **more than twice within 60 minutes**, it is **locked** the third time (only admins can unlock it, with MRadmin the owner gets a ticket). Shutdowns by the hard cap do not count for this.
- **Who learns about it:** the owner (if offline, at the next login), the team (`mrchunks.admin.gui`) in chat and the Discord log. `/mrchunks lag` shows the current server load and all loaders that are switched off.
- A restart brings back all loaders that were switched off.
- **Configurable** under `staged-shutdown`: `warn-mspt`, `shutdown-mspt`, `average-seconds`, `step-seconds`, `min-minutes`, `max-shutdowns`, `window-minutes`, `hard-cap-mspt` (`0` = no hard cap), `hard-cap-seconds`.
- **After the server start** the staged shutdown and the hard cap wait **3 minutes** (`lag-protection.startup-delay-seconds`).
- **Switch off in the MRadmin web:** under **Chunk loaders → Control** an admin can switch the staged shutdown including the hard cap off and on again (`mrchunks.admin.monitoring`). While it is off, all loaders that were switched off load again right away; the limits per loader still apply. The setting survives a restart.

## Pause all loaders (team)
Under **Chunk loaders → Control** in the MRadmin web an admin can **temporarily pause all chunk loaders** (`mrchunks.admin.pause`), e.g. in case of problems or before maintenance. All chunks are released right away, but the loaders stay switched on and load again as soon as the pause is lifted in the web. The pause also survives a restart. Owners of active loaders and the team are informed in chat, plus a Discord log. The menu shows "⏸ Paused by the team".

## Server pause
If the **whole server** lags - no matter why -, MRchunks pauses **all** loaders:
- **Pause** when the TPS is below 15 for 30 seconds. Short lag spikes trigger nothing. Right before that, all loaders are measured once more, and clear culprits are locked.
- **Resume** when the TPS is at least 18 again for 60 seconds. Nobody has to switch anything on again.
- Owners and admins are informed, the menu shows "⏸ Paused". Configurable under `server-pause`.
