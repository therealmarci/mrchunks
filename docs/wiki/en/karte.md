# Map (via MRadmin)

With **[MRadmin](https://mrtechnology.marcelr.at/wiki/mradmin/)** the player page has a tab **Map** with real Minecraft terrain. What players see there is described under [In the browser](browser.md).

| Layer / view | What | Setting |
|---|---|---|
| My chunk loaders | own loaders and the ones you are invited to - in the color of their state, right click switches on/off | `player-map.own` |
| Other chunk loaders | loaders of all other players (name, owner, size), without actions | `player-map.others` |
| Surroundings | small map in the opened loader card of the player page | `player-map.loader-preview` |

The map belongs entirely to MRadmin: MRadmin installs and updates squaremap itself, sets it up and shows the map only in its own web interface (see MRadmin's guide). Without MRadmin there is no map - everything else still works.

The button **Web map** in `/chunkloader` (`mrchunks.map`) sends the link to the map to the chat.

> [!WARNING]
> **Update from MRchunks 1.1 or older:** the web map moved to MRadmin. Update **MRadmin and MRchunks together**. The public squaremap page with all chunk loaders no longer exists, the settings `squaremap.*` are gone (MRadmin takes care of address and setup). `/mrchunks map` and `mrchunks.admin.map` no longer exist, use `/mradmin map` (`mradmin.map.admin`) instead.
