import importlib.util
import pathlib
import sqlite3
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('lutris_inventory', pathlib.Path(__file__).with_name('lutris_inventory.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.home = pathlib.Path(self.temp.name)
        root = self.home / '.local/share/lutris'
        (root / 'games').mkdir(parents=True)
        self.db = root / 'pga.db'
        self.con = sqlite3.connect(self.db)
        self.con.execute('create table games(id,name,slug,runner,installed,configpath,playtime,lastplayed)')
        # Synthetic config/shortcut adapters: no local Lutris installation or game launch.
        self.saved = dict(sys.modules)
        for name in ['lutris','lutris.util','lutris.util.steam','lutris.util.steam.shortcut']:
            sys.modules[name] = types.ModuleType(name)
        sys.modules['lutris.util.steam.shortcut'].get_shortcuts = lambda: {}
    def tearDown(self):
        self.con.close();self.temp.cleanup()
        for name in list(sys.modules):
            if name.startswith('lutris') and name != 'lutris_inventory':
                if name in self.saved:sys.modules[name]=self.saved[name]
                else:sys.modules.pop(name,None)
    def add(self, ident, exe, hours=1.5):
        root=self.db.parent
        (root/'games'/f'g{ident}.yml').write_text('game:\n  exe: '+str(exe).replace('\\','/')+'\n')
        self.con.execute('insert into games values(?,?,?,?,?,?,?,?)',(ident,'Mouse','mouse','wine',1,f'g{ident}',hours,100));self.con.commit()
    def test_missing_lutris_is_unknown_not_a_confirmed_empty_library(self):
        with self.assertRaises(RuntimeError):module.inventory(self.home/'absent')
    def test_readable_empty_database_is_a_confirmed_empty_library(self):
        self.assertEqual(module.inventory(self.home),[])
    def test_installer_never_launches_or_claims_game_time(self):
        exe=self.home/'setup.exe';exe.touch();self.add(1,exe)
        game=module.inventory(self.home)[0]
        self.assertFalse(game['launchable']);self.assertIsNone(game['playtimeMinutes'])
    def test_numbered_installer_is_also_blocked(self):
        exe=self.home/'setup64.exe';exe.touch();self.add(1,exe)
        self.assertFalse(module.inventory(self.home)[0]['launchable'])
    def test_correcting_target_keeps_identity_and_converts_hours_once(self):
        exe=self.home/'Mouse.exe';exe.touch();self.add(1,exe,1.5)
        game=module.inventory(self.home)[0]
        self.assertTrue(game['launchable']);self.assertEqual(game['id'],'1');self.assertEqual(game['playtimeMinutes'],90)
    def test_metadata_id_cannot_replace_registered_launch_id(self):
        exe=self.home/'Mouse.exe';exe.touch();self.add(1,exe)
        with patch.object(module,'shortcuts',return_value={'0':{'AppName':'Mouse','Exe':'"/usr/bin/dbus-run-session"','LaunchOptions':'-- /usr/bin/lutris lutris:rungameid/1','appid':-1}}):
            self.assertEqual(module.inventory(self.home)[0]['shortcutId'],'4294967295')
    def test_binary_shortcut_parser_rejects_truncated_data(self):
        folder=self.home/'.local/share/Steam/userdata/1/config';folder.mkdir(parents=True)
        (folder/'shortcuts.vdf').write_bytes(b'\x00shortcuts\x00\x00noend')
        with self.assertRaises(ValueError):module.shortcuts(self.home)
    def test_unreadable_launch_config_is_not_a_confirmed_empty_library(self):
        exe=self.home/'Mouse.exe';exe.touch();self.add(1,exe)
        (self.db.parent/'games/g1.yml').unlink()
        with self.assertRaises(ValueError):module.inventory(self.home)

if __name__=='__main__':unittest.main()
