import unittest
from host_game_gpu import choose
class GamingGpuTests(unittest.TestCase):
    def test_prefers_egpu_independent_of_order_and_falls_back_only_if_absent(self):
        igpu={'vendor':'0x1002','device':'0x1586','render':'/dev/dri/renderD129'}
        egpu={'vendor':'0x1002','device':'0x7550','render':'/dev/dri/renderD128'}
        self.assertEqual(choose([igpu,egpu]),egpu)
        self.assertEqual(choose([egpu,igpu]),egpu)
        self.assertEqual(choose([igpu]),igpu)
    def test_ambiguity_and_unknown_hardware_fail_explicitly(self):
        gpu={'vendor':'0x1002','device':'0x7550'}
        with self.assertRaises(RuntimeError): choose([gpu,gpu])
        with self.assertRaises(RuntimeError): choose([])
if __name__=='__main__': unittest.main()
