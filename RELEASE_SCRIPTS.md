# GTAR release scripts (091126)

The **OFFICIAL STANDARD** uses strictly positive whole-integer dev iterations, incremented by exactly 1. All previous letter-suffixed versions, including `dev.8a` and `dev.8b`, are **DEPRECATED / LEGACY** and must not be emitted as new releases.

| Platform | Dev display name | Production display name |
| --- | --- | --- |
| Web | `web v1.0.<base>-dev.<integer>` | `web v1.1.<base>` |
| Android | `app v1.0.<base>-dev.<integer>` | `app v1.1.<base>` |

Examples: `web v1.0.62-dev.9`, `app v1.0.62-dev.5`. Promotion uses only the whole integer iteration: `new_base = current_base + iteration`. Letter suffixes have no numeric weight and are never automatically converted into extra fixes. The next cycle resets to `-dev.1`. Historical tags are retained unchanged as legacy records.

Both scripts are standalone Python 3.9+ programs using only the standard library. Git must be installed for tag validation and version mutations. Run them from any directory; paths resolve relative to the scripts.

Default invocation is read-only dev inspection:

```text
python release_web.py
python release_android.py
```

Preview or apply a dev bump (no commit, tag, remote push or deployment):

```text
python release_web.py --bump-dev --dry-run
python release_android.py --bump-dev --dry-run
python release_android.py --bump-dev
```

For a checkout still using the **DEPRECATED / LEGACY** version `1.0.62-DEV.8b`. The specification does not define alphabetic iterations. Supply `--legacy-iteration N` with the intended total numeric iteration when converting it. For example, **only if the intended iteration is 10**:

```text
python release_web.py --bump-dev --legacy-iteration 10 --dry-run
```

That preview produces `web v1.0.62-dev.11`. No default value for N is assumed.

Promotion requires the `dev` branch, a completely clean working tree/index, a configured Git identity, and an unused target tag:

```text
python release_web.py --promote-to-prod --dry-run
python release_android.py --promote-to-prod --dry-run
python release_android.py --promote-to-prod
```

Promotion calculates `new_base = base + iteration`, commits production version files, creates an annotated `web-v1.1.<new_base>` or `app-v1.1.<new_base>` tag, then commits the next dev configuration `1.0.<new_base>-dev.1`. The current branch remains `dev`; the production tag references the preceding production commit. Production promotion does not push, deploy, dispatch CI or build artifacts. Tests/builds should be verified before committing the source to promote. Existing repository hooks still run normally.

Web package and lockfile versions remain valid numeric semver without a display prefix. UI labels gain `web v`, while dev/prod constants remain numeric. Android versionName uses `app v`, with a separate debug suffix. The production snapshot has an empty dev suffix. Each dev bump increments Android versionCode once. Promotion increments it once for production, then once again for the next dev reset so both configurations have increasing codes.

If Git fails partway through promotion, the tool stops and retains completed commits/tags for inspection. It never force-resets history or removes release tags automatically. Review `git status` and `git log` before recovering; rerunning against an existing production tag is rejected.

Git tags always use `web-v<version>` or `app-v<version>` with zero whitespace; human-readable titles use `web v<version>` or `app v<version>`. Both scripts validate generated tags with `git check-ref-format`. Android workflows listen for `app-v1.0.*-dev.*` (dev) and `app-v1.1.*` (production); Web tags do not trigger Android releases. The CI metadata helper validates the checked-out version and rejects mismatched triggering tags before building. Preview workflows run on platform-specific dev tags, not dev branch pushes, avoiding duplicate runs when both refs are pushed. Manual dispatch remains supported. Production publication remains a separate, explicitly controlled operation.

Regression tests (disposable local repositories only):

```text
python -m unittest discover -s tests -p test_release_scripts.py -v
```


Publish a dev bump with an annotated version tag:

```text
python release_android.py --bump-dev --push --dry-run
python release_android.py --bump-dev --push
python release_web.py --bump-dev --push
```

`--push` requires `--bump-dev`, a clean `dev` branch, Git identity, an origin remote,
and an unused local/remote tag. It commits with `chore(app): bump dev version (app v1.0.62-dev.8)`
(or the Web equivalent), annotates the official tag, and atomically pushes only
`dev` and that tag to origin. Unrelated tags are never pushed. An atomic push
rejection leaves the local commit and tag intact; retry the exact command printed
by the script after resolving the remote issue, instead of bumping again.
Without `--push`, existing local-only behavior is unchanged. Dry runs never contact
origin or modify refs. Production promotion remains local-only.
Android dev tags trigger the APK preview release; Web dev tags trigger Web tests,
build, and an artifact upload, without deploying to Cloudflare.

---

## Dedicated QoL Release & Deployment Tools

### 1. Git Push & Release Sync Tool (`push_release.py`)

Atomically pushes dev release tags and the `dev` branch to `origin` without hardcoding versions:

```text
# Preview push commands without mutating Git or contacting remote
python push_release.py web --dry-run
python push_release.py app --dry-run
python push_release.py all --dry-run

# Execute atomic release push to origin
python push_release.py web
python push_release.py app
python push_release.py all
```

- Dynamically resolves dev versions and tags from `release_web.py` and `release_android.py`.
- Validates that the working tree and index are clean before pushing.
- Creates annotated local tags (`web-v1.0.*-dev.*` / `app-v1.0.*-dev.*`) if not already present.
- Executes `git push --atomic origin refs/heads/dev:refs/heads/dev refs/tags/<tag>:refs/tags/<tag>`.

### 2. Automated Production Deployment Tool (`deploy.py`, `deploy_web.py`, `deploy_app.py`)

Handles safe, conflict-free production deployments:

```text
# Web Production Deployment
python deploy.py web --dry-run     # or: python deploy_web.py --dry-run
python deploy.py web               # or: python deploy_web.py

# Android Production Deployment
python deploy.py app --dry-run     # or: python deploy_app.py --dry-run
python deploy.py app               # or: python deploy_app.py
```

**Web Deployment Workflow:**
1. Detects the latest production release tag (e.g. `web-v1.1.83`).
2. Validates working tree safety (aborts on dirty files).
3. Safely switches to `main`.
4. Pulls latest `origin main`.
5. Checks out the tracked `web/` directory from the production tag snapshot (`git checkout <tag> -- web/`) to eliminate merge conflicts.
6. Commits: `chore(release): deploy <tag> to prod`.
7. Pushes `main` and the production tag to `origin`.
8. Automatically returns the developer to their initial branch (`dev`).

**Android Deployment Workflow:**
1. Identifies the latest `app-v1.1.*` production release tag (e.g. `app-v1.1.72`).
2. Validates working tree cleanliness.
3. Pushes the production tag to `origin` (`git push origin refs/tags/<tag>:refs/tags/<tag>`), triggering the GitHub Actions build and release workflow (`release.yml`).

