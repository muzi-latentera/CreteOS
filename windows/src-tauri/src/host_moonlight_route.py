"""Hold a Sunshine reply route only while the authenticated SSH lease is alive."""
import fcntl
import ipaddress
import json
import os
import select
import signal
import subprocess
import sys


TABLE = '214'
PRIORITY = 12150
PORTS = (('tcp', '47984'), ('tcp', '47989'), ('tcp', '48010'),
         ('udp', '47998-48000'), ('udp', '48002'), ('udp', '48010'))


def ip(*args, check=True):
    return subprocess.run(['/usr/bin/ip', '-4', *args], check=check, capture_output=True, text=True).stdout


def interface_address(interface, peer):
    for link in json.loads(ip('-j', 'addr', 'show', 'dev', interface)):
        if 'LOWER_UP' not in link['flags']:
            continue
        for info in link['addr_info']:
            address = ipaddress.IPv4Interface(f"{info['local']}/{info['prefixlen']}")
            if peer in address.network and peer != address.ip:
                return str(address.ip)
    raise ValueError('interfaceUnavailable')


def stop(_signal, _frame):
    raise SystemExit(0)


def hold(mode, peer):
    interface = {'wifi': 'wlan0', 'ethernet': 'eno1'}[mode]
    source = interface_address(interface, peer)
    if mode == 'wifi':
        link = subprocess.check_output(['/usr/bin/iw', 'dev', interface, 'link'], text=True)
        attributes = dict(line.strip().split(': ', 1) for line in link.splitlines() if ': ' in line)
        if attributes.get('SSID') != 'Hyperoptic Fibre E433' or not 5000 <= float(attributes.get('freq', 0)) < 6000:
            raise ValueError('wifiUnavailable')
    rules = json.loads(ip('-j', 'rule', 'show'))
    if any(PRIORITY <= rule['priority'] < PRIORITY + len(PORTS) for rule in rules) or ip('route', 'show', 'table', TABLE, check=False).strip():
        raise ValueError('routeConflict')

    prefix = f'{peer}/32'
    route = (prefix, 'dev', interface, 'src', source, 'table', TABLE)
    active_rules = []
    ip('route', 'add', *route)
    try:
        for index, (protocol, port) in enumerate(PORTS):
            rule = ('priority', str(PRIORITY + index), 'to', prefix, 'ipproto', protocol, 'sport', port, 'table', TABLE)
            ip('rule', 'add', *rule)
            active_rules.append(rule)
        for protocol, port in (('tcp', '47989'), ('udp', '47998')):
            actual = json.loads(ip('-j', 'route', 'get', str(peer), 'from', source, 'ipproto', protocol, 'sport', port, 'dport', '55000'))
            if actual[0].get('dev') != interface:
                raise ValueError('routeVerificationFailed')
        print(json.dumps({'address': source, 'interface': interface}), flush=True)
        while select.select([sys.stdin], [], [], 15)[0] and os.read(sys.stdin.fileno(), 4096):
            pass
    finally:
        for rule in reversed(active_rules):
            ip('rule', 'del', *rule, check=False)
        ip('route', 'del', *route, check=False)


def main():
    for sig in (signal.SIGHUP, signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, stop)
    peer = ipaddress.IPv4Address(sys.argv[2].split()[0])
    if not peer.is_private or peer.is_loopback or os.geteuid() != 0:
        raise ValueError('localAdminRequired')
    with open('/run/creteos-moonlight-route.lock', 'a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('routeConflict') from None
        hold(sys.argv[3], peer)


try:
    main()
except (ValueError, KeyError, IndexError, OSError, subprocess.SubprocessError) as error:
    print(json.dumps({'error': str(error) if isinstance(error, ValueError) else 'routeUnavailable'}), flush=True)
    sys.exit(1)
