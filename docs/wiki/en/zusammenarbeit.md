# Working with other plugins

MRchunks runs **on its own**. Together with other MRtechnology plugins it can do more - the plugins talk to each other through **MRadmin** ([how that works](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/)).

| Feature | Needs |
|---|---|
| MRchunks in MRadmin's dashboard, player menu and server status, page **Chunk loaders** in the web interface | MRadmin |
| Manage your own chunk loaders in the browser (`/web`, player page) | MRadmin |
| Configure MRchunks in the browser (Plugins → MRchunks → Configuration) | MRadmin |
| Automatic ticket for the owner on a lag lock | MRadmin |
| No loaders can be placed in MRpvp's arena world | MRadmin + MRpvp |
| Chunk loaders on the map of the player page | MRadmin |
| Dummy players do not count as players for MRsleep, MRadmin and MRpvp | nothing |
| Events in the **log** of the MRadmin web interface, Discord log via MRadmin's webhook (`discord.use-mradmin-webhook`) | MRadmin |
| **Team notices** on lock and lag shutdown in the inbox, as push and in the game - everyone chooses where under Account → Notifications; `/mradmin mute` mutes the messages in the game | MRadmin |

## Several worlds (Multiverse)
Chunk loaders work in every world, also in additional Multiverse worlds. When a world is unloaded, its loaders rest and continue by themselves the next time it is loaded. A dummy player does not block unloading: with `/mv unload <world> --remove-players` it leaves the world and sits back on its loader when the world is loaded.
