# Abschaltung & Pausen

Der [Lag-Schutz](lag-schutz.md) bewertet jeden Loader für sich. Hier geht es um den ganzen Server: wenn viele Loader zusammen zu viel sind oder der Server aus anderen Gründen laggt.

## Gestaffelte Abschaltung
Viele Loader können den Server **zusammen** ausbremsen, auch wenn jeder für sich unter seinen Grenzen bleibt. Dafür beobachtet MRchunks die **Tick-Zeit des Servers (MSPT)** im Mittel über eine Minute - einzelne Spitzen lösen nichts aus:

| MSPT im Mittel | Was passiert |
|---|---|
| bis 40 | alles in Ordnung |
| über 40 | **Warnung** an das Team im Chat und in Discord. Sie bleibt mindestens 5 Minuten bestehen. |
| über 45 | Der Loader mit der **höchsten Auslastung** (Anteil an seinen Grenzen) wird **„Durch Lags deaktiviert“**. Pro Minute folgt der nächste, bis die MSPT wieder bei 40 oder darunter liegt. |
| ab 70 | **Harte Grenze:** **alle** aktiven Loader werden sofort durch Lags deaktiviert (gemessen über 10 Sekunden, reagiert also schnell). Sie kommen danach genauso einzeln zurück. |

- **Durch Lags deaktiviert** ist **keine Sperre**: Der Loader bleibt eingeschaltet, gibt aber seine Chunks frei (und sein Dummy verschwindet). Er bleibt **mindestens 5 Minuten** aus.
- **Zurück:** Liegt die MSPT danach wieder bei 40 oder darunter, laden die Loader **einzeln nacheinander** wieder (einer pro Minute, der zuletzt abgeschaltete zuerst). Steigt die Last dabei wieder, stoppt die Rückkehr. Menü, `/mrchunks lag`, Spielerseite und MRadmin zeigen die voraussichtliche Rückkehrzeit.
- **Zu oft:** Wird derselbe Loader **innerhalb von 60 Minuten mehr als zweimal** abgeschaltet, wird er beim dritten Mal **gesperrt** (nur Admins können entsperren, mit MRadmin bekommt der Owner ein Ticket). Abschaltungen durch die harte Grenze zählen dafür nicht.
- **Wer erfährt es:** Der Owner (offline beim nächsten Einloggen), das Team (`mrchunks.admin.gui`) im Chat und der Discord-Log. `/mrchunks lag` zeigt die aktuelle Serverlast und alle abgeschalteten Loader.
- Ein Neustart bringt alle abgeschalteten Loader zurück.
- **Einstellbar** unter `staged-shutdown`: `warn-mspt`, `shutdown-mspt`, `average-seconds`, `step-seconds`, `min-minutes`, `max-shutdowns`, `window-minutes`, `hard-cap-mspt` (`0` = keine harte Grenze), `hard-cap-seconds`.
- **Nach dem Serverstart** warten gestaffelte Abschaltung und harte Grenze **3 Minuten** (`lag-protection.startup-delay-seconds`).
- **Im MRadmin-Web ausschalten:** Unter **Chunkloader → Steuerung** kann ein Admin die gestaffelte Abschaltung samt harter Grenze aus- und wieder einschalten (`mrchunks.admin.monitoring`). Ausgeschaltet laden alle abgeschalteten Loader sofort wieder; die Grenzen pro Loader gelten weiter. Die Einstellung bleibt auch nach einem Neustart.

## Alle Loader pausieren (Team)
Unter **Chunkloader → Steuerung** im MRadmin-Web kann ein Admin **alle Chunkloader vorübergehend pausieren** (`mrchunks.admin.pause`), z.B. bei Problemen oder vor einer Wartung. Alle Chunks werden sofort freigegeben, die Loader bleiben aber eingeschaltet und laden wieder, sobald die Pause im Web aufgehoben wird. Die Pause bleibt auch nach einem Neustart bestehen. Owner aktiver Loader und das Team werden im Chat informiert, dazu ein Discord-Log. Im Menü steht „⏸ Vom Team pausiert“.

## Server-Pause
Laggt der **ganze Server** - egal warum -, pausiert MRchunks **alle** Loader:
- **Pause**, wenn die TPS 30 Sekunden lang unter 15 liegt. Kurze Lag-Spitzen lösen nichts aus. Direkt davor werden alle Loader noch einmal gemessen, und klare Verursacher werden gesperrt.
- **Weiter**, wenn die TPS 60 Sekunden lang wieder bei mindestens 18 liegt. Niemand muss etwas neu einschalten.
- Owner und Admins werden informiert, im Menü steht „⏸ Pausiert“. Einstellbar unter `server-pause`.
