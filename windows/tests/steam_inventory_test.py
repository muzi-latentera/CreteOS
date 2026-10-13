import importlib.util
import pathlib
import tempfile
import unittest

source = pathlib.Path(__file__).resolve().parents[1] / 'src-tauri/src/steam_inventory.py'
spec = importlib.util.spec_from_file_location('steam_inventory', source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class InventoryTests(unittest.TestCase):
    def test_external_library_unicode_and_partial_installation(self):
        with tempfile.TemporaryDirectory() as temp:
            home = pathlib.Path(temp)
            steam = home / '.local/share/Steam'
            external = home / 'Extra Games'
            (steam / 'steamapps').mkdir(parents=True)
            apps = external / 'steamapps'
            (apps / 'common' / 'Returnal').mkdir(parents=True)
            (apps / 'common' / 'Downloading').mkdir()
            (steam / 'steamapps/libraryfolders.vdf').write_text('"libraryfolders" { "1" { "path" "' + external.as_posix() + '" } }')
            (apps / 'appmanifest_1649240.acf').write_text('"AppState" { "appid" "1649240" "name" "Returnal™" "StateFlags" "4" "installdir" "Returnal" }', encoding='utf-8')
            (apps / 'appmanifest_99.acf').write_text('"AppState" { "appid" "99" "StateFlags" "2" "installdir" "Downloading" }')
            (apps / 'appmanifest_98.acf').write_text('"AppState" { "appid" "98" "StateFlags" "4" "installdir" "Removed" }')
            self.assertEqual(module.inventory(home), [{'steamAppId': '1649240', 'name': 'Returnal™'}])
    def test_missing_steam_is_unknown_not_an_empty_installed_library(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(RuntimeError): module.inventory(pathlib.Path(temp))
    def test_manifest_cannot_escape_common_folder(self):
        with tempfile.TemporaryDirectory() as temp:
            home = pathlib.Path(temp)
            apps = home / '.local/share/Steam/steamapps'
            (apps / 'outside').mkdir(parents=True)
            (apps / 'appmanifest_123.acf').write_text('"AppState" { "appid" "123" "StateFlags" "4" "installdir" "../outside" }')
            self.assertEqual(module.inventory(home), [])

if __name__ == '__main__': unittest.main()
