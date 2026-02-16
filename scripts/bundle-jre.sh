#!/usr/bin/env bash
set -euo pipefail

# Bundle platform-specific JREs into release archives.
# This script runs post-GoReleaser to inject Temurin JREs into each archive.
#
# Usage: ./scripts/bundle-jre.sh <dist-dir>
#
# Prerequisites: curl, tar, unzip, jq

DIST_DIR="${1:?Usage: $0 <dist-dir>}"
VERSIONS_FILE="internal/globals/versions.yaml"

JAVA_VERSION=$(grep '^java:' "$VERSIONS_FILE" | awk '{print $2}')
echo "Java version: $JAVA_VERSION"

# Platform mappings: <goos>_<goarch> -> <adoptium_os>/<adoptium_arch>
declare -A PLATFORM_MAP=(
    ["linux_amd64"]="linux/x64"
    ["linux_arm64"]="linux/aarch64"
    ["darwin_amd64"]="mac/x64"
    ["darwin_arm64"]="mac/aarch64"
    ["windows_amd64"]="windows/x64"
    ["windows_arm64"]="windows/aarch64"
)

download_jre() {
    local os_name="$1"
    local arch="$2"
    local dest="$3"

    local url="https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/${os_name}/${arch}/jre/hotspot/normal/eclipse"
    echo "  Downloading JRE for ${os_name}/${arch}..."
    curl -fsSL -o "$dest" "$url"
}

inject_jre_tar_gz() {
    local archive="$1"
    local jre_archive="$2"
    local tmp_dir

    tmp_dir=$(mktemp -d)
    trap 'rm -rf "$tmp_dir"' RETURN

    echo "  Extracting archive..."
    tar -xzf "$archive" -C "$tmp_dir"

    echo "  Extracting JRE..."
    local jre_tmp="$tmp_dir/jre_extract"
    mkdir -p "$jre_tmp"
    tar -xzf "$jre_archive" -C "$jre_tmp"

    # Find the JRE root (first directory containing bin/java)
    local jre_root
    jre_root=$(find "$jre_tmp" -name "java" -path "*/bin/java" -type f | head -1 | xargs dirname | xargs dirname)

    if [ -z "$jre_root" ]; then
        echo "  ERROR: Could not find java binary in JRE archive"
        return 1
    fi

    # Move JRE into archive contents
    mv "$jre_root" "$tmp_dir/jre"
    rm -rf "$jre_tmp"

    echo "  Recompressing archive..."
    tar -czf "$archive" -C "$tmp_dir" .
}

inject_jre_zip() {
    local archive="$1"
    local jre_archive="$2"
    local tmp_dir

    tmp_dir=$(mktemp -d)
    trap 'rm -rf "$tmp_dir"' RETURN

    echo "  Extracting archive..."
    unzip -q "$archive" -d "$tmp_dir"

    echo "  Extracting JRE..."
    local jre_tmp="$tmp_dir/jre_extract"
    mkdir -p "$jre_tmp"
    unzip -q "$jre_archive" -d "$jre_tmp"

    # Find the JRE root (first directory containing bin/java.exe)
    local jre_root
    jre_root=$(find "$jre_tmp" -name "java.exe" -path "*/bin/java.exe" -type f | head -1 | xargs dirname | xargs dirname)

    if [ -z "$jre_root" ]; then
        echo "  ERROR: Could not find java.exe binary in JRE archive"
        return 1
    fi

    # Move JRE into archive contents
    mv "$jre_root" "$tmp_dir/jre"
    rm -rf "$jre_tmp"

    echo "  Recompressing archive..."
    (cd "$tmp_dir" && zip -qr "$archive" .)
}

for platform in "${!PLATFORM_MAP[@]}"; do
    IFS='/' read -r adoptium_os adoptium_arch <<< "${PLATFORM_MAP[$platform]}"

    echo "Processing platform: $platform ($adoptium_os/$adoptium_arch)"

    # Determine archive format
    if [[ "$platform" == windows_* ]]; then
        archive_ext="zip"
    else
        archive_ext="tar.gz"
    fi

    archive_file="$DIST_DIR/seqra_${platform}.${archive_ext}"
    if [ ! -f "$archive_file" ]; then
        echo "  Archive not found: $archive_file, skipping"
        continue
    fi

    # Download JRE
    jre_file=$(mktemp)
    download_jre "$adoptium_os" "$adoptium_arch" "$jre_file"

    # Inject JRE into archive
    if [ "$archive_ext" = "zip" ]; then
        inject_jre_zip "$archive_file" "$jre_file"
    else
        inject_jre_tar_gz "$archive_file" "$jre_file"
    fi

    rm -f "$jre_file"
    echo "  Done: $archive_file"
done

echo "JRE bundling complete."
