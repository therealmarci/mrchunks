# Häufige Fragen

### Ein Spieler kann keine Chunkloader holen.
Er braucht `mrchunks.use` - am einfachsten `mrchunks.player` für die Default-Gruppe (siehe [Schnellstart](schnellstart.md)).

### Die Loader eines Admins verschwinden beim Betreten.
Die Gruppe hat über `*` auch `mrchunks.exempt`. Setz `mrchunks.exempt` für die Gruppe auf `false` - siehe [Wildcards](permissions.md#wildcards).

### Ein Loader-Block ist trotzdem verschwunden (z.B. durch `/fill` oder WorldEdit).
Solche Befehle umgehen den Schutz. MRchunks prüft deshalb regelmäßig und beim Laden des Chunks und stellt den Block wieder her (`loaders.restore-missing-blocks`).

### Ein Spieler kann seinen Loader nicht einschalten oder vergrößern.
Sein Chunk-Budget ist voll. Im Menü **Einstellungen → Größe** steht, wie viel er nutzt und wie viel frei ist. Er kann einen anderen Loader ausschalten oder verkleinern - oder ein Admin erhöht sein Budget (`/mrchunks limit <spieler> budget <n>` oder `mrchunks.budget.<n>`, siehe [Chunk-Budget](budget.md)).

### Der Dummy-Knopf ist grau / der Dummy setzt sich nicht.
Ein Dummy braucht einen quadratischen Loader mit dem Block in der Mitte, mindestens 5×5. Im Menü **Einstellungen → Größe** die Mitte anklicken (alle Richtungen gleichmäßig). Sind Dummies auf dem Server aus (`dummies.enabled`), gibt es den Knopf nicht.

### Mein Loader ist „Durch Lags deaktiviert“ - was heißt das?
Der Server war ausgelastet, und dein Loader war gerade der aufwendigste. Er bleibt eingeschaltet und lädt nach mindestens 5 Minuten von selbst wieder, sobald sich der Server erholt hat - du musst nichts tun. Passiert das öfter als zweimal in einer Stunde, wird er gesperrt; dann hilft es, die Farm zu verkleinern. Mehr unter [Abschaltung & Pausen](abschaltung.md).

### Was passiert, wenn ein Chunkloader-Item verloren geht?
Nichts Schlimmes - gezählt werden nur **gesetzte** Loader. Ein neues Item holst du dir im Menü unter **Chunkloader holen**. Normale Smaragdblöcke sind übrigens keine Chunkloader, nur Items aus dem Menü.

### Funktioniert das im Nether und im End?
Ja. Hast du in Paper `disable-world-ticking-when-empty` aktiviert, stell `loaders.load-method: forceload` ein.

### Ich habe einen Fehler gefunden oder einen Wunsch.
Gern über das **[Formular auf der Website](https://mrtechnology.marcelr.at/report/mrchunks/)** - am besten mit der Ausgabe von `/mrchunks version`.
