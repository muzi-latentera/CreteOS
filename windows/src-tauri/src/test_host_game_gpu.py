import unittest
from unittest.mock import Mock
from host_game_gpu import choose, wait_for_gpu
class GamingGpuTests(unittest.TestCase):
    def test_selects_only_the_egpu(self):
        igpu={'vendor':'0x1002','device':'0x1586','render':'/dev/dri/renderD129'}
        egpu={'vendor':'0x1002','device':'0x7550','render':'/dev/dri/renderD128'}
        self.assertEqual(choose([igpu,egpu]),egpu)
        self.assertEqual(choose([egpu,igpu]),egpu)
        self.assertIsNone(choose([igpu]))
    def test_ambiguity_and_unknown_hardware_fail_explicitly(self):
        gpu={'vendor':'0x1002','device':'0x7550'}
        with self.assertRaises(RuntimeError): choose([gpu,gpu])
        self.assertIsNone(choose([]))

    def test_waits_for_a_late_external_render_device(self):
        gpu = {'vendor': '0x1002', 'device': '0x7550'}
        discover = Mock(side_effect=[[], [gpu]])
        sleep = Mock()
        self.assertEqual(wait_for_gpu(discover, clock=Mock(return_value=0), sleep=sleep), gpu)
        sleep.assert_called_once_with(.25)

    def test_integrated_gpu_never_starts_the_gaming_services(self):
        integrated = {'vendor': '0x1002', 'device': '0x1586'}
        with self.assertRaisesRegex(RuntimeError, 'RX 9070 XT render device unavailable'):
            wait_for_gpu(lambda: [integrated], timeout=0, clock=Mock(return_value=0))
if __name__=='__main__': unittest.main()
