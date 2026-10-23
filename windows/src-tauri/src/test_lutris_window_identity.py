import tempfile
import unittest
from pathlib import Path
from host_window_watch import process_matches as watch_matches
from host_presentation import process_matches as presentation_matches


class LutrisAncestryTests(unittest.TestCase):
    def test_umu_default_ids_match_only_their_registered_shortcut_ancestor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for pid, parent, env in [(50, 40, b'SteamAppId=default'), (40, 30, b'SteamGameId=default'),
                                     (30, 1, b'SteamGameId=13474170597885870080')]:
                p = root / str(pid)
                p.mkdir()
                (p / 'environ').write_bytes(env + b'\0')
                (p / 'stat').write_text(f'{pid} (name with ) space) S {parent} 0 0')
            for match in (watch_matches, presentation_matches):
                self.assertTrue(match(50, '3137199813', root))
                self.assertFalse(match(50, '4014772628', root))
                self.assertFalse(match(99, '3137199813', root))
                (root / '40' / 'stat').write_text('40 (cycle) S 50 0 0')
                self.assertFalse(match(50, '3137199813', root))
                (root / '40' / 'stat').write_text('40 (container) S 30 0 0')

    def test_unrelated_window_does_not_match_an_existing_game_process(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for pid, env in [(50, b'SteamAppId=default'), (60, b'SteamAppId=3137199813')]:
                p=root/str(pid);p.mkdir()
                (p/'environ').write_bytes(env+b'\0')
                (p/'stat').write_text(f'{pid} (window) S 1 0 0')
            for match in (watch_matches, presentation_matches):
                self.assertFalse(match(50, '3137199813', root))
                self.assertTrue(match(60, '3137199813', root))


if __name__ == '__main__':
    unittest.main()
