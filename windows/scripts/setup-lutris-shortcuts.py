"""Register runner-preserving Lutris shortcuts. Run on host with Steam closed.

Existing unrelated shortcuts are retained. Installer entries may be registered,
but CreteOS's fresh executable preflight refuses to launch an installer.
"""
import binascii
import datetime
import json
import os
import pathlib
import re
import shutil
import sqlite3


def main():
    for p in pathlib.Path('/proc').iterdir():
        if p.name.isdigit():
            try:
                if p.stat().st_uid == os.getuid() and (p / 'comm').read_text().strip() == 'steam':
                    raise RuntimeError('Close Steam normally before registering shortcuts; no files changed')
            except (FileNotFoundError, PermissionError):
                pass
    from lutris.util.steam import vdf
    from lutris.util.steam.shortcut import get_shortcuts_vdf_path
    db = pathlib.Path.home() / '.local/share/lutris/pga.db'
    with sqlite3.connect(db.as_uri() + '?mode=ro', uri=True) as con:
        games = con.execute('select id,name from games where installed=1').fetchall()
    path = pathlib.Path(get_shortcuts_vdf_path())
    if not path.is_file():
        raise RuntimeError('Existing Steam shortcut file required to identify the intended account')
    original = path.read_bytes()
    entries = vdf.binary_loads(original)['shortcuts']
    entries = list(entries.values()) if isinstance(entries, dict) else []
    updated, registered = [], []
    names = {str(ident): name for ident, name in games}
    for entry in entries:
        options = str(entry.get('LaunchOptions', ''))
        match = re.fullmatch(r'(?:-- /usr/bin/lutris )?lutris:rungameid/([0-9]+)', options)
        managed = entry.get('Exe') in ('"/usr/bin/dbus-run-session"', '"lutris"', '"/usr/bin/lutris"')
        if not (managed and match and match[1] in names):
            updated.append(entry)
    for ident, name in games:
        exe = '"/usr/bin/dbus-run-session"'
        appid = binascii.crc32((exe + name).encode()) | 0x80000000
        if any((int(e.get('appid', 0)) & 0xffffffff) == appid for e in updated):
            raise RuntimeError('Shortcut ID collision; existing shortcuts left unchanged')
        entry = {'appid': appid - 0x100000000, 'AppName': name, 'Exe': exe,
                 'StartDir': '"' + str(pathlib.Path.home()) + '"',
                 'LaunchOptions': '-- /usr/bin/lutris lutris:rungameid/' + str(ident),
                 'icon': '', 'IsHidden': 0, 'AllowDesktopConfig': 1, 'AllowOverlay': 1,
                 'OpenVR': 0, 'Devkit': 0, 'DevkitOverrideAppID': 0, 'LastPlayTime': 0, 'tags': {}}
        updated.append(entry)
        registered.append({'lutrisId': str(ident), 'name': name, 'shortcutId': str(appid)})
    data = vdf.binary_dumps({'shortcuts': {str(i): e for i, e in enumerate(updated)}})
    # Round-trip before replacing; preserve original on disk with dated backup.
    if len(vdf.binary_loads(data)['shortcuts']) != len(updated):
        raise RuntimeError('Shortcut roundtrip failed')
    backup = path.with_name(path.name + '.before-creteos-lutris-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    shutil.copy2(path, backup)
    temp = path.with_name('shortcuts.creteos.tmp')
    temp.write_bytes(data)
    temp.replace(path)
    print(json.dumps({'registered': registered, 'backup': str(backup), 'totalShortcuts': len(updated)}))


if __name__ == '__main__':
    main()
