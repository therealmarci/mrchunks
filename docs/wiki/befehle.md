# Befehle & Admin-Menü

Spieler brauchen keine Befehle außer `/chunkloader` (oder `/cl`) - alles läuft über Menüs. Für das Team gibt es `/mrchunks`:

| Befehl | Was er tut | Permission |
|---|---|---|
| `/mrchunks gui` | Admin-Menü: alle Spieler und ihre Loader | `mrchunks.admin.gui` |
| `/mrchunks tp <spieler> <nr>` | Zu einem Chunkloader teleportieren | `mrchunks.admin.teleport` |
| `/mrchunks toggle <spieler> <nr>` | Chunkloader an/aus | `mrchunks.admin.toggle` |
| `/mrchunks delete <spieler> <nr>` | Chunkloader löschen | `mrchunks.admin.manage` |
| `/mrchunks unlock <spieler> <nr>` | Lag-Sperre aufheben | `mrchunks.admin.manage` |
| `/mrchunks suspendlock <spieler> <nr> <dauer\|off>` | [Limit-Sperre eines Loaders aussetzen](lag-schutz.md#limit-sperre-aussetzen), z.B. `6h` (höchstens 24 Stunden), `off` beendet es | `mrchunks.admin.suspendlock` |
| `/mrchunks lag` | Serverlast, durch Lags deaktivierte, auffällige und gesperrte Loader anzeigen | `mrchunks.admin.manage` |
| `/mrchunks dummy <spieler> <nr>` | Dummy eines Loaders an/aus (auch über das Limit hinaus) | `mrchunks.admin.manage` |
| `/mrchunks link <haupt> <zweit>` | Accounts ohne Zustimmung verknüpfen | `mrchunks.admin.link` |
| `/mrchunks unlink <spieler>` | Verknüpfung lösen | `mrchunks.admin.link` |
| `/mrchunks limit <spieler> <budget\|dummy> <anzahl\|reset>` | Chunk-Budget oder Dummy-Limit eines Spielers setzen (siehe [Chunk-Budget](budget.md)) | `mrchunks.admin.limit` |
| `/mrchunks ban <spieler> [dauer\|permanent] [grund]` | Sperren: löscht alle Loader und verhindert neue | `mrchunks.admin.ban` |
| `/mrchunks unban <spieler>` · `bans` | Sperre aufheben · alle Sperren anzeigen | `mrchunks.admin.ban` |
| `/mrchunks discord [test]` | Discord-Status · Testnachricht | `mrchunks.admin.discord` |
| `/mrchunks reload` | Config und Sprachdatei neu laden | `mrchunks.admin.reload` |
| `/mrchunks version` | Version und Update-Status | `mrchunks.version` (in `mrchunks.player`) |

Owner dürfen ihre eigenen Loader auch mit `toggle` und `delete` steuern.

## Admin-Menü
`/mrchunks gui` zeigt oben eine Übersicht (Loader, aktive Loader, geladene Chunks, Sperren) und darunter alle Spieler mit Loadern als Köpfe.
- **Klick auf einen Kopf:** Budget mit Links-/Rechtsklick ändern (±10 Chunks, Shift = zurücksetzen), Dummy-Limit (±1), alle Loader löschen, sperren oder entsperren - und darunter alle Loader des Spielers.
- **Klick auf einen Loader:** an/aus, Größe, umbenennen, Grenze anzeigen, hinteleportieren, löschen.

Alle Menüs aktualisieren sich jede Sekunde. Jede Aktion prüft ihre eigene Permission.

> [!TIP]
> Mit MRadmin gibt es dasselbe auch im Browser: Seite **Chunkloader** fürs Team (mit Verlauf, Live-Ansicht und Steuerung) und der Bereich MRchunks auf jeder Spielerseite.
