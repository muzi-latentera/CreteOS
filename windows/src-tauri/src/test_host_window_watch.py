import unittest
from host_window_watch import WindowPresence, has_game_window, preparation_phase, environment_matches


class HostWindowLifecycleTests(unittest.TestCase):
    def test_nonsteam_64bit_game_id_matches_only_exact_registered_shortcut(self):
        app_id='3137199813'
        values=[b'SteamAppId=0', ('SteamGameId='+str((int(app_id)<<32)|0x02000000)).encode()]
        self.assertTrue(environment_matches(values,app_id))
        self.assertFalse(environment_matches(values,'4014772628'))
    def test_progress_is_exact_game_and_reports_no_log_content(self):
        self.assertEqual(preparation_phase("2638890", "Still replaying 2638890: 50%", ""), "compiling")
        self.assertIsNone(preparation_phase("263889", "Still replaying 2638890: 50%", ""))
        self.assertEqual(preparation_phase("2638890", "", "AppID 2638890 update started"), "updating")
        self.assertIsNone(preparation_phase("263889", "", "AppID 2638890 update started"))
        self.assertEqual(preparation_phase("2638890", "Destroyed compile job 2638890", ""), "starting")

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
