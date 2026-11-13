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
# creteos-saves is CreteOS's lutris_save_check.py. It always writes the local
# version first, then syncs Google Drive only in a direction that cannot discard
# a version, and records the result for CreteOS's Manage saves screen.
helper="$HOME/.local/bin/creteos-saves"
if [[ ! -r $helper ]]; then
    echo "CreteOS save helper is missing: $helper"
    exit 1
fi
timeout 600s python3 "$helper" --after-exit "$GAME_NAME" </dev/null
