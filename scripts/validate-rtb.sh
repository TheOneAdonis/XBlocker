#!/usr/bin/env bash
set -euo pipefail

# Standalone core validation when the Android/Gradle toolchain is unavailable.
# See RTB_VALIDATION.md for pinned dependency versions and download commands.
if (( $# < 3 || $# > 4 )); then
    echo "Usage: $0 <kotlin-home> <dependency-dir> <output-dir> [baseline-ref]" >&2
    exit 2
fi
compiler_home=$(cd "$1" && pwd)
dependency_dir=$(cd "$2" && pwd)
mkdir -p "$3"
output_dir=$(cd "$3" && pwd)
baseline_ref=${4:-a19b67c84e0544b47afc63c1f4109dd981c572f8}
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
java_cmd=${JAVA_HOME:+$JAVA_HOME/bin/}java
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

classpath="$dependency_dir/json.jar:$dependency_dir/okhttp.jar:$dependency_dir/okio.jar:$dependency_dir/junit-console.jar:$compiler_home/lib/kotlin-test.jar:$compiler_home/lib/kotlin-test-junit5.jar"
for jar in json okhttp okio junit-console; do
    if [[ ! -f "$dependency_dir/$jar.jar" ]]; then
        echo "Missing dependency: $dependency_dir/$jar.jar" >&2
        exit 2
    fi
done

{
    "$java_cmd" -version
    bash "$compiler_home/bin/kotlinc" -version
    git -C "$repo_root" rev-parse HEAD
    git -C "$repo_root" rev-parse "$baseline_ref^{commit}"
} > "$output_dir/environment.log" 2>&1

bash "$compiler_home/bin/kotlinc" \
    "$repo_root/core/src/main/kotlin" "$repo_root/core/src/test/kotlin" \
    -jvm-target 21 -cp "$classpath" -d "$work_dir/tests"
(cd "$repo_root/core" && "$java_cmd" -jar "$dependency_dir/junit-console.jar" execute \
    --class-path "$work_dir/tests:$repo_root/core/src/test/resources:$classpath:$compiler_home/lib/kotlin-stdlib.jar" \
    --scan-classpath --disable-banner --disable-ansi-colors --details=summary) \
    | tee "$output_dir/core-tests.log"

mkdir -p "$work_dir/baseline"
git -C "$repo_root" archive "$baseline_ref" core/src/main/kotlin \
    | tar -x -C "$work_dir/baseline"
fixture="$repo_root/core/src/test/resources/rtb-image-ad-x12.34.json"
for mode in baseline patched; do
    source_dir="$repo_root/core/src/main/kotlin"
    log_name=rtb-branch.log
    if [[ "$mode" == baseline ]]; then
        source_dir="$work_dir/baseline/core/src/main/kotlin"
        log_name=upstream-0.3.4.log
    fi
    bash "$compiler_home/bin/kotlinc" "$source_dir" "$repo_root/scripts/ReplayRtbFixture.kt" \
        -jvm-target 21 -cp "$dependency_dir/json.jar" -include-runtime -d "$work_dir/$mode.jar"
    "$java_cmd" -cp "$work_dir/$mode.jar:$dependency_dir/json.jar" ReplayRtbFixtureKt "$fixture" "$mode" \
        | tee "$output_dir/$log_name"
done
