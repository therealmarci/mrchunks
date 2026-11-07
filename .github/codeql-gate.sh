#!/usr/bin/env bash
# Evaluates the open CodeQL alerts of this ref (after the analysis jobs uploaded their results): fails from severity
# high (high/critical), lower findings only go into the summary of the run. Alerts dismissed in the Security tab of
# the repository (with a reason) do not count. Used by .github/workflows/checks.yml; needs GH_TOKEN with
# security-events: read.
set -euo pipefail

REPO=${GITHUB_REPOSITORY:?}
REF=${GITHUB_REF:?}
SUMMARY=${GITHUB_STEP_SUMMARY:-/dev/stdout}

OUT=$(mktemp)
# severity <TAB> rule <TAB> place <TAB> description <TAB> link
gh api --paginate "repos/$REPO/code-scanning/alerts?ref=$REF&state=open&tool_name=CodeQL&per_page=100" \
    --jq '.[] | [(.rule.security_severity_level // .rule.severity // "none"), .rule.id,
          "\(.most_recent_instance.location.path // "?"):\(.most_recent_instance.location.start_line // "?")",
          ((.rule.description // "") | gsub("[\r\n\t|]"; " ")), .html_url] | @tsv' > "$OUT"

rank() { case "$1" in critical) echo 4 ;; high) echo 3 ;; medium) echo 2 ;; low) echo 1 ;; *) echo 0 ;; esac; }
total=$(wc -l < "$OUT")
blocking=$(awk -F'\t' '$1 == "critical" || $1 == "high"' "$OUT" | wc -l)

{
    echo "### CodeQL"
    if [ "$total" -eq 0 ]; then
        echo "No open findings."
    else
        printf '%s open finding(s), %s blocking (high/critical).\n\n' "$total" "$blocking"
        printf '| Severity | Rule | Place | Description |\n|---|---|---|---|\n'
        while IFS=$'\t' read -r sev rule place desc url; do
            printf '%s\t| %s | [%s](%s) | %s | %s |\n' "$(rank "$sev")" "$sev" "$rule" "$url" "$place" "$desc"
        done < "$OUT" | sort -rn | cut -f2-
    fi
} >> "$SUMMARY"

while IFS=$'\t' read -r sev rule place desc url; do
    file=${place%:*}
    line=${place##*:}
    level=warning
    [ "$sev" = critical ] || [ "$sev" = high ] && level=error
    echo "::$level file=$file,line=$line::$rule ($sev): $desc - $url"
done < "$OUT"

if [ "$blocking" -gt 0 ]; then
    echo "$blocking open CodeQL finding(s) with severity high/critical - fix them, or dismiss them in the Security tab with a reason if they are false alarms."
    exit 1
fi
echo "OK: $total open finding(s), none high/critical."
