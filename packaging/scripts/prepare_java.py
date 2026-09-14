"""Enable UTF-8 Win32 paths for the bundled Java 17 launcher (Windows 10 1903+)."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ASM1 = 'urn:schemas-microsoft-com:asm.v1'
ASM3 = 'urn:schemas-microsoft-com:asm.v3'
SETTINGS = 'http://schemas.microsoft.com/SMI/2019/WindowsSettings'


def utf8_manifest(manifest):
    ET.register_namespace('', ASM1)
    ET.register_namespace('asmv3', ASM3)
    ET.register_namespace('ws', SETTINGS)
    root = ET.fromstring(manifest)
    application = root.find(f'{{{ASM3}}}application')
    if application is None:
        application = ET.SubElement(root, f'{{{ASM3}}}application')
    settings = application.find(f'{{{ASM3}}}windowsSettings')
    if settings is None:
        settings = ET.SubElement(application, f'{{{ASM3}}}windowsSettings')
    codepage = settings.find(f'{{{SETTINGS}}}activeCodePage')
    if codepage is None:
        codepage = ET.SubElement(settings, f'{{{SETTINGS}}}activeCodePage')
    codepage.text = 'UTF-8'
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def prepare(executable):
    if sys.platform != 'win32':
        raise RuntimeError('Java PE manifest preparation requires Windows')
    from PyInstaller.utils.win32 import winmanifest, winresource

    filename = str(executable)
    # Keep the vendor's execution policy and other settings, including non-neutral languages.
    manifests = winresource.get_resources(filename, [24])[24][1]
    original = manifests.get(0, next(iter(manifests.values())))
    updated = utf8_manifest(original)
    winmanifest.write_manifest_to_executable(filename, updated)
    readback = winresource.get_resources(filename, [24])[24][1]
    if not all(utf8_manifest(value) == value for value in readback.values()):
        raise RuntimeError('Java UTF-8 manifest read-back verification failed')
    print(f'Enabled UTF-8 process code page: {executable}')


if __name__ == '__main__':
    prepare(Path(sys.argv[1]))
