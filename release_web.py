import re
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent
WEB_DIR = ROOT_DIR / "web"
GTAR_TS = WEB_DIR / "src" / "types" / "gtar.ts"
WEB_PKG = WEB_DIR / "package.json"

def run_cmd(cmd, cwd=ROOT_DIR):
    print(f"\n[RUNNING] {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=cwd, shell=True)
    if res.returncode != 0:
        print(f"[ERROR] Command failed: {' '.join(cmd)}")
        sys.exit(res.returncode)

def calculate_next_versions():
    if not GTAR_TS.exists():
        print(f"[ERROR] Cannot find {GTAR_TS}")
        sys.exit(1)

    content = GTAR_TS.read_text(encoding="utf-8")
    dev_match = re.search(r"GTAR_DEV_VERSION\s*=\s*['\"]1\.0\.(\d+)-dev\.(\d+)['\"];", content)
    if not dev_match:
        print("[ERROR] Could not parse GTAR_DEV_VERSION (expected '1.0.<num>-dev.<num>')")
        sys.exit(1)

    base_num = int(dev_match.group(1))
    dev_iterations = int(dev_match.group(2))

    new_patch = base_num + dev_iterations
    new_prod_ver = f"1.1.{new_patch}"
    new_dev_ver = f"1.0.{new_patch}-dev.1"

    return new_prod_ver, new_dev_ver, base_num, dev_iterations

def update_web_files(new_prod_ver: str, new_dev_ver: str):
    print(f"\n--> Updating {GTAR_TS.name} & {WEB_PKG.name}...")
    
    # 1. Update gtar.ts
    content = GTAR_TS.read_text(encoding="utf-8")
    content = re.sub(
        r"export const GTAR_APP_VERSION = ['\"].*?['\"];",
        f"export const GTAR_APP_VERSION = '{new_prod_ver}';",
        content
    )
    content = re.sub(
        r"export const GTAR_DEV_VERSION = ['\"].*?['\"];",
        f"export const GTAR_DEV_VERSION = '{new_dev_ver}';",
        content
    )
    GTAR_TS.write_text(content, encoding="utf-8")
    print(f"  [OK] GTAR_APP_VERSION = '{new_prod_ver}'")
    print(f"  [OK] GTAR_DEV_VERSION = '{new_dev_ver}'")

    # 2. Update package.json
    if WEB_PKG.exists():
        pkg_content = WEB_PKG.read_text(encoding="utf-8")
        pkg_content = re.sub(
            r'("version"\s*:\s*")[^"]+(")',
            rf'\g<1>{new_prod_ver}\g<2>',
            pkg_content,
            count=1
        )
        WEB_PKG.write_text(pkg_content, encoding="utf-8")
        print(f"  [OK] package.json version = '{new_prod_ver}'")

def main():
    new_prod_ver, new_dev_ver, base_num, iterations = calculate_next_versions()
    tag_name = f"web-v{new_prod_ver}"

    print("==================================================")
    print("      GTAR WEB PROD RELEASE AUTOMATION            ")
    print("==================================================")
    print(f"Current Dev Base: 1.0.{base_num}")
    print(f"Accumulated Dev Fixes/Features: {iterations}")
    print(f"Calculated New PROD: v{new_prod_ver}")
    print(f"Next Initial DEV:    v{new_dev_ver}")
    print("==================================================")

    confirm = input(f"\nProceed with Prod Release v{new_prod_ver}? (y/N): ").strip().lower()
    if confirm != 'y':
        print("\n[CANCELLED] Release aborted.")
        sys.exit(0)

    # 1. Apply version bumps
    update_web_files(new_prod_ver, new_dev_ver)

    # 2. Test Build Web Bundle
    print("\n--> Verifying Web Production Build (npm run build)...")
    run_cmd(["npm.cmd", "run", "build"], cwd=WEB_DIR)

    # 3. Git Commit & Tag
    print("\n--> Staging Git Changes...")
    run_cmd(["git", "add", "web/"])
    
    commit_msg = f"chore(web): release prod v{new_prod_ver} (promoted from {iterations} dev fixes)"
    run_cmd(["git", "commit", "-m", commit_msg])
    run_cmd(["git", "tag", "-a", tag_name, "-m", f"Web Release v{new_prod_ver}"])

    print(f"\n✅ Release v{new_prod_ver} complete and tagged as {tag_name}!")
    
    push = input(f"\nPush commit and tag to origin right now? (y/N): ").strip().lower()
    if push == 'y':
        run_cmd(["git", "push", "origin", "main"])
        run_cmd(["git", "push", "origin", tag_name])
        print(f"\n🚀 Push completed!")
    else:
        print(f"\n[INFO] Skipped remote push. Run manually: git push origin main {tag_name}")

if __name__ == "__main__":
    main()