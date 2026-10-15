import unittest
from unittest.mock import patch
import tempfile
from pathlib import Path
import json
from host_presentation import game_window, needs_attention, restore_saved, steam_library, windows


class PresentationIsolationTests(unittest.TestCase):
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

    def test_recovery_never_moves_a_game_reusing_an_old_steam_container_id(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "moved.json").write_text(json.dumps({"65": ["1", False, {}, 0]}))
            with patch("host_presentation.ipc", return_value={"nodes": [{"pid": 1, "id": 65, "window_properties": {"class": "steam_app_2638890"}}]}) as ipc:
                restore_saved(folder)
                ipc.assert_called_once_with(4)
            self.assertEqual(json.loads((folder / "moved.json").read_text()), {})


if __name__ == "__main__":
    unittest.main()
