# Zusammenarbeit mit anderen Plugins

MRchunks läuft **allein**. Zusammen mit anderen MRtechnology-Plugins kann es mehr - die Plugins sprechen dafür über **MRadmin** miteinander ([wie das funktioniert](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/)).

| Funktion | Braucht |
|---|---|
| MRchunks im Dashboard, Spieler-Menü und Serverzustand von MRadmin, Seite **Chunkloader** in der Weboberfläche | MRadmin |
| Eigene Chunkloader im Browser verwalten (`/web`, Spielerseite) | MRadmin |
| MRchunks im Browser einstellen (Plugins → MRchunks → Konfiguration) | MRadmin |
| Automatisches Ticket für den Owner bei einer Lag-Sperre | MRadmin |
| In der Arena-Welt von MRpvp lassen sich keine Loader setzen | MRadmin + MRpvp |
| Chunkloader auf der Karte der Spielerseite | MRadmin |
| Dummy-Spieler zählen bei MRsleep, MRadmin und MRpvp nicht als Spieler | nichts |
| Ereignisse im **Protokoll** der MRadmin-Weboberfläche, Discord-Log über MRadmins Webhook (`discord.use-mradmin-webhook`) | MRadmin |
| **Team-Meldungen** bei Sperre und Lag-Abschaltung im Posteingang, als Push und im Spiel - jeder wählt unter Konto → Benachrichtigungen, wo; `/mradmin mute` schaltet die Hinweise im Spiel stumm | MRadmin |

## Mehrere Welten (Multiverse)
Chunkloader funktionieren in jeder Welt, auch in zusätzlichen Welten von Multiverse. Wird eine Welt entladen, ruhen ihre Loader und laufen beim nächsten Laden von selbst weiter. Ein Dummy-Spieler blockiert das Entladen nicht: Mit `/mv unload <welt> --remove-players` verlässt er die Welt und setzt sich beim Laden wieder auf seinen Loader.
