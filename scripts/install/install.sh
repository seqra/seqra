#!/usr/bin/env bash
set -euo pipefail

# Seqra installer for Linux and macOS
# Usage: curl -fsSL https://raw.githubusercontent.com/seqra/seqra/main/scripts/install/install.sh | bash

REPO="seqra/seqra"
INSTALL_DIR="${SEQRA_INSTALL_DIR:-}"
DOWNLOAD_BASE_URL="${SEQRA_DOWNLOAD_BASE_URL:-https://github.com/${REPO}/releases/latest/download}"

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

    echo "${os}_${arch}"
}

verify_checksum() {
    local archive_path="$1"
    local archive_name="$2"
    local checksums_url="${DOWNLOAD_BASE_URL}/checksums.txt"

    echo "Verifying checksum..."
    if ! curl -fsSL -o "$tmp_dir/checksums.txt" "$checksums_url" 2>/dev/null; then
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
        echo "/usr/local/bin"
    else
        echo "$HOME/.seqra/install"
    fi
}

main() {
    local platform archive_name url install_dir

    echo "Detecting platform..."
    platform="$(detect_platform)"
    echo "Platform: $platform"

    archive_name="seqra-full_${platform}.tar.gz"
    url="${DOWNLOAD_BASE_URL}/${archive_name}"

    install_dir="$(get_install_dir)"
    echo "Install directory: $install_dir"

    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "$tmp_dir"' EXIT

    echo "Downloading ${archive_name}..."
    curl -fsSL -o "$tmp_dir/$archive_name" "$url"

    verify_checksum "$tmp_dir/$archive_name" "$archive_name"

    echo "Extracting..."
    tar -xzf "$tmp_dir/$archive_name" -C "$tmp_dir"

    echo "Installing to $install_dir..."
    mkdir -p "$install_dir"
    cp "$tmp_dir/seqra" "$install_dir/seqra"
    chmod +x "$install_dir/seqra"

    # Install bundled lib and jre if present (next to the binary)
    if [ -d "$tmp_dir/lib" ]; then
        mkdir -p "$install_dir/lib"
        cp -r "$tmp_dir/lib/"* "$install_dir/lib/"
    fi

    if [ -d "$tmp_dir/jre" ]; then
        mkdir -p "$install_dir/jre"
        cp -r "$tmp_dir/jre/"* "$install_dir/jre/"
    fi

    echo ""
    echo "seqra installed successfully!"
    echo ""

    # Check if install_dir is in PATH
    case ":$PATH:" in
        *":$install_dir:"*)
            echo "Run 'seqra --version' to verify the installation."
            ;;
        *)
            echo "Add the following to your shell profile to use seqra:"
            echo ""
            echo "  export PATH=\"$install_dir:\$PATH\""
            echo ""
            echo "Then restart your shell or run the export command above."
            ;;
    esac
}

main "$@"
