import unittest
from host_stream_env import validate_environment


class StreamServiceEnvironmentTests(unittest.TestCase):
    def test_uses_compositor_display_and_does_not_export_unrelated_values(self):
        values = {"DISPLAY": ":3", "WAYLAND_DISPLAY": "wayland-2", "SWAYSOCK": "/run/user/1000/sway-sunshine.sock", "UNRELATED": "private"}
        result = validate_environment(values, "/run/user/1000")
        self.assertEqual(result["DISPLAY"], ":3")
        self.assertNotIn("UNRELATED", result)

    def test_rejects_a_different_compositor_or_missing_display(self):
        with self.assertRaises(ValueError):
            validate_environment({"SWAYSOCK": "/run/user/1000/regular-desktop.sock"}, "/run/user/1000")
        with self.assertRaises(ValueError):
            validate_environment({"SWAYSOCK": "/run/user/1000/sway-sunshine.sock"}, "/run/user/1000")
