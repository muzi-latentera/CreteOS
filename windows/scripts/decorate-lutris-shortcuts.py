"""Add official public Steam artwork to existing Lutris shortcuts, Steam closed.

Store IDs are metadata only; never replace a shortcut's executable or launch ID.
Existing art is backed up before replacement. No Steam account API key required.
"""
import datetime
import hashlib
import json
import pathlib
import re
import shutil
import urllib.parse
import urllib.request


def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'CreteOS artwork setup/1.0'})
    with urllib.request.urlopen(request, timeout=20) as response:
        data = response.read(16 * 1024 * 1024 + 1)
        if len(data) > 16 * 1024 * 1024:
            raise ValueError('Artwork response too large')
        return data


def normalized(name):
    return ''.join(c.lower() for c in name if c.isalnum())


def main():
    for process in pathlib.Path('/proc').iterdir():
        try:
            if process.name.isdigit() and (process/'comm').read_text().strip() == 'steam':
                raise RuntimeError('Close Steam normally before applying artwork')
        except (FileNotFoundError, PermissionError):
            pass
    from lutris.util.steam import vdf
    from lutris.util.steam.shortcut import get_shortcuts_vdf_path
    path = pathlib.Path(get_shortcuts_vdf_path())
    original = path.read_bytes()
    shortcuts = vdf.binary_loads(original)
    grid = path.parent/'grid'
    grid.mkdir(exist_ok=True)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = path.parent/('creteos-art-backup-'+stamp)
    backup.mkdir()
    shutil.copy2(path, backup/'shortcuts.vdf')
    records = []
    for entry in shortcuts['shortcuts'].values():
        if entry.get('Exe') != '"/usr/bin/dbus-run-session"' or not re.fullmatch(r'-- /usr/bin/lutris lutris:rungameid/[0-9]+', entry.get('LaunchOptions', '')):
            continue
        name = entry['AppName']
        search = json.loads(fetch('https://store.steampowered.com/api/storesearch/?'+urllib.parse.urlencode({'term': name, 'cc': 'GB', 'l': 'english'})))
        matches = [item for item in search.get('items', []) if normalized(item['name']) == normalized(name)]
        if len(matches) != 1:
            records.append({'name': name, 'error': 'No unambiguous exact public store match'})
            continue
        store = str(matches[0]['id'])
        details = json.loads(fetch('https://store.steampowered.com/api/appdetails/?'+urllib.parse.urlencode({'appids': store, 'cc': 'GB', 'l': 'english'})))[store]
        if not details.get('success'):
            records.append({'name': name, 'error': 'Store metadata unavailable'})
            continue
        data = details['data']
        browse_query = {'ids': [{'appid': int(store)}], 'context': {'language': 'english', 'country_code': 'GB'}, 'data_request': {'include_assets': True}}
        browse = json.loads(fetch('https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?'+urllib.parse.urlencode({'input_json': json.dumps(browse_query)})))
        item = next((item for item in browse.get('response', {}).get('store_items', []) if str(item.get('appid')) == store), {})
        assets = item.get('assets', {})
        template = assets.get('asset_url_format', '')
        def asset(keys, fallback):
            if template.startswith('steam/apps/'+store+'/') and '${FILENAME}' in template:
                for key in keys:
                    file = assets.get(key)
                    if file and not any(part in file for part in ('..', ':', '\\')) and not file.startswith('/'):
                        return 'https://shared.fastly.steamstatic.com/store_item_assets/'+template.replace('${FILENAME}', file)
            return fallback
        ident = str(int(entry['appid']) & 0xffffffff)
        result = {'name': name, 'shortcutId': ident, 'metadataStoreId': store, 'description': bool(data.get('short_description')), 'trailers': len(data.get('movies', [])), 'art': [], 'missing': []}
        base = 'https://cdn.cloudflare.steamstatic.com/steam/apps/'+store+'/'
        images = [(ident+'p.jpg', asset(['library_capsule_2x', 'library_capsule'], base+'library_600x900.jpg')), (ident+'_hero.jpg', asset(['library_hero_2x', 'library_hero'], base+'library_hero.jpg')), (ident+'_logo.png', asset(['library_logo', 'logo'], base+'logo.png')), (ident+'.jpg', data['header_image'])]
        icon = assets.get('community_icon', '')
        if re.fullmatch('[0-9a-f]{40}', icon):
            images.append((ident+'_icon.jpg', 'https://cdn.akamai.steamstatic.com/steamcommunity/public/images/apps/'+store+'/'+icon+'.jpg'))
        for filename, url in images:
            try:
                content = fetch(url)
                if not (content.startswith(b'\xff\xd8') or content.startswith(b'\x89PNG')):
                    raise ValueError('Not a JPEG/PNG image')
                target = grid/filename
                if target.exists():
                    shutil.copy2(target, backup/filename)
                temp = target.with_suffix(target.suffix+'.tmp')
                temp.write_bytes(content)
                temp.replace(target)
                result['art'].append({'file': filename, 'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()})
                if filename.endswith('_icon.jpg') or (filename.endswith('_logo.png') and not icon):
                    entry['icon'] = str(target)
            except Exception as error:
                result['missing'].append({'file': filename, 'reason': type(error).__name__})
        records.append(result)
    encoded = vdf.binary_dumps(shortcuts)
    if len(vdf.binary_loads(encoded)['shortcuts']) != len(shortcuts['shortcuts']):
        raise RuntimeError('Shortcut roundtrip failed')
    temp = path.with_name('shortcuts.artwork.tmp')
    temp.write_bytes(encoded)
    temp.replace(path)
    report = {'time': stamp, 'backup': str(backup), 'games': records, 'note': 'Steam non-Steam shortcuts retain their own identities. Descriptions and trailers are supplied by CreteOS; Steam store ownership and achievements are not granted.'}
    (grid/'creteos-lutris-artwork.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == '__main__':
    main()
