"""Verify the delivered ZIP, without importing application source or host runtimes."""
import argparse
from contextlib import closing, contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import sqlite3
import subprocess
import sys
import tempfile
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import zipfile


def fetch_json(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode('utf-8')
    request = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            body = json.load(response)
    except urllib.error.HTTPError as error:
        with error:
            detail = error.read(65536).decode('utf-8', errors='replace')
        raise RuntimeError(f'{url}: HTTP {error.code}: {detail}') from error
    if not isinstance(body, dict) or body.get('ok') is not True:
        raise RuntimeError(f'{url}: application request failed: {body}')
    return body


def wait_ready(process, url, timeout=60):
    deadline = time.monotonic() + timeout
    while True:
        if process.poll() is not None:
            raise RuntimeError(f'Application exited before readiness: {process.returncode}')
        if time.monotonic() >= deadline:
            raise RuntimeError(f'Application readiness timed out: {url}')
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                html = response.read()
            if b'<html' in html.lower() or b'<!doctype html' in html.lower():
                return html.decode('utf-8')
            raise RuntimeError('Home page is not HTML')
        except (OSError, urllib.error.URLError):
            time.sleep(0.25)


@contextmanager
def running_app(bundle, working_directory, log_file):
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    env = runtime_environment()
    system32 = Path(os.environ['SystemRoot']) / 'System32'
    command = [str(bundle / 'STZB助手-Web.exe'), '--no-browser', '--no-sniffer',
               '--host', '127.0.0.1', '--port', str(port)]
    with log_file.open('wb') as log:
        process = subprocess.Popen(command, cwd=working_directory, env=env,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NEW_PROCESS_GROUP)
        try:
            yield process, f'http://127.0.0.1:{port}'
        finally:
            subprocess.run([str(system32 / 'taskkill.exe'), '/PID', str(process.pid), '/T', '/F'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
            process.wait(timeout=10)


def runtime_environment():
    # Whitelist OS necessities only: no CI JAVA_HOME, PYTHONPATH or developer PATH.
    allowed = {'SYSTEMROOT', 'WINDIR', 'TEMP', 'TMP', 'USERPROFILE',
               'APPDATA', 'LOCALAPPDATA', 'COMSPEC', 'SYSTEMDRIVE'}
    env = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    system32 = Path(os.environ['SystemRoot']) / 'System32'
    env['PATH'] = str(system32)
    return env


def java_diagnostics(bundle):
    runtime = bundle / 'runtime/java'

    def probe(java):
        process = subprocess.run([str(java), '-version'], env=runtime_environment(),
                                 capture_output=True, encoding='utf-8', errors='replace', timeout=15)
        return {'returncode': process.returncode, 'output': process.stdout + process.stderr}

    result = {'javaDllPresent': (runtime / 'bin/java.dll').is_file(),
              'unicodeDirectory': probe(runtime / 'bin/java.exe')}
    if result['unicodeDirectory']['returncode'] != 0:
        with tempfile.TemporaryDirectory(prefix='STZBJavaASCII-') as directory:
            copied = Path(directory) / 'java'
            shutil.copytree(runtime, copied)
            result['asciiDirectory'] = probe(copied / 'bin/java.exe')
    return result


def verify_inventory(bundle):
    info = json.loads((bundle / 'build-info.json').read_text(encoding='utf-8-sig'))
    if info.get('dirty') or not re.fullmatch(r'[0-9a-f]{40}', info.get('commit', '')):
        raise RuntimeError('Package must identify a clean source commit')
    files = info.get('files', [])
    if not files:
        raise RuntimeError('Empty package inventory')
    for item in files:
        path = (bundle / item['path']).resolve()
        if not path.is_relative_to(bundle.resolve()):
            raise RuntimeError('Package inventory escapes bundle')
        if path.suffix in {'.db', '.sqlite', '.sqlite3', '.pcap'} or path.name in {'profiles.json', 'current_profile.json'}:
            raise RuntimeError(f'User data found in package: {item["path"]}')
        if hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
            raise RuntimeError(f'Package file checksum mismatch: {item["path"]}')
    for required in ('STZB助手-Web.exe', 'runtime/java/bin/java.exe'):
        if not (bundle / required).is_file():
            raise RuntimeError(f'Missing runtime: {required}')
    if not list((bundle / 'runtime/battle-engine/lib').glob('*.jar')):
        raise RuntimeError('Missing battle engine JARs')
    return info


def verify_http(base, html, bundle):
    assets = sorted(set(re.findall(r'''(?:src|href)=["'](/static/[^"']+)["']''', html)))
    if not assets:
        raise RuntimeError('Home page has no local assets')
    for asset in assets:
        with urllib.request.urlopen(base + asset, timeout=10) as response:
            actual = response.read()
        relative = urllib.parse.unquote(asset.split('?', 1)[0]).lstrip('/')
        expected = (bundle / '_internal' / relative).read_bytes()
        if not actual or actual != expected:
            raise RuntimeError(f'Incorrect static response: {asset}')
    manifest = fetch_json(base + '/api/intelligence/config/manifest')
    if not manifest.get('datasetVersion'):
        raise RuntimeError('Missing intelligence dataset version')
    research = fetch_json(base + '/api/intelligence/research/summary')
    if not research.get('datasetVersion'):
        raise RuntimeError('Missing research dataset version')
    heroes = fetch_json(base + '/api/simulate/heroes')
    if len(heroes.get('heroes', [])) < 500 or len(heroes.get('skills', [])) < 200:
        raise RuntimeError('Hero/skill resources are incomplete')
    metadata = fetch_json(base + '/api/simulate/engine')
    if not metadata.get('sourceCommit'):
        raise RuntimeError('Missing engine source metadata')
    result = fetch_json(base + '/api/simulate', {
        'repeat': 1, 'seed': 20260810,
        'blue': {'morale': 100, 'heros': [{'id': 100027, 'level': 40, 'position': 0}]},
        'red': {'morale': 100, 'heros': [{'id': 100013, 'level': 40, 'position': 0}]},
    })
    if not result.get('engineResult', {}).get('firstRun', {}).get('events'):
        raise RuntimeError('Bundled Java did not return a real battle result')
    return {'assetsChecked': len(assets), 'heroes': len(heroes['heroes']),
            'skills': len(heroes['skills']), 'engineCommit': metadata['sourceCommit']}


def verify_package(archive, checksums, logs, report):
    actual_hash = hashlib.sha256(archive.read_bytes()).hexdigest()
    expected = {line.split()[1]: line.split()[0] for line in checksums.read_text().splitlines() if line.strip()}
    if expected.get(archive.name) != actual_hash:
        raise RuntimeError('ZIP checksum mismatch')
    report['zipSha256'] = actual_hash
    with tempfile.TemporaryDirectory(prefix='STZB 中文 package ') as temp:
        extracted = Path(temp) / '应用 files'
        with zipfile.ZipFile(archive) as package:
            package.extractall(extracted)
        bundle = extracted / 'STZB-Web'
        info = verify_inventory(bundle)
        report['commit'] = info['commit']
        report['javaDiagnostics'] = java_diagnostics(bundle)
        work = Path(temp) / 'unrelated empty working directory'
        work.mkdir()
        with running_app(bundle, work, logs / 'startup.log') as (process, base):
            html = wait_ready(process, base + '/')
            report['http'] = verify_http(base, html, bundle)
            if process.poll() is not None:
                raise RuntimeError('Application exited during verification')
        database = bundle / 'stzb.db'
        if not database.is_file():
            raise RuntimeError('First launch did not create stzb.db')
        with closing(sqlite3.connect(database)) as connection:
            connection.execute('SELECT 1 FROM battles_v2 LIMIT 1')
            connection.execute('CREATE TABLE packaging_smoke_marker (value TEXT)')
            connection.execute("INSERT INTO packaging_smoke_marker VALUES ('persisted')")
            connection.commit()
        with running_app(bundle, work, logs / 'restart.log') as (process, base):
            wait_ready(process, base + '/')
            with closing(sqlite3.connect(database)) as connection:
                if connection.execute('SELECT value FROM packaging_smoke_marker').fetchone() != ('persisted',):
                    raise RuntimeError('Data did not survive application restart')
        if list((bundle / '_internal').rglob('*.db')):
            raise RuntimeError('Application wrote user data into bundled resources')
        report['restartAndDatabase'] = 'passed'

        missing = bundle / '_internal/data/intelligence/client-9.2.2/manifest.json'
        missing.rename(missing.with_suffix('.json.removed'))
        with running_app(bundle, work, logs / 'missing-resource.log') as (process, base):
            try:
                wait_ready(process, base + '/', timeout=15)
            except RuntimeError:
                process.wait(timeout=5)
                evidence = (logs / 'missing-resource.log').read_text(encoding='utf-8', errors='replace')
                if process.returncode == 0 or 'manifest.json' not in evidence or 'FileNotFoundError' not in evidence:
                    raise RuntimeError('Fault injection failed for an unrelated reason')
            else:
                raise RuntimeError('Missing mandatory resource was not rejected')
        report['missingResourceNegativeControl'] = 'passed'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--checksums', type=Path, required=True)
    parser.add_argument('--logs', type=Path, required=True)
    args = parser.parse_args()
    args.logs.mkdir(parents=True, exist_ok=True)
    report = {'ok': False, 'npcapLiveCapture': 'not tested', 'authenticationService': 'not tested'}
    try:
        if sys.platform != 'win32':
            raise RuntimeError('Delivered EXE verification requires Windows')
        verify_package(args.archive.resolve(), args.checksums.resolve(), args.logs.resolve(), report)
        report['ok'] = True
    except Exception as error:
        report['error'] = str(error)
        traceback.print_exc()
    finally:
        (args.logs / 'smoke-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    return 0 if report['ok'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
