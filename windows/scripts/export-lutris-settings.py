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
    # GPU routing is scoped to gaming services, not the user's global desktop
    # or LLM environment. Export only these helpers/settings, never Sunshine
    # credentials, pairing keys, or model secrets.
    for name in ['host_game_gpu.py','host_stream_env.py','gpu-deployment.json',
                 'gpu-verification.json','gpu-sunshine-settings.json']:
        file=home/'.local/share/creteos-host'/name
        if file.is_file():payloads['gpu/'+name]=file.read_bytes()
    for unit, name in [('sway-sunshine','90-creteos-game-gpu.conf'),
                       ('ds4-coding','90-creteos-igpu.conf'),
                       ('flashnext-coding','90-creteos-igpu.conf'),
                       ('llama-swap-manual','90-creteos-igpu.conf')]:
        file=home/'.config/systemd/user'/(unit+'.service.d')/name
        if file.is_file():payloads['gpu/systemd/'+unit+'.service.d/'+name]=file.read_bytes()
    # PBZ's explicitly installed OptiScaler payload is portable recovery data.
    # Never include its private full-registry backup or any other prefix data.
    opti=home/'.local/share/creteos-host/pbz-optiscaler'
    if (opti/'deployment.json').is_file():
        deployment=json.loads((opti/'deployment.json').read_text())
        if deployment['archiveSHA256']!='7465483deac95ccc93cf05efc9e883e00c03d3186ea79304c076c800bc399501':
            raise RuntimeError('Unexpected PBZ OptiScaler deployment; review backup allowlist')
        binary=pathlib.Path('/mnt/games/Games/Steam/steamapps/common/PBZ/platform/steam')
        total=0
        for item in deployment['files']:
            relative=pathlib.PurePosixPath(item['path'])
            allowed=(str(relative) in ['dxgi.dll','OptiScaler.ini','OptiScaler/Streamlined_fetcher_windows.bat'] or
                     (relative.parts[0]=='OptiScaler' and relative.suffix=='.dll') or
                     (relative.parts[0]=='Licenses' and relative.suffix in ['.txt','.md']))
            if relative.is_absolute() or '..' in relative.parts or not allowed:
                raise RuntimeError('Unexpected PBZ OptiScaler payload path')
            file=binary/relative
            if file.is_symlink() or not file.resolve().is_relative_to(binary.resolve()):
                raise RuntimeError('Linked PBZ OptiScaler payload')
            data=file.read_bytes();total+=len(data)
            if len(data)>256*1024*1024 or total>768*1024*1024:
                raise RuntimeError('Unexpected PBZ OptiScaler payload size')
            if hashlib.sha256(data).hexdigest()!=item['sha256']:
                raise RuntimeError('PBZ OptiScaler changed; review and update deployment before backup')
            payloads['pbz-optiscaler/payload/'+str(relative)]=data
        for name in ['deployment.json','verification.json','pbz-optiscaler.py']:
            file=opti/name
            if not file.is_file() or file.is_symlink() or file.stat().st_size>1024*1024:
                raise RuntimeError('Missing/unexpected PBZ OptiScaler recovery record')
            payloads['pbz-optiscaler/'+name]=file.read_bytes()
    # Other mod records/settings are references for the existing host. Patched
    # game archives, other DLLs, Wine prefixes and saves remain excluded.
    tools=home/'.local/share/creteos-host/mod-tools/mgsv'
    game=home/'Games/MGS V - THE PHANTOM PAIN'
    references={
        'mgsv-mods/records/'+name:tools/name
        for name in ['deployment.json','baseline.json','packages.json','startup-validation.json','gz-packages.json','gz-stage-status.json','gz-integration.json']
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
