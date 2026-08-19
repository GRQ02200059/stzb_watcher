import unittest
from pathlib import Path


WORKFLOW = (
    Path(__file__).resolve().parents[1]
    / '.github/workflows/build-windows-web.yml'
).read_text(encoding='utf-8')


class WindowsReleaseWorkflowTest(unittest.TestCase):
    def test_manual_workflow_accepts_release_tag_and_publish_switch(self):
        self.assertIn('release_tag:', WORKFLOW)
        self.assertIn('publish_release:', WORKFLOW)
        self.assertIn('type: boolean', WORKFLOW)

    def test_release_publish_is_manual_and_uploads_single_exe(self):
        self.assertIn("github.event_name == 'workflow_dispatch'", WORKFLOW)
        self.assertIn('softprops/action-gh-release@v2', WORKFLOW)
        self.assertIn('tag_name: ${{ inputs.release_tag }}', WORKFLOW)
        self.assertIn('files: dist/STZB助手-Web.exe', WORKFLOW)
        self.assertIn('contents: write', WORKFLOW)


if __name__ == '__main__':
    unittest.main()
