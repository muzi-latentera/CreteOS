import unittest
from unittest.mock import Mock, patch
from pathlib import Path
import tempfile
from host_steam_preflight import game_environment, restart_idle, snapshot, steam_snapshot, stream_active


class SteamDesktopTests(unittest.TestCase):
    def test_lutris_umu_setup_with_default_ids_is_independent_of_steam(self):
        env = {'LUTRIS_GAME_UUID': 'standalone-lutris', 'STEAM_COMPAT_DATA_PATH': '/games/prefix',
               'SteamAppId': 'default', 'SteamGameId': 'default'}
        self.assertFalse(game_environment(env))
        self.assertTrue(game_environment({**env, 'SteamGameId': '13474170597885870080'}))
        self.assertTrue(game_environment({'SteamAppId': 'default'}))

    def test_normal_shutdown_tolerates_a_dying_process_without_repeating_shutdown(self):
        inspect=Mock(side_effect=[([(1,{'DISPLAY':':1'})],[]),RuntimeError('processCheckUnavailable'),([],[])])
        run=Mock()
        self.assertEqual(restart_idle({'DISPLAY':':0'},run=run,inspect=inspect,sleep=Mock(),clock=Mock(side_effect=[0,1])),'closed')
        run.assert_called_once()
    def test_persistent_uncertainty_after_shutdown_is_reported_as_pending(self):
        inspect=Mock(side_effect=[([(1,{'DISPLAY':':1'})],[]),RuntimeError('processCheckUnavailable')])
        self.assertEqual(restart_idle({'DISPLAY':':0'},run=Mock(),inspect=inspect,clock=Mock(side_effect=[0,26])),'shutdownCheckPending')
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
                self.assertEqual(steam_snapshot(root), [(1, {'DISPLAY': ':0'})])
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
    def test_exited_processes_do_not_mask_active_game_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for pid, name in [('1', 'steam'), ('2', 'firefox'), ('3', 'wine')]:
                process = root / pid
                process.mkdir()
                (process / 'comm').write_text(name)
            uid = (root / '1').stat().st_uid

            def environment(path):
                if path.parent.name == '2':
                    raise ProcessLookupError()
                if path.parent.name == '3':
                    return b'SteamAppId=4115450\0'
                return b'DISPLAY=:1\0'

            with patch('host_steam_preflight.os.getuid', return_value=uid, create=True), patch('pathlib.Path.read_bytes', autospec=True, side_effect=environment):
                steam, games = snapshot(root)
                self.assertEqual(steam, [(1, {'DISPLAY': ':1'})])
                self.assertEqual(games, [3])
                run = Mock()
                self.assertEqual(restart_idle({'DISPLAY': ':0'}, run=run, inspect=lambda: (steam, games)), 'gameRunning')
                run.assert_not_called()

    def test_running_games_and_connected_streams_are_never_interrupted(self):
        run = Mock()
        steam = [(1, {'DISPLAY': ':0'})]
        self.assertEqual(restart_idle({'DISPLAY': ':0'}, run=run, inspect=lambda: (steam, [2])), 'gameRunning')
        self.assertEqual(restart_idle({'DISPLAY': ':0'}, stream_active=lambda: True, run=run, inspect=lambda: (steam, [])), 'streamActive')
        self.assertEqual(restart_idle({'DISPLAY': ':0'}, run=run, inspect=lambda: ([], [])), 'ready')
        run.assert_not_called()

    def test_idle_steam_on_either_desktop_closes_and_buddy_owns_restart(self):
        for display in (':0', ':1'):
            with self.subTest(display=display):
                inspect = Mock(side_effect=[([(1, {'DISPLAY': display})], []), ([], [])])
                run = Mock()
                self.assertEqual(restart_idle({'DISPLAY': ':0'}, run=run, inspect=inspect), 'closed')
                run.assert_called_once()
                self.assertEqual(run.call_args.args[0], ['/usr/bin/steam', '-shutdown'])
                self.assertEqual(run.call_args.kwargs['env']['DISPLAY'], ':0')

    def test_sunshine_udp_session_is_protected_after_rtsp_closes(self):
        with patch('host_steam_preflight.subprocess.run', side_effect=[Mock(stdout=''), Mock(stdout='UNCONN 0 0 *:47999 *:* users:(("sunshine",pid=8,fd=9))')]):
            self.assertTrue(stream_active())

    def test_shutdown_timeout_and_a_new_game_never_force_termination(self):
        physical = ([(1, {'DISPLAY': ':1'})], [])
        run = Mock()
        self.assertEqual(restart_idle({'DISPLAY': ':0'}, run=run, inspect=lambda: physical, clock=Mock(side_effect=[0, 26])), 'shutdownPending')
        self.assertEqual(restart_idle({'DISPLAY': ':0'}, run=run, inspect=Mock(side_effect=[physical, (physical[0], [2])])), 'gameRunning')
        self.assertTrue(game_environment({'SteamAppId': '4115450'}))
        self.assertTrue(game_environment({'SteamGameId': '13474170597885870080'}))
        self.assertFalse(game_environment({'SteamAppId': '0'}))


if __name__ == '__main__':
    unittest.main()
