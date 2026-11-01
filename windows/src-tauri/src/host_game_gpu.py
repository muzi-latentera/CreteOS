"""Select only the gaming child's AMD GPU; never export a global model setting."""
import json
import os
from pathlib import Path
import sys


def choose(devices):
    for wanted in ('0x7550', '0x1586'):
        matches = [d for d in devices if d['vendor'] == '0x1002' and d['device'] == wanted]
        if len(matches) > 1:
            raise RuntimeError('Ambiguous gaming GPU; refusing to select by index')
        if matches:
            return matches[0]
    raise RuntimeError('Neither RX 9070 XT nor supported fallback iGPU is available')


def gaming_environment(sys_drm=Path('/sys/class/drm'), state=None):
    devices=[]
    for node in sys_drm.glob('renderD*'):
        device=node/'device'
        devices.append({'vendor':(device/'vendor').read_text().strip(),
                        'device':(device/'device').read_text().strip(),
                        'render':'/dev/dri/'+node.name,'pci':device.resolve().name})
    selected=choose(devices)
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
