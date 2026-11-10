#!/usr/bin/env bash
# Dev channel: seals the JARs of a green check run on dev with the release token (secret DEV_TOKEN, .github/DevSeal.java)
# and puts them into the rolling release "mc0-dev" (pre-release, never "Latest"). Installer and MRadmin open them
# only with the token. The tag "mc0-dev" names Minecraft "0" on purpose: every update checker (also older plugin
# versions), the website and MRadmin's release picker ignore it. Used by .github/workflows/checks.yml.
set -euo pipefail

ART=${1:?usage: dev-channel.sh <artifacts folder>}
TAG=mc0-dev
REPO=${GITHUB_REPOSITORY:?}
SHA=${GITHUB_SHA:?}
SUMMARY=${GITHUB_STEP_SUMMARY:-/dev/stdout}

if [ -z "${DEV_TOKEN:-}" ]; then
    echo "::notice::No secret DEV_TOKEN - dev channel skipped (set it under Settings > Secrets and variables > Actions)."
    echo "Dev channel: skipped (no DEV_TOKEN)." >> "$SUMMARY"
    exit 0
fi

OUT=$(mktemp -d)
FILES=()
ENTRIES='[]'
# download-artifact puts a single artifact directly into the folder, several into subfolders - just take every JAR.
# The name comes from the file name: MRtoolbar-mod-mc26.3-STA-1.1.0-bu001.jar -> MRtoolbar-mod.
while IFS= read -r jar; do
    name=$(basename "$jar" .jar | sed -E 's/-mc[0-9][0-9.]*-.*$//')
    part=plugin
    props=gradle.properties
    case "$name" in
        *-mod) part=mod; props=mod/gradle.properties ;;
        *) [ -f plugin/gradle.properties ] && props=plugin/gradle.properties ;;
    esac
    base=$(grep '^version=' "$props" | cut -d= -f2 | tr -d '[:space:]')
    mc=$(grep -E '^(mcVersion|minecraft_version)=' "$props" | head -n 1 | cut -d= -f2 | tr -d '[:space:]')
    sealed="$OUT/$name-dev.jar.mrdev"
    java .github/DevSeal.java seal "$jar" "$sealed"
    sum=$(sha256sum "$jar" | cut -d' ' -f1)
    size=$(stat -c %s "$jar")
    ENTRIES=$(jq -c --arg n "$(basename "$sealed")" --arg a "$name" --arg p "$part" --arg b "$base" --arg mc "$mc" \
        --arg j "$(basename "$jar")" --arg s "$sum" --argjson z "$size" \
        '. + [{file: $n, artifact: $a, part: $p, baseVersion: $b, minecraft: $mc, jar: $j, sha256: $s, size: $z}]' <<< "$ENTRIES")
    FILES+=("$sealed")
done < <(find "$ART" -name '*.jar' ! -name '*-plain.jar' | sort)
[ ${#FILES[@]} -gt 0 ] || { echo "::error::no JARs to seal"; exit 1; }

# Token check: a short known text sealed with the token - MRadmin opens it to tell whether its token is valid,
# without downloading a whole JAR.
printf '%s' "mrtechnology-dev-token-check" > "$OUT/check.txt"
java .github/DevSeal.java seal "$OUT/check.txt" "$OUT/check.mrdev" >/dev/null
CHECK=$(base64 -w0 "$OUT/check.mrdev")
rm -f "$OUT/check.txt" "$OUT/check.mrdev"

DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq -n --arg r "$REPO" --arg c "$SHA" --arg d "$DATE" --argjson run "${GITHUB_RUN_NUMBER:-0}" --argjson f "$ENTRIES" \
    --arg k "$CHECK" \
    '{format: 1, repository: $r, commit: $c, shortCommit: $c[0:7], run: $run, date: $d, check: $k, files: $f}' \
    > "$OUT/dev.json"

NOTES="Dev-Build ${SHA:0:7} vom $DATE (Lauf ${GITHUB_RUN_NUMBER:-?}) - unfertiger Entwicklungsstand.
Die Dateien sind verschlüsselt und lassen sich nur mit dem Freigabetoken über den Installer oder MRadmin installieren.
Nicht für den Produktivbetrieb. Stabile Versionen: https://mrtechnology.marcelr.at/de/downloads/"

# move the tag to this commit (or create it), then replace the assets
if gh api "repos/$REPO/git/refs/tags/$TAG" >/dev/null 2>&1; then
    gh api -X PATCH "repos/$REPO/git/refs/tags/$TAG" -f sha="$SHA" -F force=true >/dev/null
else
    gh api -X POST "repos/$REPO/git/refs" -f ref="refs/tags/$TAG" -f sha="$SHA" >/dev/null
fi
if gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
    gh release edit "$TAG" -R "$REPO" --title "Dev-Build (verschlüsselt)" --notes "$NOTES" --prerelease --latest=false >/dev/null
    for old in $(gh release view "$TAG" -R "$REPO" --json assets --jq '.assets[].name'); do
        keep=0
        for f in "${FILES[@]}" "$OUT/dev.json"; do [ "$(basename "$f")" = "$old" ] && keep=1; done
        [ $keep = 1 ] || gh release delete-asset "$TAG" "$old" -R "$REPO" -y
    done
else
    gh release create "$TAG" -R "$REPO" --title "Dev-Build (verschlüsselt)" --notes "$NOTES" --prerelease --latest=false --verify-tag >/dev/null
fi
gh release upload "$TAG" -R "$REPO" --clobber "${FILES[@]}" "$OUT/dev.json"

{
    echo "### Dev channel"
    echo "Release \`$TAG\` now points to \`${SHA:0:7}\` (sealed):"
    jq -r '.files[] | "- \(.file) (\(.baseVersion), \(.size) bytes)"' "$OUT/dev.json"
} >> "$SUMMARY"
echo "Dev channel updated: ${#FILES[@]} file(s) for ${SHA:0:7}"
