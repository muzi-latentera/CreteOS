"""Rollback must preserve unrelated registry settings and refuse edited payloads."""
import contextlib
import hashlib
import importlib.util
import io
import json
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('pbz', pathlib.Path(__file__).parents[1] / 'pbz-optiscaler.py')
pbz = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pbz)


class RollbackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = pathlib.Path(self.temp.name)
        pbz.BIN = root / 'binary'; pbz.BIN.mkdir()
        pbz.STATE = root / 'state'; pbz.STATE.mkdir()
        pbz.REG = root / 'user.reg'
        pbz.idle = lambda: None
        self.original = 'WINE REGISTRY Version 2\n\n[Unrelated] 123\n"keep"="yes"\n\n'
        self.section = '[' + pbz.KEY + '] 456\n"dxgi"="native,builtin"\n\n'
        pbz.REG.write_text(self.original + self.section)
        (pbz.BIN / 'dxgi.dll').write_bytes(b'example payload')
        self.record = {'registrySection': self.section, 'files': [{'path': 'dxgi.dll',
            'sha256': hashlib.sha256(b'example payload').hexdigest()}]}
        (pbz.STATE / 'deployment.json').write_text(json.dumps(self.record))

    def test_rollback_preserves_other_registry_sections(self):
        with contextlib.redirect_stdout(io.StringIO()):
            pbz.main('rollback')
        self.assertEqual(pbz.REG.read_text(), self.original)
        self.assertFalse((pbz.BIN / 'dxgi.dll').exists())

    def test_modified_payload_refuses_before_registry_write(self):
        (pbz.BIN / 'dxgi.dll').write_bytes(b'user changed this')
        with self.assertRaises(RuntimeError):
            pbz.main('rollback')
        self.assertEqual(pbz.REG.read_text(), self.original + self.section)
        self.assertEqual((pbz.BIN / 'dxgi.dll').read_bytes(), b'user changed this')

    def test_modified_override_refuses_before_file_removal(self):
        pbz.REG.write_text(self.original + self.section.replace('native,builtin', 'builtin'))
        with self.assertRaises(RuntimeError):
            pbz.main('rollback')
        self.assertTrue((pbz.BIN / 'dxgi.dll').exists())


if __name__ == '__main__':
    unittest.main()
