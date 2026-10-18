# Versionen & Releases

## Versionsschema
Jede veröffentlichte Version besteht aus **Minecraft-Version** und **Plugin-Version**:

```
mc26.3-v1.2.0
  │       └─ Plugin-Version (SemVer): MAJOR.MINOR.PATCH
  └───────── Minecraft-Version, für die gebaut wurde
```

| Teil | Erhöhen, wenn … | Beispiel |
|---|---|---|
| **MAJOR** | große Releases, inkompatible Änderungen (Config, Befehle, Permissions) | `1.4.2` → `2.0.0` |
| **MINOR** | neue Funktionen, kompatibel | `1.4.2` → `1.5.0` |
| **PATCH** | Hotfixes und Fehlerbehebungen | `1.4.2` → `1.4.3` |

- **Vorabversionen:** `0.x` oder ein Suffix wie `-rc.1` → `mc26.3-v1.5.0-rc.1`. Auf GitHub und Modrinth als Pre-Release/Beta markiert.
- Dieselbe Plugin-Version kann es für mehrere Minecraft-Versionen geben: `mc26.3-v1.5.0` und `mc26.4-v1.5.0` haben denselben Funktionsumfang.
- Das Schema gilt überall gleich: Git-Tag, JAR-Name (`MRchunks-mc26.3-v1.5.0.jar`), Modrinth-Versionsnummer und `/plugins`.
- In `gradle.properties` stehen beide Teile getrennt: `version=1.5.0` und `mcVersion=26.3`. Der Build setzt sie zusammen.

## Branches
| Branch | Zweck | Releases |
|---|---|---|
| `beta` | Entwicklung für die **neueste** Minecraft-Version | Vorabversionen (`-rc.N`) |
| `main` | stabiler Stand für die **neueste** Minecraft-Version | stabile Versionen, auf GitHub als „Latest“ |
| `mc/<version>` | Wartung einer **älteren** Minecraft-Version, z.B. `mc/26.3` | stabile Versionen (Hotfixes, auf Wunsch Backports) |

Der Update-Checker meldet nur Versionen **derselben** Minecraft-Version. Ein Server mit `mc26.3-v1.5.0` bekommt
`mc26.3-v1.5.1` angezeigt, aber nie `mc26.4-…`, denn die würde auf 26.3 nicht laufen.

## Neue Minecraft-Version unterstützen (z.B. 26.4)
1. **Alte Version sichern** (nur wenn sie weiter gepflegt werden soll): `git branch mc/26.3 main && git push -u origin mc/26.3`
2. Auf `beta` in `gradle.properties` anpassen: `mcVersion=26.4`, `paperApi=<passende Paper-API>`, `supportedGameVersions=26.4`.
   Meist auch `version` erhöhen (MINOR, bei Bedarf MAJOR).
3. Code anpassen, testen, als Vorabversion taggen: `mc26.4-v1.6.0-rc.1`.
4. Wenn stabil: `beta` nach `main` mergen und `mc26.4-v1.6.0` auf `main` taggen.

## Hotfix für eine ältere Minecraft-Version
1. `git switch mc/26.3`, Fehler beheben (oder den Fix von `beta` mit `git cherry-pick` holen).
2. `version` in `gradle.properties` um PATCH erhöhen, Abschnitt in `CHANGELOG.md` ergänzen.
3. Committen, pushen und auf `mc/26.3` taggen: `git tag mc26.3-v1.5.1 && git push origin mc26.3-v1.5.1`.

## Release-Ablauf (Checkliste)
1. `version` (und ggf. `mcVersion`) in `gradle.properties` setzen.
2. In `CHANGELOG.md` einen Abschnitt `## [<version>] - <datum>` anlegen. Der Workflow nimmt ihn als Release-Text.
3. Committen und pushen.
4. Tag `mc<mcVersion>-v<version>` auf dem richtigen Branch setzen und pushen.

Der Workflow `.github/workflows/release.yml`
- prüft, ob der Tag zu `gradle.properties` passt,
- lässt stabile Versionen nur von `main` oder `mc/<mcVersion>` zu (Vorabversionen auch von `beta`),
- baut die JAR, erstellt das GitHub-Release und lädt die Version auf Modrinth hoch (mit `supportedGameVersions`).

Nur Releases von `main` werden auf GitHub als „Latest“ markiert. Ein Hotfix für eine alte Minecraft-Version verdrängt also nicht das aktuelle Release.
