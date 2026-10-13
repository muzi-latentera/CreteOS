"""Read-only Steam inventory for CreteOS. No credentials or launch commands."""
import json
import pathlib
import re


def vdf(text):
    tokens = re.findall(r'"(?:\\.|[^"\\])*"|[{}]', text)
    position = 0
    def parse():
        nonlocal position
        result = {}
        while position < len(tokens):
            key = tokens[position]
            position += 1
            if key == '}':
                return result
            if position >= len(tokens):
                break
            value = tokens[position]
            position += 1
            key = key[1:-1].replace('\\\\', '\\').replace('\\"', '"')
            result[key] = parse() if value == '{' else value[1:-1].replace('\\\\', '\\').replace('\\"', '"')
        return result
    return parse()


def inventory(home):
    roots = [home / '.local/share/Steam', home / '.steam/steam', home / '.steam/root', home / '.var/app/com.valvesoftware.Steam/.local/share/Steam']
    libraries = set()
    found = False
    for root in roots:
        if not root.is_dir():
            continue
        found = True
        libraries.add(root.resolve())
        for config in [root / 'steamapps/libraryfolders.vdf', root / 'config/libraryfolders.vdf']:
            if not config.is_file():
                continue
            try:
                folders = vdf(config.read_text(encoding='utf-8', errors='replace')).get('libraryfolders', {})
                for index, folder in folders.items():
                    if not index.isdigit():
                        continue
                    path = folder.get('path') if isinstance(folder, dict) else folder
                    if path:
                        libraries.add(pathlib.Path(path).expanduser().resolve())
            except (OSError, ValueError):
                continue
    games = {}
    failures = 0
    for library in libraries:
        try:
            manifests = list((library / 'steamapps').glob('appmanifest_*.acf'))
        except OSError:
            failures += 1
            continue
        for manifest in manifests:
            try:
                state = vdf(manifest.read_text(encoding='utf-8', errors='replace')).get('AppState', {})
                app_id = str(state.get('appid', ''))
                directory = state.get('installdir', '')
                if not app_id.isdigit() or not directory or not int(state.get('StateFlags', '0')) & 4:
                    continue
                common = (library / 'steamapps/common').resolve()
                install = (common / directory).resolve()
                if not install.is_relative_to(common) or not install.is_dir():
                    continue
                games[app_id] = {'steamAppId': app_id, 'name': state.get('name', app_id)}
            except (OSError, ValueError):
                failures += 1
    if not found:
        raise RuntimeError('No Steam library found for this host login')
    if failures:
        raise RuntimeError('Some Steam installation records could not be read')
    return sorted(games.values(), key=lambda game: int(game['steamAppId']))


if __name__ == '__main__':
    print(json.dumps(inventory(pathlib.Path.home()), ensure_ascii=False))
