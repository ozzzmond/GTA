import re
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent

# Auto-detect Android Gradle location
ANDROID_GRADLE = ROOT_DIR / "android" / "app" / "build.gradle"
if not ANDROID_GRADLE.exists():
    ANDROID_GRADLE = ROOT_DIR / "app" / "build.gradle"
if not ANDROID_GRADLE.exists():
    ANDROID_GRADLE = ROOT_DIR / "app" / "build.gradle.kts"

def run_cmd(cmd, cwd=ROOT_DIR):
    print(f"\n[RUNNING] {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=cwd, shell=True)
    if res.returncode != 0:
        print(f"[ERROR] Command failed: {' '.join(cmd)}")
        sys.exit(res.returncode)

def calculate_next_android_versions():
    if not ANDROID_GRADLE.exists():
        print(f"[ERROR] Cannot find Android build.gradle at {ANDROID_GRADLE}")
        sys.exit(1)

    content = ANDROID_GRADLE.read_text(encoding="utf-8")
    
    # Check current versionName (e.g. "1.0.62-dev.2" or "1.1.62")
    match = re.search(r'versionName\s+(=?\s*)["\'](1\.[01]\.(\d+)(?:-dev\.(\d+))?)["\']', content)
    if not match:
        print("[ERROR] Could not parse current versionName from build.gradle")
        sys.exit(1)

    current_full = match.group(2)
    base_num = int(match.group(3))
    dev_iterations = int(match.group(4)) if match.group(4) else 1

    new_patch = base_num + dev_iterations
    new_prod_ver = f"1.1.{new_patch}"
    new_dev_ver = f"1.0.{new_patch}-dev.1"

    return new_prod_ver, new_dev_ver, base_num, dev_iterations

def update_android_files(new_prod_ver: str):
    print(f"\n--> Updating {ANDROID_GRADLE.name}...")
    content = ANDROID_GRADLE.read_text(encoding="utf-8")

    # 1. Bump versionCode (+1)
    def bump_code(m):
        old_code = int(m.group(3))
        new_code = old_code + 1
        print(f"  [OK] Bumped versionCode: {old_code} -> {new_code}")
        return f"{m.group(1)}{new_code}"

    content = re.sub(r'(versionCode\s+(=?\s*))(\d+)', bump_code, content)

    # 2. Update versionName to new prod version
    content = re.sub(
        r'(versionName\s+(=?\s*))["\'][^"\']+["\']',
        rf'\g<1>"{new_prod_ver}"',
        content
    )
    ANDROID_GRADLE.write_text(content, encoding="utf-8")
    print(f"  [OK] Updated versionName to '{new_prod_ver}'")

def main():
    new_prod_ver, new_dev_ver, base_num, iterations = calculate_next_android_versions()
    tag_name = f"android-v{new_prod_ver}"

    print("==================================================")
    print("    GTAR ANDROID PROD RELEASE AUTOMATION          ")
    print("==================================================")
    print(f"Current Dev Base: 1.0.{base_num}")
    print(f"Accumulated Dev Fixes/Features: {iterations}")
    print(f"Calculated New PROD: v{new_prod_ver}")
    print(f"Next Initial DEV:    v{new_dev_ver}")
    print("==================================================")

    confirm = input(f"\nProceed with Android Prod Release v{new_prod_ver}? (y/N): ").strip().lower()
    if confirm != 'y':
        print("\n[CANCELLED] Release aborted.")
        sys.exit(0)

    # 1. Apply Gradle updates
    update_android_files(new_prod_ver)

    # 2. Check build kung available si gradlew.bat
    gradlew = ROOT_DIR / "gradlew.bat"
    if not gradlew.exists() and (ROOT_DIR / "android" / "gradlew.bat").exists():
        gradlew = ROOT_DIR / "android" / "gradlew.bat"

    if gradlew.exists():
        check_build = input("\nRun gradlew assembleRelease check? (y/N): ").strip().lower()
        if check_build == 'y':
            run_cmd([str(gradlew), "assembleRelease"], cwd=gradlew.parent)

    # 3. Git Commit & Tag
    print("\n--> Staging Git Changes...")
    run_cmd(["git", "add", str(ANDROID_GRADLE)])
    
    commit_msg = f"chore(android): release prod v{new_prod_ver} (promoted from {iterations} dev fixes)"
    run_cmd(["git", "commit", "-m", commit_msg])
    run_cmd(["git", "tag", "-a", tag_name, "-m", f"Android Release v{new_prod_ver}"])

    print(f"\n✅ Android release complete and tagged as {tag_name}!")
    
    push = input(f"\nPush commit and tag to origin right now? (y/N): ").strip().lower()
    if push == 'y':
        run_cmd(["git", "push", "origin", "main"])
        run_cmd(["git", "push", "origin", tag_name])
        print(f"\n🚀 Push completed!")
    else:
        print(f"\n[INFO] Skipped remote push. Run manually: git push origin main {tag_name}")

if __name__ == "__main__":
    main()