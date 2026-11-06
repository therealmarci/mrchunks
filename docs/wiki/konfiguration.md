# Konfiguration

Am einfachsten im Browser: Mit **MRadmin** unter **Plugins → MRchunks → Konfiguration** - jede Einstellung mit Erklärung, Speichern lädt MRchunks sofort neu (Permission `mradmin.config`). Alles steht außerdem kommentiert in `plugins/MRchunks/config.yml`; Änderungen von Hand übernimmst du mit `/mrchunks reload`.

| Abschnitt | Die wichtigsten Einstellungen | Mehr dazu |
|---|---|---|
| `general` | `language` (Standard `auto`), `fallback-language`, `server-language` | [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `loaders` | `default-budget` (147 Chunks), `default-reach` (3 = neue Loader 7×7), `max-reach` (7 = höchstens 15×15), `max-loaders` (30), `overlap`, `load-method`, `restore-missing-blocks` | [Chunk-Budget](budget.md) |
| `invites` | an/aus, Dauer-Auswahl, höchstens so viele Mitglieder | [Mitspieler](mitspieler.md) |
| `linked-accounts` | an/aus, wie viele Zweit-Accounts | [Mitspieler](mitspieler.md#zweit-accounts-verknüpfen) |
| `dummies` | an/aus (Standard aus), Limit, Skin, sitzend | [Dummy-Spieler](dummy.md) |
| `lag-protection` · `staged-shutdown` · `server-pause` | Mess- und Sperrgrenzen, MSPT-Stufen der gestaffelten Abschaltung, TPS-Schwellen | [Lag-Schutz](lag-schutz.md), [Abschaltung & Pausen](abschaltung.md) |
| `item` · `border` | Aussehen des Loaders und der Grenze, `item.block-choices`: Blöcke, die Spieler für ihre Loader wählen dürfen (leere Liste `[]` = keine Auswahl) | [Chunkloader benutzen](chunkloader.md#namen-block-und-grenze) |
| `player-map` | Ebenen auf der Karte der Spielerseite (MRadmin): `own`, `others`, `loader-preview` | [Karte](karte.md) |
| `discord` · `updates` · `gui` | Logs, Update-Hinweise, Menüs | [Discord](#discord) |

- **Nether und End:** Hast du in Paper `disable-world-ticking-when-empty` aktiviert, stell `loaders.load-method: forceload` ein.
- **`config-version`** bitte nicht ändern - daran erkennt MRchunks, ob die Datei aktualisiert werden muss.

## Sprache & Texte
- `general.language: auto` (Standard): Jeder Spieler bekommt die Sprache seines Minecraft-Clients, sonst `fallback-language` (Standard Englisch).
- Discord-Logs und Tickets in MRadmin haben keinen einzelnen Empfänger - sie kommen in `general.server-language` (Standard `en`). Auf einem deutschen Server also `general.server-language: de` setzen.
- Alle Texte - Chat, Menüs, Item, Spielerseite, Discord - stehen in `plugins/MRchunks/lang/<sprache>.yml`. Ein leerer Text (`""`) schaltet die Nachricht ab. Mehr unter [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/).

## Discord
Webhook anlegen (*Kanal bearbeiten → Integrationen → Webhooks*), `discord.enabled: true` setzen, die `webhook-url` eintragen, `/mrchunks reload` und mit `/mrchunks discord test` prüfen. Welche Ereignisse gesendet werden (Loader gesetzt, gelöscht, gesperrt, Einladungen, Admin-Aktionen …), stellst du unter `discord.events` ein.

> [!TIP]
> **Kein eigener Webhook?** Mit MRadmin geht es auch über dessen Discord-Log: `discord.enabled: true`, `webhook-url` leer lassen und `discord.use-mradmin-webhook: true` setzen.
>
> Mit MRadmin steht jedes Ereignis außerdem im **Protokoll** der Weboberfläche (bis zu 14 Tage) - auch wenn Discord aus ist.
