# Standalone RTB image ad validation

Baseline: upstream 0.3.4, `a19b67c84e0544b47afc63c1f4109dd981c572f8`.

## Problem and scope

Some X timelines serve a standalone `TimelineRtbImageAd` without
`tweet_results`. Existing promotion checks cover several tweet and item metadata
locations, but miss this RTB item shape. The patch recognizes the explicit type
through `content` / `itemContent` / `item` wrappers and records
`rule=TimelineRtbImageAd`. It does not treat `rtb_promoted_metadata` alone as proof
of an ad, and leaves quoted tweets, user references and auxiliary objects alone.

## Fixture and regression coverage

`core/src/test/resources/rtb-image-ad-x12.34.json` retains the module/item structure
from a conversation timeline captured on X `12.34.0-prod.01`. Impression and
thread identifiers and tracking payloads were redacted; the ordinary sibling
tweet is synthetic. Raw account timelines are not included.

The fixture test checks that only the RTB item is removed: the outer module and
the complete ordinary tweet remain unchanged. Disabling promotion filtering or
the entire filter preserves the original input byte for byte. Other regression
cases cover metadata-only entries, a 60-post ordinary feed, quoted content,
auxiliary RTB types, module siblings, cursors and keyword filtering.

The same committed fixture is replayed against unmodified upstream and the
patched core with `TimelineFilter(RuleEngine(FilterSettings(), ""))`:

| Check | Upstream 0.3.4 | Patched core |
| --- | --- | --- |
| Block events | 0 | 1, `TimelineRtbImageAd` |
| Outer timeline entries | 1 | 1 |
| Items inside the module | 2 | 1 |
| Ordinary tweet | Preserved | Preserved |
| RTB item | Retained | Removed |

Logs: [upstream](validation/upstream-0.3.4.log), [patched](validation/rtb-branch.log).
The replay checks the JSON structure and fails if unrelated content changes.

## Executed validation

The [complete core suite](validation/core-tests.log) passed: **107 tests successful, 0 failed**. It was compiled
directly with the project's Kotlin **2.3.20**, JDK **21.0.12.1**, and JVM target 21,
then run with JUnit Platform Console **1.11.4**. This is standalone core validation;
it is not a successful Gradle or Android APK build.

To reproduce on Linux/macOS, install JDK 21 and the Kotlin 2.3.20 compiler
distribution, set `JAVA_HOME` to that JDK, then prepare the pinned dependencies:

```bash
dependency_dir=$(mktemp -d)
curl -fL https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar -o "$dependency_dir/json.jar"
curl -fL https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar -o "$dependency_dir/okhttp.jar"
curl -fL https://repo.maven.apache.org/maven2/com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar -o "$dependency_dir/okio.jar"
curl -fL https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar -o "$dependency_dir/junit-console.jar"
bash scripts/validate-rtb.sh /path/to/kotlinc "$dependency_dir" /tmp/xblocker-rtb-results
```

The script runs all core tests and replays the fixture against both revisions,
writing test results, environment details and replay logs into the output directory.
It requires the baseline commit to be available in the local Git history.

## Remaining checks

Android APK build and lint were not run in this validation environment. Before
merge, run on a machine with JDK 21, Android SDK 37 and Build Tools 36.0.0:

```bash
bash gradlew :core:test :app:assembleDebug :app:lintDebug
```
