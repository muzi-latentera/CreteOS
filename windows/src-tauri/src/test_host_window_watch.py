import unittest
from host_window_watch import WindowPresence, has_game_window


class HostWindowLifecycleTests(unittest.TestCase):
    def test_never_closes_without_observing_a_stable_game_window(self):
        tracker = WindowPresence()
        self.assertIsNone(tracker.sample(False, 0))
        self.assertIsNone(tracker.sample(True, 1))
        self.assertIsNone(tracker.sample(False, 1.1))
        self.assertIsNone(tracker.sample(False, 100))

    def test_game_disappearance_precedes_lingering_steam_processes(self):
        tracker = WindowPresence()
        tracker.sample(True, 0)
        self.assertEqual(tracker.sample(True, .25), "visible")
        tracker.sample(False, 20)
        self.assertIsNone(tracker.sample(False, 20.5))
        self.assertEqual(tracker.sample(False, 20.75), "closed")

    def test_window_recreation_does_not_mean_game_exit(self):
        tracker = WindowPresence()
        tracker.sample(True, 0)
        tracker.sample(True, .25)
        tracker.sample(False, 10)
        self.assertIsNone(tracker.sample(True, 10.5))
        tracker.sample(False, 20)
        self.assertIsNone(tracker.sample(False, 20.5))

    def test_exact_game_matching_includes_other_workspaces_and_floating_windows(self):
        game = {"pid": 12, "window_properties": {"class": "steam_app_2638890"}}
        tree = {"nodes": [{"nodes": [game]}]}
        self.assertTrue(has_game_window(tree, "2638890", lambda pid: False))
        self.assertFalse(has_game_window(tree, "883710", lambda pid: False))
        self.assertTrue(has_game_window({"floating_nodes": [game]}, "2638890", lambda pid: False))

    def test_native_game_pid_match_and_unrelated_steam_window(self):
        steam = {"pid": 13, "window_properties": {"class": "Steam"}}
        self.assertFalse(has_game_window(steam, "2638890", lambda pid: False))
        self.assertTrue(has_game_window({"pid": 12}, "2638890", lambda pid: pid == 12))


if __name__ == "__main__":
    unittest.main()
