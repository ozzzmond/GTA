"""Standard-library integration tests; all mutations occur in disposable Git repositories."""
import json
import os
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1]

class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for script in ['release_web.py', 'release_android.py']:
            shutil.copy2(SOURCE / script, self.root / script)
        self.write('.github/release_metadata.py', (SOURCE / '.github/release_metadata.py').read_text(encoding='utf-8'))
        self.write('web/package.json', json.dumps({'name': 'web', 'version': '1.0.50-dev.12'}))
        self.write('web/package-lock.json', json.dumps({'version': '1.0.50-dev.12', 'packages': {'': {'version': '1.0.50-dev.12'}, 'node_modules/keep': {'version': '4.5.6'}}}))
        self.write('web/src/types/gtar.ts', "export const GTAR_DEV_VERSION = '1.0.50-dev.12'\nexport const GTAR_APP_VERSION = '1.1.50'\n")
        for file in ['web/src/App.tsx', 'web/src/components/Header.tsx']:
            self.write(file, 'const label = `v${GTAR_DEV_VERSION}`\nconst prod = `v${GTAR_APP_VERSION}`\n')
        self.write('app/build.gradle.kts', 'android {\n    versionCode = 66\n    versionName = "v1.0.50"\n    debug {\n        versionNameSuffix = "-dev.12"\n    }\n}\n')
        self.git('init', '-b', 'dev')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'release@example.test')
        self.git('add', '.')
        self.git('commit', '-m', 'fixture')
    def write(self, path, content):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding='utf-8')
    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True, stderr=subprocess.STDOUT).strip()
    def run_script(self, platform, *args, success=True):
        result = subprocess.run([sys.executable, str(self.root / f'release_{platform}.py'), *args], cwd=self.root, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0 if success else 1, result.stdout + result.stderr)
        return result.stdout + result.stderr
    def test_inspection_and_dry_runs_never_mutate(self):
        head = self.git('rev-parse', 'HEAD')
        for platform in ['web', 'android']:
            self.run_script(platform)
            for action in ['--bump-dev', '--promote-to-prod']:
                self.assertIn('[DRY RUN]', self.run_script(platform, action, '--dry-run'))
        self.assertEqual(self.git('status', '--porcelain'), '')
        self.assertEqual(self.git('tag'), '')
        self.assertEqual(self.git('rev-parse', 'HEAD'), head)
    def test_web_dev_bump_updates_badges_and_lock_only(self):
        self.run_script('web', '--bump-dev')
        self.assertEqual(json.loads((self.root / 'web/package.json').read_text())['version'], '1.0.50-dev.13')
        lock = json.loads((self.root / 'web/package-lock.json').read_text())
        self.assertEqual(lock['packages']['']['version'], '1.0.50-dev.13')
        self.assertEqual(lock['packages']['node_modules/keep']['version'], '4.5.6')
        self.assertIn('web v${GTAR_DEV_VERSION}', (self.root / 'web/src/App.tsx').read_text())
        self.assertEqual(self.git('tag'), '')
    def test_web_promotion_tags_prod_then_resets_dev(self):
        self.run_script('web', '--promote-to-prod')
        tagged = json.loads(self.git('show', 'web-v1.1.62:web/package.json'))
        self.assertEqual(tagged['version'], '1.1.62')
        self.assertEqual(json.loads((self.root / 'web/package.json').read_text())['version'], '1.0.62-dev.1')
        self.assertIn("GTAR_APP_VERSION = '1.1.62'", (self.root / 'web/src/types/gtar.ts').read_text())
        self.assertEqual(self.git('branch', '--show-current'), 'dev')
        self.assertEqual(self.git('status', '--porcelain'), '')
        self.assertEqual(self.git('rev-list', '--count', 'HEAD'), '3')
    def test_android_promotion_has_correct_tag_suffix_and_monotonic_codes(self):
        self.run_script('android', '--promote-to-prod')
        tagged = self.git('show', 'app-v1.1.62:app/build.gradle.kts')
        self.assertIn('versionCode = 67', tagged)
        self.assertIn('versionName = "app v1.1.62"', tagged)
        self.assertIn('versionNameSuffix = ""', tagged)
        current = (self.root / 'app/build.gradle.kts').read_text()
        self.assertIn('versionCode = 68', current)
        self.assertIn('versionName = "app v1.0.62"', current)
        self.assertIn('versionNameSuffix = "-dev.1"', current)
        self.run_script('android', '--bump-dev')
        self.assertIn('versionCode = 69', (self.root / 'app/build.gradle.kts').read_text())
    def test_dirty_wrong_branch_and_existing_tags_are_rejected(self):
        self.write('unrelated.txt', 'Keep this')
        self.assertIn('clean', self.run_script('web', '--promote-to-prod', success=False))
        (self.root / 'unrelated.txt').unlink()
        self.git('checkout', '-b', 'main')
        self.assertIn('dev branch', self.run_script('android', '--bump-dev', success=False))
        self.git('checkout', 'dev')
        self.git('tag', 'web-v1.1.62')
        self.assertIn('already exists', self.run_script('web', '--promote-to-prod', success=False))
        self.assertEqual(self.git('status', '--porcelain'), '')
    def test_legacy_iteration_requires_explicit_mapping(self):
        for name in ['web/package.json', 'web/package-lock.json', 'web/src/types/gtar.ts']:
            path = self.root / name
            path.write_text(path.read_text().replace('1.0.50-dev.12', '1.0.62-DEV.8b'))
        self.assertIn('--legacy-iteration', self.run_script('web'))
        self.run_script('web', '--bump-dev', '--dry-run', success=False)
        output = self.run_script('web', '--promote-to-prod', '--dry-run', '--legacy-iteration', '10')
        self.assertIn('web v1.1.72', output)
    def test_integer_standard_and_platform_prefixes(self):
        import runpy
        for script, prefix in [('release_web.py', 'web'), ('release_android.py', 'app')]:
            parse = runpy.run_path(str(self.root / script))['parse_dev']
            self.assertEqual(parse(f'{prefix} v1.0.62-dev.9', None), (62, 9))
            for version in ['1.0.62-dev.8a', '1.0.62-dev.8b', '1.0.62-DEV.8']:
                with self.assertRaisesRegex(ValueError, 'DEPRECATED / LEGACY'):
                    parse(version, None)
            for suffix in ['0', '-1', '1.5', '01', '9+build', '?']:
                with self.assertRaises(ValueError):
                    parse(f'1.0.62-dev.{suffix}', None)
            with self.assertRaises(ValueError):
                parse('1.0.62-dev.9', 10)
            wrong = 'app' if prefix == 'web' else 'web'
            with self.assertRaises(ValueError):
                parse(f'{wrong} v1.0.62-dev.9', None)

    def test_release_metadata_validates_universal_tags(self):
        for script, prefix in [('release_web.py', 'web'), ('release_android.py', 'app')]:
            metadata = runpy.run_path(str(self.root / script))['release_metadata']
            for version in ['1.0.62-dev.9', '1.1.62']:
                for value in [version, 'v' + version, f'{prefix} v{version}', f'{prefix}-v{version}']:
                    with self.subTest(value=value):
                        info = metadata(value)
                        self.assertEqual(info['tag'], f'{prefix}-v{version}')
                        self.assertEqual(info['title'], f'{prefix} v{version}')
                        self.assertEqual(info['prerelease'], '-dev.' in version)
                        self.git('check-ref-format', 'refs/tags/' + info['tag'])
            for value in ['1.0.62-dev.8a', '1.0.62-dev.0', '1.0.62-dev.01',
                          '1.0.62-dev.9 extra', '1.1.62\nBAD=1', '1.1.62..',
                          'wrong v1.1.62', '1.1.62/foo', '1.1.62@{x}']:
                with self.subTest(value=value), self.assertRaises(ValueError):
                    metadata(value)

    def test_ci_metadata_from_display_versions_and_tag_events(self):
        gradle = self.root / 'app/build.gradle.kts'
        gradle.write_text(gradle.read_text(encoding='utf-8').replace('v1.0.50', 'app v1.0.50'), encoding='utf-8')
        for platform in ['app', 'web']:
            for ref_type, ref_name, success in [('branch', 'dev', True),
                    ('tag', f'{platform}-v1.0.50-dev.12', True),
                    ('tag', f'{platform}-v1.0.50-dev.13', False),
                    ('tag', f'{platform} v1.0.50-dev.12', False)]:
                envfile = self.root / 'ci-env.txt'
                envfile.write_text('', encoding='utf-8')
                result = subprocess.run([sys.executable, str(self.root / '.github/release_metadata.py'),
                    '--platform', platform, '--dev-only'], cwd=self.root, capture_output=True, text=True,
                    env={**os.environ, 'GITHUB_REF_TYPE': ref_type, 'GITHUB_REF_NAME': ref_name,
                         'GITHUB_ENV': str(envfile)})
                self.assertEqual(result.returncode, 0 if success else 1, result.stderr)
                output = envfile.read_text(encoding='utf-8')
                if success:
                    self.assertIn(f'RELEASE_TAG={platform}-v1.0.50-dev.12\n', output)
                    self.assertIn(f'RELEASE_TITLE={platform} v1.0.50-dev.12\n', output)
                    self.assertIn('IS_PRERELEASE=true', output)
                else:
                    self.assertEqual(output, '')

    def test_ci_production_metadata_and_dev_workflow_guard(self):
        self.run_script('android', '--promote-to-prod')
        self.git('checkout', 'app-v1.1.62')
        for dev_only in [False, True]:
            result = subprocess.run([sys.executable, str(self.root / '.github/release_metadata.py'),
                '--platform', 'app'] + (['--dev-only'] if dev_only else []),
                cwd=self.root, capture_output=True, text=True,
                env={**os.environ, 'GITHUB_REF_TYPE': 'tag', 'GITHUB_REF_NAME': 'app-v1.1.62', 'GITHUB_ENV': ''})
            self.assertEqual(result.returncode, 1 if dev_only else 0, result.stderr)
            if not dev_only:
                self.assertIn('RELEASE_TAG=app-v1.1.62', result.stdout)
                self.assertIn('IS_PRERELEASE=false', result.stdout)
        self.git('check-ref-format', 'refs/tags/app-v1.1.62')

    def test_push_dev_release_to_local_origin(self):
        remote = self.root / 'origin.git'
        self.git('init', '--bare', str(remote))
        self.git('remote', 'add', 'origin', str(remote))
        # Keep the disposable bare origin out of working-tree cleanliness checks.
        (self.root / '.git/info/exclude').write_text('origin.git/\n', encoding='utf-8')
        self.git('tag', 'unrelated-local-tag')
        for platform, prefix in [('android', 'app'), ('web', 'web')]:
            self.run_script(platform, '--bump-dev', '--push')
            tag = f'{prefix}-v1.0.50-dev.13'
            self.assertEqual(self.git('cat-file', '-t', tag), 'tag')
            self.assertEqual(self.git('log', '-1', '--format=%s'),
                             f'chore({prefix}): bump dev version ({prefix} v1.0.50-dev.13)')
            refs = self.git('ls-remote', 'origin')
            self.assertIn('refs/tags/' + tag, refs)
            self.assertNotIn('unrelated-local-tag', refs)
            self.assertIn(self.git('rev-parse', 'HEAD') + '\trefs/heads/dev', refs)
            self.assertEqual(self.git('status', '--porcelain'), '')

    def test_push_dry_run_and_preflight_do_not_mutate(self):
        for platform in ['android', 'web']:
            before = self.git('rev-parse', 'HEAD')
            self.assertIn('[DRY RUN]', self.run_script(platform, '--bump-dev', '--push', '--dry-run'))
            self.run_script(platform, '--push', success=False)
            self.run_script(platform, '--promote-to-prod', '--push', success=False)
            self.run_script(platform, '--bump-dev', '--push', success=False)  # no origin
            self.assertEqual(self.git('status', '--porcelain'), '')
            self.assertEqual(self.git('rev-parse', 'HEAD'), before)
            self.assertEqual(self.git('tag'), '')

    def test_disagreeing_versions_fail_without_writes(self):
        path = self.root / 'web/package.json'
        path.write_text(path.read_text().replace('1.0.50-dev.12', '1.0.51-dev.12'))
        before = self.git('diff')
        self.assertIn('disagree', self.run_script('web', '--bump-dev', success=False))
        self.assertEqual(before, self.git('diff'))

if __name__ == '__main__':
    unittest.main()
