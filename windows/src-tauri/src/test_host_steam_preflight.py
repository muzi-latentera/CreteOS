import unittest
from unittest.mock import Mock, patch
from pathlib import Path
import tempfile
from host_steam_preflight import game_environment, migrate, wrong_desktop, snapshot, steam_snapshot


class SteamDesktopTests(unittest.TestCase):
    def test_correct_desktop_probe_reads_only_steam_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for pid,name in [('1','steam'),('2','protected-worker')]:
                p=root/pid;p.mkdir();(p/'comm').write_text(name)
            uid=(root/'1').stat().st_uid
            def environment(path):
                if path.parent.name=='2':raise PermissionError()
                return b'DISPLAY=:0\0'
            with patch('host_steam_preflight.os.getuid',return_value=uid,create=True),patch('pathlib.Path.read_bytes',autospec=True,side_effect=environment):
                self.assertFalse(wrong_desktop(steam_snapshot(root),{'DISPLAY':':0'}))
                with self.assertRaises(RuntimeError):snapshot(root)
    def test_protected_desktop_services_do_not_block_but_unknown_processes_do(self):
        with tempfile.TemporaryDirectory() as directory:
            process = Path(directory) / '123'
            process.mkdir()
            uid = process.stat().st_uid
            for comm, allowed in [('systemd', True), ('sway', True), ('steam', False), ('game.exe', False)]:
                with self.subTest(comm=comm):
                    (process / 'comm').write_text(comm)
                    with patch('host_steam_preflight.os.getuid', return_value=uid, create=True), patch('pathlib.Path.read_bytes', side_effect=PermissionError):
                        if allowed:
                            self.assertEqual(snapshot(Path(directory)), ([], []))
                        else:
                            with self.assertRaises(RuntimeError):
                                snapshot(Path(directory))
    def test_x11_identity_takes_precedence_over_inherited_wayland(self):
        steam = [(1, {'DISPLAY': ':1', 'WAYLAND_DISPLAY': 'wayland-1'})]
        self.assertTrue(wrong_desktop(steam, {'DISPLAY': ':0', 'WAYLAND_DISPLAY': 'wayland-1'}))
        self.assertFalse(wrong_desktop(steam, {'DISPLAY': ':1'}))
        with self.assertRaises(RuntimeError):
            wrong_desktop([(1, {})], {'DISPLAY': ':0'})

    def test_games_and_existing_correct_desktop_are_never_restarted(self):
        run = Mock()
        self.assertEqual(migrate({'DISPLAY': ':0'}, run=run, inspect=lambda: ([(1, {'DISPLAY': ':1'})], [2])), 'gameRunning')
        self.assertEqual(migrate({'DISPLAY': ':0'}, run=run, inspect=lambda: ([(1, {'DISPLAY': ':0'})], [])), 'ready')
        self.assertEqual(migrate({'DISPLAY': ':0'}, run=run, inspect=lambda: ([], [])), 'ready')
        run.assert_not_called()

    def test_idle_physical_steam_closes_normally_and_buddy_owns_restart(self):
        inspect = Mock(side_effect=[([(1, {'DISPLAY': ':1'})], []), ([], [])])
        run = Mock()
        self.assertEqual(migrate({'DISPLAY': ':0'}, run=run, inspect=inspect), 'moved')
        run.assert_called_once()
        self.assertEqual(run.call_args.args[0], ['/usr/bin/steam', '-shutdown'])
        self.assertEqual(run.call_args.kwargs['env']['DISPLAY'], ':0')

    def test_shutdown_timeout_and_a_new_game_never_force_termination(self):
        physical = ([(1, {'DISPLAY': ':1'})], [])
        run = Mock()
        self.assertEqual(migrate({'DISPLAY': ':0'}, run=run, inspect=lambda: physical, clock=Mock(side_effect=[0, 26])), 'shutdownPending')
        self.assertEqual(migrate({'DISPLAY': ':0'}, run=run, inspect=Mock(side_effect=[physical, (physical[0], [2])])), 'gameRunning')
        self.assertTrue(game_environment({'SteamAppId': '4115450'}))
        self.assertTrue(game_environment({'SteamGameId': '13474170597885870080'}))
        self.assertFalse(game_environment({'SteamAppId': '0'}))


if __name__ == '__main__':
    unittest.main()
