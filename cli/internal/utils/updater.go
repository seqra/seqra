package utils

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/seqra/opentaint/internal/output"
)

// InstallMethod represents how opentaint was installed.
type InstallMethod int

const (
	InstallMethodUnknown InstallMethod = iota
	InstallMethodHomebrew
	InstallMethodBinary
)

// DetectInstallMethod detects how opentaint was installed based on the executable path.
func DetectInstallMethod() (InstallMethod, string) {
	exe, err := os.Executable()
	if err != nil {
		return InstallMethodUnknown, ""
	}
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		return InstallMethodUnknown, ""
	}

	return classifyExePath(exe), exe
}

func UpdateHint(latestVersion string) string {
	method, _ := DetectInstallMethod()
	switch method {
	case InstallMethodHomebrew:
		return fmt.Sprintf("A new version is available: v%s. Run \"brew upgrade --cask opentaint\" to update.", latestVersion)
	default:
		return fmt.Sprintf("A new version is available: v%s. Run \"opentaint update\" to update.", latestVersion)
	}
}

func classifyExePath(exePath string) InstallMethod {
	lowerPath := strings.ToLower(exePath)
	if strings.Contains(lowerPath, "/cellar/") || strings.Contains(lowerPath, "/caskroom/") || strings.Contains(lowerPath, "/homebrew/") {
		return InstallMethodHomebrew
	}
	return InstallMethodBinary
}

// GetLatestRelease fetches the latest release version from GitHub.
func GetLatestRelease(owner, repo, token string) (string, string, error) {
	client := newGithubClient(token)

	ctx := context.Background()
	release, _, err := client.Repositories.GetLatestRelease(ctx, owner, repo)
	if err != nil {
		return "", "", fmt.Errorf("failed to fetch latest release: %w", err)
	}

	tag := release.GetTagName()
	// Strip "v" prefix if present
	ver := strings.TrimPrefix(tag, "v")

	return ver, tag, nil
}

// DownloadReleaseArchive downloads the appropriate release archive for the current platform.
func DownloadReleaseArchive(owner, repo, tag, token, destDir string, skipVerify bool) (string, error) {
	client := newGithubClient(token)

	archiveName := getArchiveName()
	ctx := context.Background()

	release, _, err := client.Repositories.GetReleaseByTag(ctx, owner, repo, tag)
	if err != nil {
		return "", fmt.Errorf("failed to get release %s: %w", tag, err)
	}

	for _, asset := range release.Assets {
		if asset.GetName() == archiveName {
			rc, _, err := client.Repositories.DownloadReleaseAsset(ctx, owner, repo, asset.GetID(), client.Client())
			if err != nil {
				return "", fmt.Errorf("failed to download asset: %w", err)
			}
			defer func() { _ = rc.Close() }()

			destPath := filepath.Join(destDir, archiveName)
			f, err := os.Create(destPath)
			if err != nil {
				return "", err
			}
			defer func() { _ = f.Close() }()

			if _, err := f.ReadFrom(rc); err != nil {
				return "", fmt.Errorf("failed to write archive: %w", err)
			}

			if err := f.Close(); err != nil {
				return "", err
			}

			if !skipVerify {
				if err := verifyAssetChecksum(client, owner, repo, release, asset, destPath); err != nil {
					_ = os.Remove(destPath)
					return "", err
				}
			}

			output.LogDebugf("Downloaded release archive to %s", destPath)
			return destPath, nil
		}
	}

	return "", fmt.Errorf("archive %s not found in release %s", archiveName, tag)
}

func getArchiveName() string {
	ext := "tar.gz"
	if runtime.GOOS == "windows" {
		ext = "zip"
	}
	return fmt.Sprintf("opentaint_%s_%s.%s", runtime.GOOS, runtime.GOARCH, ext)
}

// SelfUpdate performs an in-place update of the opentaint binary and bundled artifacts.
func SelfUpdate(archivePath, installDir string) error {
	// Create temp dir next to install target to avoid cross-device rename errors
	// when replacing binaries/directories (e.g. /tmp -> $HOME/.opentaint).
	tmpDir, err := os.MkdirTemp(installDir, ".opentaint-update-*")
	if err != nil {
		return fmt.Errorf("failed to create temp dir: %w", err)
	}
	defer func() { _ = os.RemoveAll(tmpDir) }()

	// Extract archive
	if strings.HasSuffix(archivePath, ".zip") {
		if err := ExtractZip(archivePath, tmpDir); err != nil {
			return fmt.Errorf("failed to extract archive: %w", err)
		}
	} else {
		if err := extractTarGzFile(archivePath, tmpDir); err != nil {
			return fmt.Errorf("failed to extract archive: %w", err)
		}
	}

	// Determine binary name
	binaryName := "opentaint"
	if runtime.GOOS == "windows" {
		binaryName = "opentaint.exe"
	}

	newBinary := filepath.Join(tmpDir, binaryName)
	if _, err := os.Stat(newBinary); err != nil {
		return fmt.Errorf("binary not found in archive: %w", err)
	}

	currentBinary := filepath.Join(installDir, binaryName)

	if runtime.GOOS == "windows" {
		// Windows: rename current to .old, then place new
		oldBinary := currentBinary + ".old"
		_ = os.Remove(oldBinary)
		if err := os.Rename(currentBinary, oldBinary); err != nil {
			return fmt.Errorf("failed to rename current binary (try running as administrator): %w", err)
		}
		if err := os.Rename(newBinary, currentBinary); err != nil {
			// Try to restore
			_ = os.Rename(oldBinary, currentBinary)
			return fmt.Errorf("failed to install new binary: %w", err)
		}
		// Schedule old binary deletion (best effort)
		_ = os.Remove(oldBinary)
	} else {
		// Unix: atomic rename
		if err := os.Rename(newBinary, currentBinary); err != nil {
			return fmt.Errorf("failed to install new binary (try running with sudo): %w", err)
		}
		if err := os.Chmod(currentBinary, 0o755); err != nil {
			output.LogInfof("Failed to set binary permissions: %v", err)
		}
	}

	// Update lib/ and jre/ directories.
	// Preserve the installation style: if bundled artifacts exist next to the
	// binary, update them in place. Otherwise, place into the install tier
	// (~/.opentaint/install/) so bare-binary installations stay bare.
	bundledLib := filepath.Join(installDir, "lib")
	bundledJRE := filepath.Join(installDir, "jre")

	_, libBundled := os.Stat(bundledLib)
	_, jreBundled := os.Stat(bundledJRE)

	if err := updateArtifactDir(tmpDir, "lib", libBundled == nil, installDir); err != nil {
		output.LogInfof("Failed to update lib directory: %v", err)
	}
	if err := updateArtifactDir(tmpDir, "jre", jreBundled == nil, installDir); err != nil {
		output.LogInfof("Failed to update jre directory: %v", err)
	}

	// If anything was placed in the install tier, update the version marker.
	if libBundled != nil || jreBundled != nil {
		if err := WriteInstallVersionMarker(); err != nil {
			output.LogInfof("Failed to write install version marker: %v", err)
		}
	}

	return nil
}

// updateArtifactDir moves an extracted artifact directory (lib or jre) to the
// appropriate location. When bundled is true, it replaces the directory next to
// the binary (installDir). Otherwise it places it in the install tier.
func updateArtifactDir(tmpDir, name string, bundled bool, installDir string) error {
	src := filepath.Join(tmpDir, name)
	if _, err := os.Stat(src); err != nil {
		return nil // archive doesn't contain this directory
	}

	var dest string
	if bundled {
		dest = filepath.Join(installDir, name)
	} else {
		dir := GetInstallDir()
		if dir == "" {
			return nil
		}
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return fmt.Errorf("failed to create install dir: %w", err)
		}
		dest = filepath.Join(dir, name)
	}

	_ = os.RemoveAll(dest)
	return os.Rename(src, dest)
}

// extractTarGzFile extracts a .tar.gz file to a destination directory.
func extractTarGzFile(archivePath, destDir string) error {
	f, err := os.Open(archivePath)
	if err != nil {
		return err
	}
	defer func() { _ = f.Close() }()

	return extractTarGz(f, destDir)
}
