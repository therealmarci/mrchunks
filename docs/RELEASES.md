# Versionen & Releases

## Versionsschema
Jede veröffentlichte Version besteht aus **Minecraft-Version**, **Kanal**, **Versionsnummer** und **Build**:

```
mc26.3-STA-1.4.0-bu002
  │     │   │      └─ Build: zählt die Releases dieser Versionsnummer (001, 002 …)
  │     │   └──────── MAJOR.MINOR.PATCH
  │     └──────────── Kanal: STA = stabil, BET = Beta
  └────────────────── Minecraft-Version, für die gebaut wurde
```

| Teil | Bedeutung | Beispiel |
|---|---|---|
| **Kanal** | `STA` = stabil, `BET` = Beta | `BET-1.4.0-bu001` → `STA-1.4.0-bu002` |
| **MAJOR** | große Umbauten, inkompatible Änderungen (Config, Befehle, Permissions) | `1.4.2` → `2.0.0` |
| **MINOR** | neue Funktionen | `1.4.2` → `1.5.0` |
| **PATCH** | Hotfixes | `1.4.2` → `1.4.3` |
| **BUILD** | zählt die Releases **einer** Version (jede Beta und die Stable), beginnt bei jeder neuen Version bei `001` | `bu001`, `bu002` … |

- **Eine Stable schließt ihre Version ab.** Aus `BET-1.4.0-bu002` wird `STA-1.4.0-bu003`; soll danach wieder eine Beta
  kommen, ist das `BET-1.4.1-bu001` (nächster PATCH).
- **Reihenfolge** (was ist neuer?): MAJOR, MINOR, PATCH, dann BUILD. Der Kanal entscheidet nur, wer ein Release
  bekommt: Server bekommen nur Stables, Betas nur mit eingeschaltetem `updates.include-prereleases` (bzw. bei
  MRadmin `versions.include-prereleases`) - außer bei `0.x`, das es nur als Beta gibt. Wer Betas wieder ausschaltet,
  bleibt auf seiner Beta, bis eine neuere Stable kommt (es wird nie zurückgestuft).
- **Ältere Releases** hießen `mc26.3-v1.3.1` (SemVer) und werden weiter erkannt; sie zählen als Build 0 ihrer Nummer.
  Erste Plugin-Version, die das neue Schema versteht: die Releases vom 2026-11-07 (z.B. MRadmin 1.4.0).
- Dieselbe Version kann es für mehrere Minecraft-Versionen geben (`mc26.3-STA-1.5.0-bu001`, `mc26.4-STA-1.5.0-bu001`).
- Die Klasse `util/Versions` (Vergleich, Kanal, Beta-Regel) ist in allen MRtechnology-Plugins identisch; Prüfung mit
  `##OTHER/Scripts/check-versions.sh`.
- Das Schema gilt überall gleich: Git-Tag, JAR-Name (`MRchunks-mc26.3-STA-1.4.0-bu002.jar`), `plugin.yml`
  (`STA-1.4.0-bu002+mc26.3`) und `/plugins`. In `gradle.properties` stehen `version=STA-1.4.0-bu002` und
  `mcVersion=26.3` getrennt, der Build setzt sie zusammen.

## Branches
| Branch | Zweck | Releases |
|---|---|---|
| `dev` | tägliche Entwicklung und Testbuilds | keine - jeder Push baut nur ein Actions-Artefakt (Prüfrunde) |
| `beta` | nächstes Update als Beta | `BET-…` (GitHub-Vorabversion) |
| `main` | stabiler Stand für die **neueste** Minecraft-Version, Standardzweig (Website, Wiki, Issue-Vorlagen) | `STA-…`, auf GitHub als „Latest“ |
| `mc/<version>` | Wartung einer **älteren** Minecraft-Version, z.B. `mc/26.3` | `STA-…`/`BET-…` (Hotfixes) |

Weg eines Updates: `dev` → `beta` (Beta-Release) → `main` (Stable). Die Beta darf übersprungen werden
(`dev` → `main`). Nach jedem Release werden die Branches wieder gleichgezogen. Die Wiki-Vorschau der Website
(`/wiki/preview/<slug>/`) zeigt den Stand von `beta`, das Wiki selbst den von `main`.

Der Update-Check meldet nur Versionen **derselben** Minecraft-Version - ein Server mit 26.3 bekommt nie `mc26.4-…`.

## Neue Minecraft-Version unterstützen (z.B. 26.4)
1. **Alte Version sichern** (nur wenn sie weiter gepflegt werden soll): `git branch mc/26.3 main && git push -u origin mc/26.3`
2. Auf `dev` in `gradle.properties` anpassen: `mcVersion=26.4`, `paperApi=<passende Paper-API>`, `supportedGameVersions=26.4`.
3. Code anpassen, testen, als Beta veröffentlichen (`mc26.4-BET-1.5.0-bu001`), danach als Stable (`mc26.4-STA-1.5.0-bu002`).

## Hotfix für eine ältere Minecraft-Version
1. `git switch mc/26.3`, Fehler beheben (oder den Fix von `dev` mit `git cherry-pick` holen).
2. `version` in `gradle.properties` setzen (nächster PATCH, Build `001`, z.B. `STA-1.4.1-bu001`), CHANGELOG-Abschnitt anlegen.
3. Committen, pushen und auf `mc/26.3` taggen: `git tag mc26.3-STA-1.4.1-bu001 && git push origin mc26.3-STA-1.4.1-bu001`.

## Release-Ablauf (Checkliste)
Versionsnummern legt der User fest. Beispiel Beta, dann Stable:

**Mit dem Release-Skript** (`C:\REPO\mc\##OTHER\Scripts\release.sh`, in Git Bash) laufen die Schritte 2-5 von selbst:

```bash
bash "##OTHER/Scripts/release.sh" <repo> beta 1.4.0        # dev -> beta, Tag BET-1.4.0-bu<nächster>
bash "##OTHER/Scripts/release.sh" <repo> stable            # beta -> main, Nummer der letzten Beta, nächster Build
bash "##OTHER/Scripts/release.sh" <repo> stable 1.4.0 --from dev   # Beta überspringen
#   MRtoolbar/MRwaypoints: <repo>/plugin bzw. <repo>/mod; --dry-run zeigt nur, was passieren würde
```

Es prüft vorher sauberes Arbeitsverzeichnis und gleichen Stand mit GitHub, sucht den nächsten Build aus den Tags,
lehnt geschlossene Versionen und Stables für 0.x ab, fasst bei einer Stable die Beta-Abschnitte im CHANGELOG
zusammen (`release-changelog.mjs`), zieht `beta`/`dev` nach (ging `dev` inzwischen weiter, bleiben dessen
unveröffentlichte Einträge erhalten), startet den Workflow bei Bedarf von Hand und prüft das fertige Release.

Von Hand:

1. Auf `dev` ist alles fertig, gebaut, getestet und committet; im CHANGELOG steht alles unter `## [Unveröffentlicht]`.
2. **Beta:** `dev` nach `beta` mergen. Auf `beta`: `version=BET-1.4.0-bu001` in `gradle.properties`,
   im CHANGELOG `## [Unveröffentlicht]` → `## [BET-1.4.0-bu001] - <datum>` (darüber bleibt `## [Unveröffentlicht]` leer
   stehen), Commit „Pre-Release …“, Tag `mc26.3-BET-1.4.0-bu001` auf `beta`, pushen.
3. **Stable:** `beta` (bzw. beim Überspringen `dev`) nach `main` mergen. Auf `main`: `version=STA-1.4.0-bu002`
   (nächster Build), im CHANGELOG **alle** Beta-Abschnitte seit der letzten Stable zu einem Abschnitt
   `## [STA-1.4.0-bu002] - <datum>` zusammenfassen (Stable-Nutzer sehen sonst nur die letzte Beta), Commit „Release …“,
   Tag `mc26.3-STA-1.4.0-bu002` auf `main`, pushen.
4. Branches gleichziehen: `beta` und `dev` per Fast-Forward auf den neuen Stand bringen und pushen.
5. Release-Workflow abwarten (`gh run watch`): Release da, JAR dran, Beta als Vorabversion, Stable als „Latest“.

Der Workflow `.github/workflows/release.yml`
- prüft das Format der Version und dass der Tag zur `gradle.properties` passt,
- lässt Stables nur von `main` oder `mc/<mcVersion>` zu, Betas nur von `beta` oder `mc/<mcVersion>`,
- lässt vorher die Prüfrunde laufen (siehe unten), übernimmt deren JAR und erstellt das GitHub-Release mit
  Herkunftsnachweis (Beta = Vorabversion, nur eine Stable von `main` wird „Latest“).

Das Release-Text kommt aus dem CHANGELOG-Abschnitt mit genau dieser Version (`## [STA-1.4.0-bu002]`).

## Prüfrunde vor jedem Release (Sicherheit)
Jeder Release-Workflow ruft zuerst `.github/workflows/checks.yml` auf. **Ist dort etwas rot, entsteht kein Release.**
Dieselbe Prüfrunde läuft bei jedem Push, jedem Pull-Request und jeden Montag (findet neue Lücken in schon
veröffentlichten Versionen - GitHub schickt bei einem roten Lauf eine Mail).

| Prüfung | Was | Stoppt bei |
|---|---|---|
| Bauen + Tests | `./gradlew build`, Prüfung des Gradle-Wrappers (`gradle-wrapper.jar` unverändert) | jedem Fehler |
| Bibliotheken | alles, was auf dem Server landet (eingebettet = `runtimeClasspath`, Mod: `include`, dazu die `libraries:` der `plugin.yml`), per OSV-Scanner gegen bekannte Lücken (`.github/shipped-dependencies.gradle`, `.github/scan-dependencies.sh`) | CVSS ≥ 7.0 (hoch/kritisch) |
| CodeQL | Code-Analyse auf Sicherheitsprobleme (Abfragen `security-extended`), Funde im Reiter **Security → Code scanning** | offenen Funden „high“/„critical“ (`.github/codeql-gate.sh`) |

Mittlere und niedrige Funde stoppen nicht, sie stehen in der Zusammenfassung des Laufs. **Fehlalarm von CodeQL:** im
Reiter Security den Fund mit Begründung schließen („False positive“ / „Won't fix“) - danach zählt er nicht mehr.
**Lücke in einer Bibliothek:** Version anheben (Spalte „Fixed in“ der Zusammenfassung), bauen, neu taggen.

Veröffentlicht wird genau die JAR, die die Prüfrunde gebaut hat (nicht neu gebaut), mit **Herkunftsnachweis**:
GitHub bestätigt, dass sie aus diesem Tag gebaut wurde. Prüfen: `gh attestation verify <jar> -R therealmarci/<repo>`.

Außerdem: Alle Actions sind auf einen Commit festgelegt (Version als Kommentar daneben), die Workflows haben nur
Leserechte, Schreibrechte nur der Release-Schritt. `dependency-graph.yml` meldet die mitgelieferten Bibliotheken von
`main` an GitHub - neue Lücken erscheinen dann als Dependabot-Warnung im Reiter Security (ohne automatische
Pull-Requests). `gradle/actions` bleibt bewusst auf v5: ab v6 gehört zum Cache ein Baustein mit eigenen
Nutzungsbedingungen.

Actions aktualisieren: neue Version und ihren Commit nachschlagen (`gh api repos/<owner>/<action>/commits/<tag> --jq .sha`)
und in allen Workflows ersetzen. Ein Lauf von Hand: `gh workflow run Checks -R therealmarci/<repo> --ref <branch>`.
