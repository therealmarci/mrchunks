# Configuration

Easiest in the browser: with **MRadmin** under **Plugins → MRchunks → Configuration** - every setting with an explanation, saving reloads MRchunks right away (permission `mradmin.config`). Everything is also commented in `plugins/MRchunks/config.yml`; you apply changes by hand with `/mrchunks reload`.

| Section | The most important settings | More |
|---|---|---|
| `general` | `language` (default `auto`), `fallback-language`, `server-language` | [Languages & messages](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `loaders` | `default-budget` (147 chunks), `default-reach` (3 = new loaders 7×7), `max-reach` (7 = at most 15×15), `max-loaders` (30), `overlap`, `load-method`, `restore-missing-blocks` | [Chunk budget](budget.md) |
| `invites` | on/off, duration choices, at most this many members | [Friends](mitspieler.md) |
| `linked-accounts` | on/off, how many alt accounts | [Friends](mitspieler.md#link-alt-accounts) |
| `dummies` | on/off (default off), limit, skin, sitting | [Dummy players](dummy.md) |
| `lag-protection` · `staged-shutdown` · `server-pause` | Measurement and lock limits, MSPT steps of the staged shutdown, TPS thresholds | [Lag protection](lag-schutz.md), [Shutdown & pauses](abschaltung.md) |
| `item` · `border` | Looks of the loader and the border, `item.block-choices`: blocks players may choose for their loaders (empty list `[]` = no choice) | [Using chunk loaders](chunkloader.md#names-block-and-border) |
| `player-map` | Layers on the map of the player page (MRadmin): `own`, `others`, `loader-preview` | [Map](karte.md) |
| `discord` · `updates` · `gui` | Logs, update notices, menus | [Discord](#discord) |

- **Nether and End:** if you enabled `disable-world-ticking-when-empty` in Paper, set `loaders.load-method: forceload`.
- **`config-version`** please do not change - MRchunks uses it to know whether the file has to be updated.

## Language & messages
- `general.language: auto` (default): every player gets the language of their Minecraft client, otherwise `fallback-language` (default English).
- Discord logs and tickets in MRadmin have no single recipient - they use `general.server-language` (default `en`). On a German server, set `general.server-language: de`.
- All messages - chat, menus, item, player page, Discord - are in `plugins/MRchunks/lang/<language>.yml`. An empty message (`""`) turns it off. More under [Languages & messages](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/).

## Discord
Create a webhook (*Edit channel → Integrations → Webhooks*), set `discord.enabled: true`, enter the `webhook-url`, `/mrchunks reload` and check with `/mrchunks discord test`. Which events are sent (loader placed, deleted, locked, invitations, admin actions …) you set under `discord.events`.

> [!TIP]
> **No own webhook?** With MRadmin it also works through its Discord log: `discord.enabled: true`, leave `webhook-url` empty and set `discord.use-mradmin-webhook: true`.
>
> With MRadmin every event is also in the **log** of the web interface (up to 14 days) - even when Discord is off.

## All settings
Every setting of `config.yml` with its default - the same fields as in MRadmin's browser form. The list is generated automatically from the plugin and therefore always matches this version.

<!-- auto:config -->
