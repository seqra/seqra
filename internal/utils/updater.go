package utils

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/google/go-github/v72/github"
	"github.com/sirupsen/logrus"
)

// InstallMethod represents how seqra was installed.
type InstallMethod int

const (
	InstallMethodUnknown InstallMethod = iota
	InstallMethodHomebrew
	InstallMethodScoop
	InstallMethodGoInstall
	InstallMethodBinary
)

// DetectInstallMethod detects how seqra was installed based on the executable path.
func DetectInstallMethod() (InstallMethod, string) {
	exe, err := os.Executable()
	if err != nil {
		return InstallMethodUnknown, ""
	}
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		return InstallMethodUnknown, ""
	}

	goPath := os.Getenv("GOPATH")
	if goPath == "" {
		home, _ := os.UserHomeDir()
		goPath = filepath.Join(home, "go")
	}

	return classifyExePath(exe, goPath), exe
}

func classifyExePath(exePath, goPath string) InstallMethod {
	lowerPath := strings.ToLower(exePath)
	if strings.Contains(lowerPath, "/cellar/") || strings.Contains(lowerPath, "/homebrew/") {
		return InstallMethodHomebrew
	}
	if strings.Contains(lowerPath, "/scoop/apps/") || strings.Contains(lowerPath, "\\scoop\\apps\\") {
		return InstallMethodScoop
	}
	goBin := filepath.Join(goPath, "bin")
	if strings.HasPrefix(exePath, goBin) {
		return InstallMethodGoInstall
	}
	return InstallMethodBinary
}

// GetLatestRelease fetches the latest release version from GitHub.
func GetLatestRelease(owner, repo, token string) (string, string, error) {
	var client *github.Client
	if token == "" {
		client = github.NewClient(nil)
	} else {
		client = github.NewClient(nil).WithAuthToken(token)
	}

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
func DownloadReleaseArchive(owner, repo, tag, token, destDir string) (string, error) {
	var client *github.Client
	if token == "" {
		client = github.NewClient(nil)
	} else {
		client = github.NewClient(nil).WithAuthToken(token)
	}

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
			logrus.Debugf("Downloaded release archive to %s", destPath)
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
	return fmt.Sprintf("seqra_%s_%s.%s", runtime.GOOS, runtime.GOARCH, ext)
}

// SelfUpdate performs an in-place update of the seqra binary and bundled artifacts.
func SelfUpdate(archivePath, installDir string) error {
	tmpDir, err := os.MkdirTemp("", "seqra-update-*")
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
	binaryName := "seqra"
	if runtime.GOOS == "windows" {
		binaryName = "seqra.exe"
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
			logrus.Warnf("Failed to set binary permissions: %v", err)
		}
	}

	// Update lib/ directory
	newLib := filepath.Join(tmpDir, "lib")
	if _, err := os.Stat(newLib); err == nil {
		currentLib := filepath.Join(installDir, "lib")
		_ = os.RemoveAll(currentLib)
		if err := os.Rename(newLib, currentLib); err != nil {
			logrus.Warnf("Failed to update lib directory: %v", err)
		}
	}

	// Update jre/ directory
	newJRE := filepath.Join(tmpDir, "jre")
	if _, err := os.Stat(newJRE); err == nil {
		currentJRE := filepath.Join(installDir, "jre")
		_ = os.RemoveAll(currentJRE)
		if err := os.Rename(newJRE, currentJRE); err != nil {
			logrus.Warnf("Failed to update jre directory: %v", err)
		}
	}

	return nil
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
