#!/usr/bin/env bash
# Checks the shipped libraries (gradle.lockfile from shipped-dependencies.gradle) with OSV-Scanner against known
# vulnerabilities. Fails from severity high (CVSS >= 7.0) - lower findings and findings without a score only go into
# the summary of the run. Used by .github/workflows/checks.yml.
set -euo pipefail

LOCK=${1:?usage: scan-dependencies.sh <gradle.lockfile>}
OSV_VERSION=v2.7.0
OSV_SHA256=160c6253e2922e9e0a6a9771c5c4c40a4b3c6090b34a3cdd5e80c96cc2e4218b
FAIL_FROM=7.0
SUMMARY=${GITHUB_STEP_SUMMARY:-/dev/stdout}
TITLE=${SCAN_TITLE:-$(dirname "$(dirname "$(dirname "$LOCK")")")}

BIN=${OSV_BIN:-${RUNNER_TEMP:-/tmp}/osv-scanner-$OSV_VERSION}   # OSV_BIN: own binary for local tests
if [ ! -x "$BIN" ]; then
    curl -fsSL -o "$BIN" "https://github.com/google/osv-scanner/releases/download/$OSV_VERSION/osv-scanner_linux_amd64"
    echo "$OSV_SHA256  $BIN" | sha256sum -c --quiet -
    chmod +x "$BIN"
fi

if ! grep -qv '^#' "$LOCK"; then
    echo "No shipped libraries in $LOCK - nothing to scan."
    printf '### Libraries (%s)\nNo shipped libraries.\n' "$TITLE" >> "$SUMMARY"
    exit 0
fi

OUT=$(mktemp)
set +e
"$BIN" scan -L "$LOCK" --format json > "$OUT"
code=$?
set -e
# 0 = nothing found, 1 = vulnerabilities found, anything else = the scan itself failed
if [ "$code" -gt 1 ]; then
    echo "::error::OSV-Scanner failed (exit $code)"
    exit 1
fi

# severity <TAB> package version <TAB> ids <TAB> fixed in
jq -r '.results[]?.packages[]? | .package as $p | (.vulnerabilities // []) as $v | .groups[] |
    . as $g |
    ([$v[] | select(.id as $i | $g.ids | index($i)) | .affected[]?.ranges[]?.events[]? | .fixed // empty] | unique | join(", ")) as $fixed |
    [($g.max_severity // ""), "\($p.name) \($p.version)", ($g.ids | join(", ")), (if $fixed == "" then "-" else $fixed end)] | @tsv' \
    "$OUT" | sort -t$'\t' -k1,1 -rn > "$OUT.tsv"

total=$(wc -l < "$OUT.tsv")
blocking=$(awk -F'\t' -v min="$FAIL_FROM" '$1 != "" && $1 + 0 >= min' "$OUT.tsv" | wc -l)

{
    printf '### Libraries (%s)\n' "$TITLE"
    if [ "$total" -eq 0 ]; then
        printf 'No known vulnerabilities in %s libraries.\n' "$(grep -cv '^#' "$LOCK")"
    else
        printf '%s finding(s), %s blocking (CVSS >= %s).\n\n' "$total" "$blocking" "$FAIL_FROM"
        printf '| CVSS | Library | Advisory | Fixed in |\n|---|---|---|---|\n'
        awk -F'\t' '{ printf "| %s | %s | %s | %s |\n", ($1 == "" ? "?" : $1), $2, $3, $4 }' "$OUT.tsv"
    fi
} >> "$SUMMARY"

while IFS=$'\t' read -r score pkg ids fixed; do
    if [ -n "$score" ] && awk -v s="$score" -v m="$FAIL_FROM" 'BEGIN { exit !(s + 0 >= m) }'; then
        echo "::error::$pkg: $ids (CVSS $score) - fixed in: $fixed"
    else
        echo "::warning::$pkg: $ids (CVSS ${score:-unknown}) - fixed in: $fixed"
    fi
done < "$OUT.tsv"

if [ "$blocking" -gt 0 ]; then
    echo "$blocking vulnerability group(s) with CVSS >= $FAIL_FROM - update the library before releasing."
    exit 1
fi
echo "OK: $total finding(s), none with CVSS >= $FAIL_FROM."
