import unittest
from unittest.mock import patch
import tempfile
from pathlib import Path
import json
from host_presentation import conceal_library, game_window, needs_attention, restore_saved, steam_library, steam_on_other_display, windows


class PresentationIsolationTests(unittest.TestCase):
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
        self.assertTrue(needs_attention("ShowInterstitials", 0, 31, []))
        self.assertFalse(needs_attention(None, None, 44, [], 0))
        self.assertTrue(needs_attention(None, None, 45, [], 0))

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
