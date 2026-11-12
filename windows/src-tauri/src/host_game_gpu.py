"""Select only the gaming child's AMD GPU; never export a global model setting."""
import json
import os
from pathlib import Path
import sys
import time


def choose(devices):
    matches = [d for d in devices if d['vendor'] == '0x1002' and d['device'] == '0x7550']
    if len(matches) > 1:
        raise RuntimeError('Ambiguous RX 9070 XT; refusing to select by index')
    return matches[0] if matches else None


def render_devices(sys_drm):
    for node in sys_drm.glob('renderD*'):
        device = node / 'device'
        render = Path('/dev/dri') / node.name
        try:
            if render.is_char_device():
                yield {'vendor': (device / 'vendor').read_text().strip(),
                       'device': (device / 'device').read_text().strip(),
                       'render': str(render), 'pci': device.resolve(strict=True).name}
        except FileNotFoundError:
            continue  # The driver can remove or recreate the device during boot.


def wait_for_gpu(discover, timeout=35, clock=time.monotonic, sleep=time.sleep):
    deadline = clock() + timeout
    while True:
        selected = choose(discover())
        if selected:
            return selected
        if clock() >= deadline:
            raise RuntimeError('RX 9070 XT render device unavailable; gaming services require the external GPU')
        sleep(.25)


def gaming_environment(sys_drm=Path('/sys/class/drm'), state=None):
    selected = wait_for_gpu(lambda: render_devices(sys_drm))
    state=state or Path.home()/'.local/state/creteos-host/gpu'
    state.mkdir(parents=True,exist_ok=True)
    temporary=state/('game-render.'+str(os.getpid()))
    temporary.symlink_to(selected['render'])
    temporary.replace(state/'game-render')
    (state/'selection.json').write_text(json.dumps(selected,indent=2)+'\n')
    return {'DRI_PRIME':'1002:'+selected['device'][2:]+'!',
            'WLR_RENDER_DRM_DEVICE':selected['render']}


def main(command):
    if not command: raise ValueError('A gaming executable is required')
    environment=dict(os.environ)
    environment.update(gaming_environment())
    os.execvpe(command[0],command,environment)


if __name__=='__main__': main(sys.argv[1:])
