import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


class WindowsJavaManifestTest(unittest.TestCase):
    def test_utf8_codepage_preserves_existing_execution_policy_and_is_idempotent(self):
        path = Path(__file__).resolve().parents[1] / 'packaging/scripts/prepare_java.py'
        spec = importlib.util.spec_from_file_location('prepare_java', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        original = b'''<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
          <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3"><security><requestedPrivileges>
          <requestedExecutionLevel level="asInvoker" uiAccess="false"/>
          </requestedPrivileges></security></trustInfo></assembly>'''
        updated = module.utf8_manifest(original)
        root = ET.fromstring(updated)
        self.assertEqual(root.find('.//{urn:schemas-microsoft-com:asm.v3}requestedExecutionLevel').attrib,
                         {'level': 'asInvoker', 'uiAccess': 'false'})
        elements = root.findall('.//{http://schemas.microsoft.com/SMI/2019/WindowsSettings}activeCodePage')
        self.assertEqual([item.text for item in elements], ['UTF-8'])
        self.assertEqual(updated, module.utf8_manifest(updated))


if __name__ == '__main__':
    unittest.main()
