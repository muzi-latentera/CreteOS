"""Bounded read-only Lutris inventory. No credentials or arbitrary commands."""
import json
import math
import pathlib
import re
import sqlite3
import struct
from contextlib import closing


def shortcuts(home):
    """Read shortcut metadata without importing Lutris/GTK or probing displays."""
    paths = []
    for root in [home / '.local/share/Steam', home / '.var/app/com.valvesoftware.Steam/.local/share/Steam']:
        paths.extend(root.glob('userdata/*/config/shortcuts.vdf'))
    if not paths:
        return {}
    if len(paths) != 1:
        raise ValueError('Multiple Steam profiles require explicit shortcut setup')
    if paths[0].stat().st_size > 4 * 1024 * 1024:
        raise ValueError('Shortcut file too large')
    data = paths[0].read_bytes()
    position = 0
    def string():
        nonlocal position
        end = data.index(b'\0', position)
        if end - position > 65536:
            raise ValueError('Shortcut value too large')
        value = data[position:end].decode('utf-8', errors='strict')
        position = end + 1
        return value
    def object_(depth=0):
        nonlocal position
        if depth > 8:
            raise ValueError('Shortcut nesting too deep')
        result = {}
        while position < len(data):
            kind = data[position]; position += 1
            if kind == 8:
                return result
            key = string()
            if kind == 0:
                value = object_(depth + 1)
            elif kind == 1:
                value = string()
            elif kind in (2, 7):
                size = 4 if kind == 2 else 8
                if position + size > len(data):
                    raise ValueError('Truncated shortcut integer')
                value = int.from_bytes(data[position:position + size], 'little', signed=kind == 2)
                position += size
            else:
                raise ValueError('Unsupported shortcut field')
            result[key] = value
        raise ValueError('Truncated shortcut object')
    return object_().get('shortcuts', {})


def inventory(home):
    roots = [home / '.local/share/lutris', home / '.var/app/net.lutris.Lutris/data/lutris']
    result = []
    for root in roots:
        db = root / 'pga.db'
        if not db.is_file():
            continue
        import yaml
        with closing(sqlite3.connect(db.as_uri() + '?mode=ro', uri=True, timeout=3)) as con:
            con.row_factory = sqlite3.Row
            rows = con.execute('select id,name,slug,runner,installed,configpath,playtime,lastplayed from games where installed=1 limit 1001').fetchall()
        if len(rows) > 1000:
            raise ValueError('Lutris library exceeds supported size')
        for row in rows:
            ident, config = str(row['id']), row['configpath'] or ''
            if not re.fullmatch(r'[0-9]{1,9}', ident) or pathlib.Path(config).name != config or not config:
                continue
            candidates = [root / 'games' / (config + '.yml'), home / '.config/lutris/games' / (config + '.yml'), home / '.var/app/net.lutris.Lutris/config/lutris/games' / (config + '.yml')]
            path = next((p for p in candidates if p.is_file()), None)
            if path is None or path.stat().st_size > 1024 * 1024:
                raise ValueError('Lutris launch configuration unavailable')
            data = yaml.safe_load(path.read_text()) or {}
            game = data.get('game') or {}
            exe = pathlib.Path(str(game.get('exe') or '')).expanduser()
            installer = bool(re.match(r'^(setup|install|unins|uninstall)([._-]|$)', exe.name.lower()))
            launchable = exe.is_file() and not installer
            hours = float(row['playtime'] or 0)
            if not math.isfinite(hours) or hours < 0:
                hours = 0
            result.append({'id': ident, 'name': str(row['name'])[:512], 'slug': str(row['slug'] or '')[:512],
                           'runner': str(row['runner'] or ''), 'launchable': launchable,
                           'reason': 'Lutris still points to an installer' if installer else '' if launchable else 'Game executable is missing',
                           'playtimeMinutes': min(int(hours * 60), 4294967295) if launchable else None,
                           'lastPlayed': int(row['lastplayed'] or 0), 'shortcutId': None})
    # Shortcut IDs must come from real registered entries, never a metadata Store ID.
    if result:
        registered = shortcuts(home)
        if isinstance(registered, dict):
            for shortcut in registered.values():
                options = str(shortcut.get('LaunchOptions', ''))
                match = re.fullmatch(r'-- /usr/bin/lutris lutris:rungameid/([0-9]{1,9})', options)
                if shortcut.get('Exe') != '"/usr/bin/dbus-run-session"' or not match:
                    continue
                for game in result:
                    if game['id'] == match[1] and shortcut.get('AppName') == game['name']:
                        game['shortcutId'] = str(int(shortcut['appid']) & 0xffffffff)
    return result


if __name__ == '__main__':
    print(json.dumps(inventory(pathlib.Path.home()), ensure_ascii=False))
