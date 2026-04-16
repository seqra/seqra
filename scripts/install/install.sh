#!/usr/bin/env bash
set -euo pipefail

# OpenTaint installer for Linux and macOS
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh | bash
#   curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh | bash -s -- 1.2.3

REPO="${OPENTAINT_REPOSITORY:-seqra/opentaint}"
INSTALL_DIR="${OPENTAINT_INSTALL_DIR:-}"

DOWNLOADER=""

# Populates VERSION_PATH_SEGMENT and VERSION_TAG from the raw version argument.
# Accepts:
#   (empty)  -> latest
#   latest
#   X.Y.Z
#   vX.Y.Z
#   X.Y.Z-suffix
#   vX.Y.Z-suffix
# Exits 2 on invalid input.
validate_version() {
    local raw="${1:-latest}"

    if [ "$raw" = "latest" ] || [ -z "$raw" ]; then
        VERSION_PATH_SEGMENT="latest/download"
        VERSION_TAG="latest"
        return
    fi

    if [[ "$raw" =~ ^v?[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9._-]+)?$ ]]; then
        local normalized="${raw#v}"
        VERSION_PATH_SEGMENT="download/v${normalized}"
        VERSION_TAG="v${normalized}"
        return
    fi

    echo "Error: invalid version '$raw'." >&2
    echo "Expected 'latest' or 'X.Y.Z' (optionally prefixed with 'v')." >&2
    exit 2
}

# Prints the resolved path of an existing opentaint binary if it appears to
# belong to a Homebrew installation (mirrors cli/internal/utils/updater.go
# classification). Prints nothing otherwise.
detect_homebrew_install() {
    if ! command -v opentaint >/dev/null 2>&1; then
        return 0
    fi
    local path
    path="$(command -v opentaint)"
    # Resolve symlinks so we can compare against the real location.
    if command -v readlink >/dev/null 2>&1; then
        # readlink -f is Linux/GNU; macOS realpath works but differs. Fall back.
        if resolved="$(readlink -f "$path" 2>/dev/null)"; then
            path="$resolved"
        fi
    fi
    case "$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')" in
        */cellar/*|*/caskroom/*|*/homebrew/*)
            echo "$path"
            ;;
    esac
}

pick_downloader() {
    if command -v curl >/dev/null 2>&1; then
        DOWNLOADER="curl"
        return
    fi
    if command -v wget >/dev/null 2>&1; then
        DOWNLOADER="wget"
        return
    fi
    echo "Error: curl or wget is required but neither is installed." >&2
    echo "Install curl or wget and re-run the installer." >&2
    exit 1
}

download() {
    local url="$1"
    local output="$2"

    case "$DOWNLOADER" in
        curl)
            curl -fsSL -o "$output" "$url"
            ;;
        wget)
            wget -q -O "$output" "$url"
            ;;
        *)
            echo "Error: no downloader configured." >&2
            exit 1
            ;;
    esac
}

detect_platform() {
    local os arch

    os="$(uname -s)"
    case "$os" in
        Linux)  os="linux" ;;
        Darwin) os="darwin" ;;
        *)
            echo "Error: Unsupported operating system: $os" >&2
            exit 1
            ;;
    esac

    arch="$(uname -m)"
    case "$arch" in
        x86_64|amd64)  arch="amd64" ;;
        arm64|aarch64) arch="arm64" ;;
        *)
            echo "Error: Unsupported architecture: $arch" >&2
            exit 1
            ;;
    esac

    # Rosetta-2: a shell running as amd64 under Rosetta on Apple Silicon
    # should download the native arm64 archive instead.
    if [ "$os" = "darwin" ] && [ "$arch" = "amd64" ]; then
        if [ "$(sysctl -n sysctl.proc_translated 2>/dev/null)" = "1" ]; then
            arch="arm64"
        fi
    fi

    echo "${os}_${arch}"
}

verify_checksum() {
    local archive_path="$1"
    local archive_name="$2"
    local checksums_url="${DOWNLOAD_BASE_URL}/checksums.txt"

    echo "Verifying checksum..."
    if ! download "$checksums_url" "$tmp_dir/checksums.txt" 2>/dev/null; then
        echo "Warning: Could not download checksums.txt, skipping verification." >&2
        return 0
    fi

    local expected
    expected="$(grep "  ${archive_name}$" "$tmp_dir/checksums.txt" | awk '{print $1}')"
    if [ -z "$expected" ]; then
        echo "Warning: No checksum found for ${archive_name}, skipping verification." >&2
        return 0
    fi

    local actual
    if command -v sha256sum >/dev/null 2>&1; then
        actual="$(sha256sum "$archive_path" | awk '{print $1}')"
    elif command -v shasum >/dev/null 2>&1; then
        actual="$(shasum -a 256 "$archive_path" | awk '{print $1}')"
    else
        echo "Warning: No SHA256 tool found, skipping verification." >&2
        return 0
    fi

    if [ "$expected" != "$actual" ]; then
        echo "Error: Checksum verification failed!" >&2
        echo "  Expected: $expected" >&2
        echo "  Actual:   $actual" >&2
        exit 1
    fi
    echo "Checksum verified."
}

get_install_dir() {
    if [ -n "$INSTALL_DIR" ]; then
        echo "$INSTALL_DIR"
        return
    fi

    if [ "$(id -u)" = "0" ]; then
        echo "/usr/local/lib/opentaint"
    else
        echo "$HOME/.opentaint/install"
    fi
}

main() {
    local platform archive_name url install_dir bin_dir

    validate_version "${1:-}"
    pick_downloader

    local existing_brew
    existing_brew="$(detect_homebrew_install)"
    if [ -n "$existing_brew" ] && [ "${OPENTAINT_FORCE:-0}" != "1" ]; then
        echo "Error: opentaint is already installed via Homebrew at $existing_brew" >&2
        echo "Run 'brew upgrade --cask opentaint' to update, or set" >&2
        echo "OPENTAINT_FORCE=1 to install side-by-side anyway." >&2
        exit 3
    fi

    DOWNLOAD_BASE_URL="${OPENTAINT_DOWNLOAD_BASE_URL:-https://github.com/${REPO}/releases/${VERSION_PATH_SEGMENT}}"

    echo "Version: $VERSION_TAG"
    echo "Detecting platform..."
    platform="$(detect_platform)"
    echo "Platform: $platform"

    archive_name="opentaint-full_${platform}.tar.gz"
    url="${DOWNLOAD_BASE_URL}/${archive_name}"

    install_dir="$(get_install_dir)"
    echo "Install directory: $install_dir"

    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "$tmp_dir"' EXIT

    echo "Downloading ${archive_name}..."
    download "$url" "$tmp_dir/$archive_name"

    verify_checksum "$tmp_dir/$archive_name" "$archive_name"

    echo "Extracting..."
    tar -xzf "$tmp_dir/$archive_name" -C "$tmp_dir"

    echo "Installing to $install_dir..."
    mkdir -p "$install_dir"
    cp "$tmp_dir/opentaint" "$install_dir/opentaint"
    chmod +x "$install_dir/opentaint"

    # Install bundled lib and jre if present (next to the binary)
    if [ -d "$tmp_dir/lib" ]; then
        mkdir -p "$install_dir/lib"
        cp -r "$tmp_dir/lib/"* "$install_dir/lib/"
    fi

    if [ -d "$tmp_dir/jre" ]; then
        mkdir -p "$install_dir/jre"
        cp -r "$tmp_dir/jre/"* "$install_dir/jre/"
    fi

    # For root installs, symlink into /usr/local/bin so the binary is in PATH
    # while keeping lib/jre next to the actual binary for bundled path resolution
    bin_dir="$install_dir"
    if [ "$(id -u)" = "0" ] && [ -z "$INSTALL_DIR" ]; then
        ln -sf "$install_dir/opentaint" /usr/local/bin/opentaint
        bin_dir="/usr/local/bin"
    fi

    echo ""
    echo "opentaint installed successfully!"
    echo ""
    echo "OPENTAINT_BINARY_PATH=$bin_dir/opentaint"
    echo ""

    # Check if bin_dir is in PATH
    case ":$PATH:" in
        *":$bin_dir:"*)
            echo "Run 'opentaint --version' to verify the installation."
            ;;
        *)
            echo "Add the following to your shell profile to use opentaint:"
            echo ""
            echo "  export PATH=\"$bin_dir:\$PATH\""
            echo ""
            echo "Then restart your shell or run the export command above."
            ;;
    esac
}

main "$@"
