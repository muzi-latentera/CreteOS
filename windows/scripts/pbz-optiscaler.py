"""Pinned, game-only OptiScaler installation. Run on Bosgame: python3 script.py install|verify|rollback.

Never launches Wine, Steam or a game. Refuses an active PBZ prefix/stream and
existing destination files. Registry backup stays private on the original host.
"""
import datetime
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import urllib.request

GAME = pathlib.Path('/mnt/games/Games/Steam/steamapps/common/PBZ')
BIN = GAME / 'platform/steam'
PREFIX = pathlib.Path('/mnt/games/Games/Steam/steamapps/compatdata/4115450/pfx')
STATE = pathlib.Path.home() / '.local/share/creteos-host/pbz-optiscaler'
ASSET = 'OptiScaler_v10.0.0-pre1_20261102.7z'
URL = 'https://github.com/optiscaler/OptiScaler-nightly/releases/download/nightly-20261102/' + ASSET
SHA = '7465483deac95ccc93cf05efc9e883e00c03d3186ea79304c076c800bc399501'
KEY = r'Software\\Wine\\AppDefaults\\PBZ.exe\\DllOverrides'
REG = PREFIX / 'user.reg'


def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + '.creteos-tmp')
    with temp.open('xb') as f:
        f.write(data)
    os.replace(temp, path)


def idle():
    if subprocess.run(['ss', '-Htn', 'state', 'established', '(', 'sport', '=', '48010', ')'],
                      capture_output=True, text=True, check=True).stdout.strip():
        raise RuntimeError('Active Sunshine stream; no changes made')
    for proc in pathlib.Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            env = dict(x.split(b'=', 1) for x in (proc / 'environ').read_bytes().split(b'\0') if b'=' in x)
            if env.get(b'SteamAppId') == b'4115450' or env.get(b'WINEPREFIX') == str(PREFIX).encode():
                raise RuntimeError('PBZ prefix active: PID ' + proc.name)
        except (PermissionError, ProcessLookupError, FileNotFoundError):
            pass


def sections(text):
    return re.split(r'(?=^\[)', text, flags=re.M)


def app_sections(text):
    return [part for part in sections(text) if part.startswith('[' + KEY + ']')]


def verify(record):
    for item in record['files']:
        path = BIN / item['path']
        if not path.is_file() or path.is_symlink() or digest(path) != item['sha256']:
            raise RuntimeError('Installed payload differs: ' + item['path'])
    if app_sections(REG.read_text()) != [record['registrySection']]:
        raise RuntimeError('PBZ registry override differs')
    return {'verified': True, 'files': len(record['files']), 'fsrPayloadVersion': '4.1.2.4555',
            'runtimeTested': False, 'gameLaunched': False}


def main(action):
    if action not in ('install', 'verify', 'rollback'):
        raise ValueError('Use install, verify or rollback')
    if action == 'verify':
        print(json.dumps(verify(json.loads((STATE / 'deployment.json').read_text())), indent=2))
        return
    idle()
    if action == 'rollback':
        record = json.loads((STATE / 'deployment.json').read_text())
        verify(record)  # Refuse to overwrite later changes; review those manually.
        text = REG.read_text()
        atomic(REG, text.replace(record['registrySection'], '', 1).encode())
        for item in record['files']:
            (BIN / item['path']).unlink()
        atomic(STATE / 'rollback.json', json.dumps({'time': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'removedFiles': len(record['files']), 'registrySectionRemoved': True}, indent=2).encode())
        print('Rollback complete; original native FSR/game settings retained')
        return
    if (STATE / 'deployment.json').exists():
        raise RuntimeError('Deployment already exists; verify or review rollback first')
    if not (BIN / 'PBZ.exe').is_file() or (BIN / 'PBZ.exe').stat().st_size < 100_000_000:
        raise RuntimeError('Unexpected PBZ executable layout')
    before_reg = REG.read_bytes()
    if app_sections(before_reg.decode()):
        raise RuntimeError('Existing PBZ DLL overrides require manual review')
    # Also reject conflicting proxy names, even if this install would not overwrite them.
    for name in ('dxgi.dll', 'winmm.dll', 'version.dll', 'OptiScaler.dll', 'OptiScaler.ini', 'OptiScaler'):
        if (BIN / name).exists():
            raise RuntimeError('Existing mod/proxy requires review: ' + name)
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    archive = STATE / ASSET
    if not archive.exists():
        urllib.request.urlretrieve(URL, archive)
    if digest(archive) != SHA:
        raise RuntimeError('Official archive SHA256 mismatch')
    staging = STATE / 'extracted'
    staging.mkdir(exist_ok=True)
    subprocess.run(['7z', 'x', '-y', '-o' + str(staging), str(archive)], check=True, stdout=subprocess.DEVNULL)
    config = (staging / 'OptiScaler.ini').read_text(encoding='utf-8-sig')
    settings = {'Dx12Upscaler': 'ffx', 'UpscalerIndex': '0', 'Fsr4Model': '0',
                'EnableFfxInputs': 'true', 'UseFfxInputs': 'true', 'Dxgi': 'false',
                'LogToFile': 'true', 'LogLevel': '2', 'TargetProcessName': 'PBZ.exe',
                'DisableOverlays': 'false'}
    for key, value in settings.items():
        config, count = re.subn(r'^' + key + r'=auto\s*$', key + '=' + value, config, flags=re.M)
        if count != 1:
            raise RuntimeError('Unexpected upstream config key: ' + key)
    payloads = {'dxgi.dll': (staging / 'OptiScaler.dll').read_bytes(), 'OptiScaler.ini': config.encode()}
    for directory in ('OptiScaler', 'Licenses'):
        for path in (staging / directory).rglob('*'):
            if path.is_symlink():
                raise RuntimeError('Unexpected archive symlink')
            if path.is_file():
                payloads[path.relative_to(staging).as_posix()] = path.read_bytes()
    for relative in payloads:
        if (BIN / relative).exists():
            raise RuntimeError('Destination conflict: ' + relative)
    idle()
    if REG.read_bytes() != before_reg:
        raise RuntimeError('Registry changed during staging; retry after review')
    # Full prefix registry is private rollback evidence, never exported to Git/recovery.
    backup = STATE / 'user.reg.before-private'
    backup.write_bytes(before_reg)
    backup.chmod(0o600)
    section = '[' + KEY + '] ' + str(int(datetime.datetime.now().timestamp())) + '\n"dxgi"="native,builtin"\n\n'
    record = {'time': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'release': ASSET,
              'url': URL, 'archiveSHA256': SHA, 'gameAppId': 4115450, 'binaryDirectory': str(BIN),
              'prefix': str(PREFIX), 'fsrPayloadVersion': '4.1.2.4555', 'settings': settings,
              'registrySection': section, 'previousRegistrySHA256': hashlib.sha256(before_reg).hexdigest(),
              'runtimeTested': False, 'gameLaunched': False,
              'files': [{'path': rel, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                        for rel, data in payloads.items()]}
    copied = []
    try:
        for relative, data in payloads.items():
            atomic(BIN / relative, data)
            copied.append(relative)
        atomic(REG, before_reg + b'\n' + section.encode())
        result = verify(record)
        atomic(STATE / 'deployment.json', json.dumps(record, indent=2).encode())
        atomic(STATE / 'verification.json', json.dumps(result, indent=2).encode())
    except Exception:
        atomic(REG, before_reg)
        for relative in copied:
            (BIN / relative).unlink()
        raise
    # Keep the exact reviewed helper with the private host recovery inputs.
    if ('__file__' in globals() and pathlib.Path(__file__).is_file()
            and pathlib.Path(__file__).resolve() != (STATE / 'pbz-optiscaler.py').resolve()):
        shutil.copy2(__file__, STATE / 'pbz-optiscaler.py')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'verify')
