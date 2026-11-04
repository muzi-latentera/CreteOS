import unittest
from unittest.mock import patch
import tempfile
from pathlib import Path
import json
from host_presentation import conceal_library, game_window, needs_attention, restore_saved, steam_library, steam_on_other_display, windows, launch_blocker, login_blocker, LoginLog, login_window


class PresentationIsolationTests(unittest.TestCase):
    def test_existing_login_dialog_is_distinct_from_the_main_library(self):
        node={'visible':True,'name':'Sign in to Steam','window_properties':{'class':'Steam'}}
        self.assertTrue(login_window([(node,None)]))
        node['name']='Steam';self.assertFalse(login_window([(node,None)]))
    def test_login_watch_ignores_previous_instance_logout_but_reveals_a_new_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'connection_log.txt'
            path.write_text("RecvMsgClientLoggedOff('old instance')\n")
            watch=LoginLog(path)
            self.assertFalse(watch.poll())
            with path.open('a') as out:out.write('[Logging On] connecting\n')
            self.assertFalse(watch.poll())
            with path.open('a') as out:out.write("RecvMsgClientLoggedOff('Request revoked')\n")
            self.assertTrue(watch.poll())
            with path.open('a') as out:out.write("RecvMsgClientLogOnResponse() : 'OK'\n")
            self.assertFalse(watch.poll())
    def test_embedded_cloud_warning_is_revealed_without_a_separate_dialog(self):
        line = 'GameAction [AppID 4115450, ActionID 3] : LaunchApp waiting for user response to SynchronizingCloud "syncfailed"'
        self.assertEqual(launch_blocker('4115450', line), 'cloud-sync')
        self.assertIsNone(launch_blocker('411545', line))
        self.assertIsNone(launch_blocker('4115450', line.replace('waiting for user response', 'changed task')))
        self.assertEqual(launch_blocker('4115450', line.replace('syncfailed', 'conflict')), 'cloud-conflict')
        self.assertTrue(needs_attention('cloud-sync', 0, 2, []))
        self.assertTrue(needs_attention('cloud-conflict', 0, 2, []))
        self.assertFalse(needs_attention('cloud-sync', 0, .5, []))
        self.assertFalse(needs_attention('ShowInterstitials', 0, 90, []))

    def test_revoked_login_clears_only_after_successful_login(self):
        revoked = "[Logged On] RecvMsgClientLoggedOff('Request revoked')\nnot auto reconnecting due to Request revoked\n"
        self.assertTrue(login_blocker(revoked))
        self.assertTrue(login_blocker(revoked + '[Logging On] connecting\n'))
        self.assertFalse(login_blocker(revoked + "RecvMsgClientLogOnResponse() : 'OK'\n"))
        self.assertFalse(login_blocker('unrelated network error'))
    def test_nonsteam_64bit_identity_can_match_without_a_steam_window_class(self):
        ident='3137199813'
        values=('SteamGameId='+str((int(ident)<<32)|0x02000000)).encode()+b'\0'
        with patch('pathlib.Path.read_bytes',return_value=values):
            self.assertTrue(game_window({'pid':123,'window_properties':{'class':'MOUSE.exe'}},ident))
            self.assertFalse(game_window({'pid':123,'window_properties':{'class':'MOUSE.exe'}},'4014772628'))
    def test_existing_physical_steam_is_blocked_even_with_an_inherited_wayland_variable(self):
        with tempfile.TemporaryDirectory() as directory:
            proc = Path(directory)
            steam = proc / "123"
            steam.mkdir()
            (steam / "comm").write_text("steam\n")
            (steam / "environ").write_bytes(b"DISPLAY=:0\0WAYLAND_DISPLAY=wayland-1\0")
            self.assertTrue(steam_on_other_display({"DISPLAY": ":1", "WAYLAND_DISPLAY": "wayland-1"}, proc))
            self.assertFalse(steam_on_other_display({"DISPLAY": ":0"}, proc))
            (steam / "environ").unlink()
            self.assertFalse(steam_on_other_display({"DISPLAY": ":1"}, proc))

    def test_main_library_is_hidden_but_dialogs_and_games_are_not(self):
        library = {"window_properties": {"class": "steam", "title": "Steam", "window_type": "normal"}}
        self.assertTrue(steam_library(library))
        self.assertFalse(steam_library({"window_properties": {**library["window_properties"], "transient_for": 1}}))
        self.assertFalse(steam_library({"window_properties": {**library["window_properties"], "title": "Cloud conflict"}}))
        self.assertFalse(steam_library({"window_properties": {**library["window_properties"], "window_type": "dialog"}}))
        self.assertFalse(steam_library({"window_properties": {"class": "steam_app_2638890", "title": "Steam", "window_type": "normal"}}))

    def test_exact_game_window_does_not_match_other_games(self):
        node = {"window_properties": {"class": "steam_app_2638890"}}
        self.assertTrue(game_window(node, "2638890"))
        self.assertFalse(game_window(node, "883710"))

    def test_restore_workspace_is_retained_for_floating_and_hidden_windows(self):
        tree = {"nodes": [{"type": "workspace", "name": "1", "nodes": [{"pid": 1}], "floating_nodes": [{"pid": 2}]}], "floating_nodes": []}
        self.assertEqual([(n["pid"], workspace) for n, workspace in windows(tree)], [(1, "1"), (2, "1")])

    def test_background_ui_preparation_does_not_expose_steam_but_real_prompts_do(self):
        self.assertFalse(needs_attention("ShowInterstitials", 0, 15, []))
        dialog = ({"visible": True, "window_properties": {"class": "steam", "window_type": "dialog"}}, "1")
        self.assertTrue(needs_attention("ShowInterstitials", 0, 2, [dialog]))
        self.assertFalse(needs_attention(None, None, 99, [dialog]))
        self.assertFalse(needs_attention("ShowInterstitials", 0, 31, []))
        self.assertFalse(needs_attention(None, None, 44, [], 0))
        self.assertFalse(needs_attention(None, None, 45, [], 0))
        self.assertFalse(needs_attention("ProcessingShaderCache", 0, 120, [], 0))
        self.assertTrue(needs_attention("ShowInterstitials", 0, 60, [dialog], 0))

    def test_recovery_never_moves_a_game_reusing_an_old_steam_container_id(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "moved.json").write_text(json.dumps({"65": ["1", False, {}, 0]}))
            with patch("host_presentation.ipc", return_value={"nodes": [{"pid": 1, "id": 65, "window_properties": {"class": "steam_app_2638890"}}]}) as ipc:
                restore_saved(folder)
                ipc.assert_called_once_with(4)
            self.assertEqual(json.loads((folder / "moved.json").read_text()), {})

    def test_transparency_keeps_library_mapped_and_journals_before_command(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            saved = {}
            def command(kind, text):
                self.assertEqual(kind, 0)
                self.assertNotIn("scratchpad", text)
                self.assertNotIn("move", text)
                self.assertEqual(json.loads((folder / "moved.json").read_text())["65"]["mode"], "transparent")
                return [{"success": True}]
            with patch("host_presentation.ipc", side_effect=command) as ipc:
                conceal_library(folder, {"id": 65, "fullscreen_mode": 1}, saved)
                conceal_library(folder, {"id": 65, "fullscreen_mode": 0}, saved)
                ipc.assert_called_once_with(0, "[con_id=65] opacity set 0, fullscreen disable")
            self.assertEqual(saved[65]["fullscreen"], 1)

    def test_transparency_restore_preserves_geometry_and_legacy_recovery_still_works(self):
        library = {"id": 65, "pid": 1, "window_properties": {"class": "steam", "title": "Steam", "window_type": "normal"}}
        for original, expected in [
            ({"mode": "transparent", "fullscreen": 1}, "[con_id=65] opacity set 1, fullscreen enable"),
            (["1", False, {}, 0], '[con_id=65] move container to workspace "1", floating disable'),
        ]:
            with self.subTest(original=original), tempfile.TemporaryDirectory() as directory:
                folder = Path(directory)
                (folder / "moved.json").write_text(json.dumps({"65": original}))
                with patch("host_presentation.ipc", side_effect=[{"nodes": [library]}, [{"success": True}]]) as ipc:
                    restore_saved(folder)
                    self.assertEqual(ipc.call_args.args, (0, expected))
                self.assertEqual(json.loads((folder / "moved.json").read_text()), {})

    def test_failed_restoration_retains_journal_for_recovery(self):
        library = {"id": 65, "pid": 1, "window_properties": {"class": "steam", "title": "Steam", "window_type": "normal"}}
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            original = {"65": {"mode": "transparent", "fullscreen": 0}}
            (folder / "moved.json").write_text(json.dumps(original))
            with patch("host_presentation.ipc", side_effect=[{"nodes": [library]}, [{"success": False}]]):
                with self.assertRaises(OSError):
                    restore_saved(folder)
            self.assertEqual(json.loads((folder / "moved.json").read_text()), original)


if __name__ == "__main__":
    unittest.main()
