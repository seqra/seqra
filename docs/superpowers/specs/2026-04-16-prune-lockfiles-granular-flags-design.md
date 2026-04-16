# Prune: Lockfiles & Granular Flags

## Problem

The `opentaint prune` command lacks:
1. **Concurrency safety** — no file locking; concurrent prune/compile can corrupt state. The existing `HasStagingDir` is a best-effort TOCTOU heuristic.
2. **Granular control** — only `--all` vs default. Users cannot selectively prune logs, models, artifacts, etc.
3. **Log isolation** — logs live inside `cache/<slug-hash>/`, making it impossible to prune them independently of models.

## Directory Structure

Flat layout for JARs/rules/JDK/JRE is **unchanged**. Only the cache area is restructured — logs move to their own top-level directory:

```
~/.opentaint/
├── analyzer_<version>.jar          # unchanged
├── autobuilder_<version>.jar       # unchanged
├── rules_<version>/                # unchanged
├── jdk/                            # unchanged
├── jre/                            # unchanged
├── install/                        # unchanged
│   ├── lib/
│   ├── jre/
│   └── .versions
├── cache/                          # models only (no more logs here)
│   └── <slug-hash>/
│       ├── project-model/
│       ├── .staging-*/
│       └── .compile.lock           # per-project compile lock
├── logs/                           # mirrors cache/<slug-hash> structure
│   └── <slug-hash>/
│       └── <timestamp>.log
└── .prune.lock                     # global prune lock
```

### Changes from current layout
- `logs/` becomes a top-level sibling of `cache/`, using the same `<slug-hash>` subdirs.
- Lock files live where they protect: `.compile.lock` per project, `.prune.lock` globally.
- No migration needed for old logs in `cache/` dirs — they get cleaned up naturally by prune.

## Granular Prune Flags

| Flag | Targets | Default prune | `--all` |
|------|---------|:---:|:---:|
| `--artifacts` | Stale analyzer + autobuilder JARs | yes | yes |
| `--rules` | Stale rules directories | yes | yes |
| `--jdk` | Old JDK/JRE versions | yes | yes |
| `--models` | `cache/<slug-hash>/project-model/` + `.staging-*` | yes | yes |
| `--logs` | `logs/<slug-hash>/` | no | yes |
| `--install` | `install/lib/` + `install/jre/` | no | yes |

### Behavior

- **No flags** = default prune (artifacts, rules, jdk, models). Same as today minus `--all` stuff.
- **`--all`** = everything including logs and install-tier.
- **Specific flags** (e.g., `--models --logs`) = only those categories, nothing else.
- Specific flags and `--all` are **mutually exclusive** — error if combined.

Examples:
```bash
opentaint prune                    # default: artifacts + rules + jdk + models
opentaint prune --logs             # only logs
opentaint prune --models --logs    # models and logs
opentaint prune --all              # everything
opentaint prune --all --logs       # error: mutually exclusive
```

## Locking

### Dependency

`github.com/gofrs/flock` — cross-platform file locking (flock on Unix, LockFileEx on Windows). Auto-releases on process crash.

### Lock scopes

| Lock | File | Acquired by | Purpose |
|------|------|-------------|---------|
| Global prune | `~/.opentaint/.prune.lock` | `prune` command | Prevent concurrent prunes |
| Per-project compile | `cache/<slug-hash>/.compile.lock` | `scan`/`compile` | Protect active compilations |

### Lock file content (diagnostics)

```
pid=12345
command=compile
project=/Users/dev/my-app
```

PID and metadata written after acquiring the lock. Used for skip reporting only — not part of the locking mechanism itself.

### Prune flow

1. Try exclusive non-blocking lock on `.prune.lock`.
   - If held: fail fast with "Another prune is already running".
2. Scan for stale artifacts across all requested categories.
3. For each project in `cache/`, try non-blocking lock on `.compile.lock`.
   - Acquired: include project in prune candidates, release (re-acquire at delete time).
   - Locked: skip project, add to "skipped" report.
4. Display results + skipped projects.
5. If not dry-run and user confirms: delete artifacts (re-acquiring per-project locks before removing).
6. Release `.prune.lock`.

### Compile flow

1. Acquire exclusive lock on `cache/<slug-hash>/.compile.lock`.
   - If held: fail with "Compilation already in progress for this project".
2. Create staging dir, compile, promote to cache.
3. Write logs to `logs/<slug-hash>/`.
4. Release lock.

This replaces the `HasStagingDir` heuristic entirely.

### Skip reporting

When prune skips locked projects:
```
Skipped (compilation in progress):
  ~/.opentaint/cache/my-app-a1b2c3d4 (locked by PID 12345)

Stale Artifacts:
  ~/.opentaint/analyzer_0.9.0.jar (artifacts) - 250.5 MB
  Total: 1 item, 250.5 MB
```

## Migration & Backward Compatibility

### Logs

- `scan`/`compile` commands updated to write logs to `logs/<slug-hash>/`.
- No automatic migration of old logs from `cache/<slug-hash>/`.
- Old logs inside cache dirs are treated as part of the model cache for pruning — cleaned up by `--models` or `--all`.

### HasStagingDir removal

- `HasStagingDir()` removed entirely; replaced by flock-based compile lock.
- `.staging-*` directories still used for the staging workflow itself.

### No breaking changes

- Flat artifact layout unchanged.
- JDK/JRE storage unchanged.
- Install-tier unchanged.
- Project model cache paths unchanged.
- `opentaint prune` with no flags behaves identically to today's default.

## File Changes

### New files
- `cli/internal/utils/lock.go` — lock acquisition/release helpers, PID content, skip reporting.
- `cli/internal/utils/lock_test.go` — lock tests.

### Modified files
- `cli/internal/utils/prune.go` — granular category scanning, lock-aware flow.
- `cli/internal/utils/prune_test.go` — tests for new flags and locking.
- `cli/cmd/prune.go` — new flags, mutual exclusivity validation, skip reporting output.
- `cli/internal/utils/model_cache.go` — remove `HasStagingDir`, add log path helpers.
- `cli/cmd/scan.go` — compile lock instead of `HasStagingDir`, write logs to new location.
- `cli/cmd/compile.go` — same lock changes as scan.
- `go.mod` / `go.sum` — add `github.com/gofrs/flock`.

### Removed
- `HasStagingDir()` function.
