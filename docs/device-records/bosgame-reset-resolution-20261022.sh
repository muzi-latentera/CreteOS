#!/bin/bash
# Keep a live game's display stable when a Moonlight client disconnects.
# Resizing a running fixed-size Proton game can leave its viewport cropped.
set -eu
sway_socket=/run/user/1000/sway-sunshine.sock
if ! tree=$(swaymsg -s "$sway_socket" -t get_tree -r); then
    echo "CreteOS: display reset skipped; cannot inspect live game windows" >&2
    exit 0
fi
if printf '%s' "$tree" | python3 -c '
import json,sys
def game(n):
    props=n.get("window_properties") or {}
    names=[props.get("class") or "", n.get("app_id") or ""]
    return any(v.startswith("steam_app_") for v in names) or any(game(c) for c in n.get("nodes",[])+n.get("floating_nodes",[]))
sys.exit(0 if game(json.load(sys.stdin)) else 1)
'; then
    echo "CreteOS: keeping host resolution while a Steam game window is open" >&2
    exit 0
fi
swaymsg -s "$sway_socket" "output HEADLESS-1 mode 1920x1080@60Hz"
