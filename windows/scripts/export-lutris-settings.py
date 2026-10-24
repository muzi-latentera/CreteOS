"""Private host configuration backup to stdout. No prefixes, saves or credentials."""
import base64
from contextlib import closing
import hashlib
import io
import json
import pathlib
import sqlite3
import tempfile
import zipfile


def main():
    home=pathlib.Path.home()
    root=home/'.local/share/lutris'
    payloads={}
    with tempfile.TemporaryDirectory() as tmp:
        snapshot=pathlib.Path(tmp)/'pga.db'
        with closing(sqlite3.connect((root/'pga.db').as_uri()+'?mode=ro',uri=True)) as source:
            with closing(sqlite3.connect(snapshot)) as target:
                source.backup(target)
                if target.execute('pragma integrity_check').fetchone()[0]!='ok':
                    raise RuntimeError('Lutris database integrity check failed')
        payloads['lutris/pga.db']=snapshot.read_bytes()
    configs=list((root/'games').glob('*.yml'))
    if len(configs)>1000:raise RuntimeError('Unexpected configuration count')
    for file in configs:
        if file.stat().st_size>1024*1024:raise RuntimeError('Unexpected large game configuration')
        payloads['lutris/games/'+file.name]=file.read_bytes()
    for steam in [home/'.local/share/Steam',home/'.var/app/com.valvesoftware.Steam/.local/share/Steam']:
        for file in steam.glob('userdata/*/config/shortcuts.vdf'):
            if file.stat().st_size>4*1024*1024:raise RuntimeError('Unexpected large shortcut file')
            payloads['steam/'+file.relative_to(steam).as_posix()]=file.read_bytes()
            for art in (file.parent/'grid').glob('*'):
                if art.is_file() and art.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp', '.json'):
                    if art.stat().st_size > 16*1024*1024:
                        raise RuntimeError('Unexpected large Steam artwork file')
                    payloads['steam/'+art.relative_to(steam).as_posix()]=art.read_bytes()
    for name in ['reset-resolution.sh','set-resolution.sh']:
        file=home/'.config/sway-sunshine'/name
        if file.is_file():payloads['sway-sunshine/'+name]=file.read_bytes()
    # Mod records/settings are reference data for the existing host. Patched
    # game archives, DLLs, Wine prefixes and saves remain outside this export.
    tools=home/'.local/share/creteos-host/mod-tools/mgsv'
    game=home/'Games/MGS V - THE PHANTOM PAIN'
    references={
        'mgsv-mods/records/'+name:tools/name
        for name in ['deployment.json','baseline.json','packages.json','startup-validation.json']
    }
    references.update({'mgsv-mods/settings/'+name:game/name
        for name in ['snakebite.xml','ihhook_config.lua','mod/guiStyles/CurrentStyle.ini','mod/saves/ih_save.lua']})
    profiles=game/'mod/profiles'
    if profiles.is_dir():
        for file in profiles.rglob('*'):
            if file.is_file() and file.suffix.lower() in ['.lua','.json','.ini']:
                references['mgsv-mods/settings/mod/profiles/'+file.relative_to(profiles).as_posix()]=file
    for name,file in references.items():
        if file.is_file():
            if file.is_symlink() or file.stat().st_size>16*1024*1024:
                raise RuntimeError('Unexpected mod reference file')
            payloads[name]=file.read_bytes()
    records=[{'path':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()} for name,data in payloads.items()]
    payloads['manifest.json']=json.dumps({'sourceHome':str(home),'sqliteIntegrity':'ok','files':records,
        'excluded':['game downloads','Wine prefixes','game saves','SSH keys','Buddy pairing credentials','browser/Steam login sessions'],
        'restore':'Host reference only. Keep working Bosgame untouched when replacing the G1. Recreate shortcuts using the tracked setup helper with Steam closed; do not overwrite a new host database without backing it up.'},indent=2).encode()
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as archive:
        for name,data in payloads.items():archive.writestr(name,data)
    print(base64.b64encode(out.getvalue()).decode())


if __name__=='__main__':main()
