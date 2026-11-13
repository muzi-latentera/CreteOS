#!/usr/bin/env bash
# Lutris supplies GAME_NAME to the global post-exit command.
set -eu
[[ -n ${GAME_NAME:-} ]] || exit 0
state=${XDG_STATE_HOME:-"$HOME/.local/state"}/creteos
mkdir -p -m 700 "$state"
exec 9>"$state/lutris-saves.lock"
flock -w 300 9
umask 077
exec >>"$state/lutris-saves.log" 2>&1
printf '\n%s %s\n' "$(date -Is)" "$GAME_NAME"

tool="$HOME/.local/bin/ludusavi"
# A manual GUI/CLI upload may already be working on this repository.
for ((i=0; i<120; i++)); do
    if ! pgrep -u "$(id -u)" -x ludusavi >/dev/null; then break; fi
    sleep 5
done
if pgrep -u "$(id -u)" -x ludusavi >/dev/null; then
    echo "Ludusavi is already open or busy. Run a backup after it finishes."
    exit 1
fi
title=$(timeout 30s "$tool" --no-manifest-update find --backup --normalized --api -- "$GAME_NAME" </dev/null |
    python3 -c 'import json,sys; print(next(iter(json.load(sys.stdin)["games"]), ""))')
if [[ -z $title ]]; then
    echo "No exact Ludusavi title match. Check the title or add a custom game."
    exit 1
fi

# An unattended cloud conflict must leave the remote copy alone.
report=$(mktemp "$state/lutris-save-report.XXXXXX")
trap 'rm -f -- "$report"' EXIT
result=0
timeout 240s "$tool" --no-manifest-update backup --api --force --no-force-cloud-conflict --cloud-sync --full-limit 10 -- "$title" </dev/null >"$report" || result=$?
python3 - "$report" "$result" <<'PY'
import json, sys

try:
    with open(sys.argv[1]) as report:
        data = json.load(report)
except (OSError, ValueError):
    data = {}
overall = data.get("overall", {})
print(f"exit={sys.argv[2]} files/registry bytes={overall.get('processedBytes', 0)}")
if data.get("errors"):
    print("Ludusavi reported:", ", ".join(data["errors"]))
if not overall.get("processedBytes"):
    print("No matching save data was backed up.")
PY
exit "$result"
