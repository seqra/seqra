# Bidirectional project input inventory

Checked on 2026-10-12. This report records inputs and settings. It does not report completed project tests. The input audit changed no project model, source checkout, or extension file and ran no Gradle task.

The selected configuration is `../opentaint-test/projects/repos.yaml` at test-system commit `bbdbcc5fd35d976d93348760ea60561e4670f68c`. Its SHA-256 is `5b7afb38179e412bc9465fcbe32faaf85114d28c8177bcdc2563d6b3f80b79de`. No other project configuration is selected. Main is commit `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, checked out at `/tmp/opentaint-bidi-main-f945f92c`. The older `../opentaint-main` checkout is not this baseline. The current analyzer checkout is `/drive-testcomp/opentaint-go-rules/opentaint-w3`; each run manifest records its commit and patch digest.

## Fixed project inputs

All paths below are relative to `/drive-testcomp/opentaint-go-rules`. Each model's source checkout has the head shown. Every source, class, dependency, and toolchain path in the four model files exists.

| Project | Source head | Project model | Input Java | Analyzer heap | Analyzer timeout |
| --- | --- | --- | --- | --- | --- |
| OWASP BenchmarkJava | `79b9bd6177e07991a9c11dc19e457c840e229931` | `owasp-ci-investigation/project/project.yaml` | 17 | 8G | 1000s |
| conductor | `c466eaf70f3ac16b915120dd651e91aae28451c1` | `opentaint-test/opentaint-test-conductor/opentaint-project/project.yaml` | 21 | 8G | 1080s |
| thingsboard | `b31176433e8065ae4cb2a285bcd5e03fce9014f5` | `opentaint-test/opentaint-test-thingsboard/opentaint-project/project.yaml` | 17 | 12G | 1080s |
| Stirling-PDF | `d80e627899daf804f1390a0b75a1da3fd093aa84` | `e2e-root/model-Stirling-PDF/project.yaml` | 17 | 8G | 1080s |

The real-project configuration uses an outer process limit of 1200s. `opentaint-test/scripts/run_analysis.py` subtracts 120s before it passes the analyzer timeout, so the direct task uses 1080s. OWASP's 8G and 1000s come from [ci-analyzer-owasp.yaml](../../.github/workflows/ci-analyzer-owasp.yaml). The local harness also gives OWASP a 1200s outer process limit. This extra outer limit is not a field in the CI workflow.

| Model | Modules | Class files | Dependency paths | `project.yaml` SHA-256 |
| --- | ---: | ---: | ---: | --- |
| OWASP | 1 | 4541 | 131 | `e8fd467e410051c3b0b36d461d953780e5819345fd3b7fddc68a3f8769e09873` |
| conductor | 47 | 1689 | 783 | `3457cf5a1b3c63e0710dfa50a9fbca1c4229f2726ea3276cc409e019515ee35d` |
| thingsboard | 39 | 6196 | 572 | `1246cb43621245e0ef971a51052b54a56abd60725860f44824f00f34d57b58f3` |
| Stirling-PDF | 4 | 1890 | 364 | `b62263d3134af8b0c93426dedcd15a976808e4c8e838950c1a4959022d5d3ef4` |

OWASP's separate source checkout, `owasp-ci-investigation/OWASP`, has the same head as the model sources. Both are clean. The model is cached, not freshly built for this test. Its build log, `owasp-ci-investigation/autobuild.log`, records Maven 3.6.3, Java 17.0.18, and a successful build. The current CI workflow specifies Maven 3.9.11 and checks out the OWASP default branch without a pinned head. Thus this comparison reuses identical compiled input on both sides; it is not a fresh reproduction of that CI build. The CI value `EXPECTED_TRACES=4112` is a trace-count check, not a unique sink-location oracle for the new Simple output.

Conductor and Thingsboard sources are clean. Stirling-PDF has one preexisting test edit in both its source checkout and the copied model sources: `app/proprietary/src/test/java/stirling/software/proprietary/service/UserLicenseSettingsServiceTest.java`. The copied file SHA-256 is `dcebae5cc3ef2a8aa5a13243a28bce6e4da2bd6292f0fd898e5deb027ef7c2e3`. The audit did not change or rebuild it.

## Rules and Conductor extensions

Each analyzer uses its own source-tree `rules/ruleset`, as the test system does for `builtin`. Conductor additionally uses the current paths under `opentaint-test/projects/extensions/conductor`: `rules`, `passthrough`, and `dataflow-src`. Thingsboard and Stirling-PDF have no extra scan flags.

The direct runner consumes compiled dataflow approximations. Conductor already has 54 compiled files in `opentaint-test/opentaint-test-conductor/dataflow-compiled`. Its configured 48 source files match the staged source copy exactly. The compiled directory was captured by `setup_project.sh` after compilation; this audit did not recompile it.

The following directory digests hash a sorted UTF-8 manifest with one `SHA256(file)`, two spaces, relative path, and newline per file.

| Extension | Files | Current configured digest | Staged-copy digest |
| --- | ---: | --- | --- |
| `passthrough` | 35 | `6ccef26f9f3bb8e32d3ace47fcc98c2fb9b6768a8042faae103313b575dcd704` | Same |
| `dataflow-src` | 48 | `303435acc3f5ee09b02d2229eff2cd0215e546d99d2ad1253ec24704d8a136a5` | Same |
| `rules` | 5 | `28fbf2b85ecf2dfe69a44afbea57a9e335de02568b7ff051aedf3702f2d66eb6` | `cd5f80b507aa2fa8bb000cf5ab539b273302ff449b7ee158b2ae7ce131a1d4d1` |

The compiled-file directory digest is `82952196eabfdd47895c51fd4981ba9951940182edb9d1e65c4b2cbfe0119c1b`.

Only `rules/java/lib/spring/conductor-grpc-request-sources.yaml` differs between the current configuration and the staged copy. The current file adds `$*SINK` alternatives beside the plain file/path sinks. Its SHA-256 is `fcb9245ccda496797789ce1370017da0fbfe4932199419d68b94bbfac2cf912c`; the staged file is `8eb89a94401594f24060ca117f7fb8132c3d7104ea551e3dc4f1d4af28bdd0a8`. Both analyzers must use the current configured rules path, not the older staged rules directory. The test-system commit alone does not identify this modified input; the digests do.

## Direct task settings and commands

[benchmark_bidi_projects.py](../../scripts/benchmark_bidi_projects.py) runs the four selected projects sequentially. It generates an init script for each run and calls `:runProjectAnalyzer` directly from that checkout's `core` directory. It does not call the CLI. A fresh output directory is required.

```sh
python3 scripts/benchmark_bidi_projects.py \
  --main /tmp/opentaint-bidi-main-f945f92c \
  --current /drive-testcomp/opentaint-go-rules/opentaint-w3 \
  --test /drive-testcomp/opentaint-go-rules/opentaint-test \
  --output /drive-testcomp/opentaint-go-rules/bidi-real-NEW \
  --projects OWASP conductor thingsboard Stirling-PDF \
  --sides main bidi
```

The generated command is `./gradlew :runProjectAnalyzer --console=plain -I <run>/benchmark.init.gradle -x :opentaint-ir:go:buildGoServer`. The harness excludes the unused Go server build for these JVM inputs. The first setup attempt failed there because the installed `protoc` rejected proto3 optional fields. The init script sets this task's heap and recording options. It preserves the task's system properties: storage batch size 2000 and disabled multi-release JAR loading. The task's default 8G heap alone would not reproduce Thingsboard's 12G setting.

| Setting | Direct task value |
| --- | --- |
| Rules | `--semgrep-rule-set <checkout>/rules/ruleset`; add the current Conductor rules directory |
| Conductor pass models | `--passthrough-approximations <test>/projects/extensions/conductor/passthrough` |
| Conductor dataflow models | `--java-dataflow-approximations <test>/opentaint-test-conductor/dataflow-compiled` |
| Real-project severity | `--semgrep-rule-severity warning --semgrep-rule-severity error`, matching CLI defaults |
| OWASP severity | No filter, matching its workflow |
| Bidi selection | `--ifds-bidirectional` on the current side only; policy limits are 1, 2, 3, ... |
| Legacy/default options | Tree AP; getter model enabled; alias call depth 1; symbolic execution off; no CWE, rule-ID, or entry-point filter; external-method tracking off |
| Code-flow limit | Omitted: the CLI default 0 is also omitted, so both use the runner's unlimited/null limit |
| Output | Debug logs; fingerprints; Semgrep-style rule IDs for the three real projects, standard IDs for OWASP |

CLI-to-runner names differ: `--ruleset` becomes `--semgrep-rule-set`; Java source approximations must first become compiled classes. The CLI's `--experimental` changes flag visibility only. Tool-version metadata, report filenames, and the optional rule-load trace do not select analysis rules. The source URI base defaults to the model source root; the CLI supplies the same root with a trailing separator.

Both analyzer processes in this comparison use Java 17. Their model toolchains remain as listed above. This matches OWASP CI, but differs from the current CLI's analyzer runtime default of Java 21 (`cli/internal/globals/versions.yaml`). Keep this common runtime difference explicit when discussing performance.

Each run saves its command, model hash, analyzer commit and patch hash, resource samples, logs, and SARIF. Profiles use a continuous JFR recording and requested 30s CPU samples one minute after the full analysis starts and near its analysis timeout. A run that ends before a sampling window has no sample for that window. The new timing logs separate engine time from barrier time. Only a joined COMPLETE run enters the barrier; only a complete forward run updates the report. An incomplete first run can therefore have an empty output, while a later incomplete run retains the earlier complete forward report. SARIF existence alone does not establish completion.
