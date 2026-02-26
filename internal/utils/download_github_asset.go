package utils

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/go-github/v74/github"
	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
	"github.com/sirupsen/logrus"
)

func newGithubClient(token string) *github.Client {
	if token == "" {
		return github.NewClient(nil)
	}
	return github.NewClient(nil).WithAuthToken(token)
}

func newProgressPrinter() *output.Printer {
	return output.NewConfigured(globals.Config.Log.Color, globals.Config.Quiet)
}

// verifyAssetChecksum checks the SHA256 of filePath using the asset's native digest (preferred)
// or checksums.txt as fallback. Returns nil if verified or if checksums are unavailable.
// Returns error only on checksum mismatch.
func verifyAssetChecksum(client *github.Client, owner, repo string, release *github.RepositoryRelease, asset *github.ReleaseAsset, filePath string) error {
	assetName := asset.GetName()

	// Try native GitHub asset digest first (available since June 2025).
	if expected, ok := ParseAssetDigest(asset.GetDigest()); ok {
		if err := VerifyFileChecksum(filePath, expected); err != nil {
			return fmt.Errorf("integrity check failed for %s: %w", assetName, err)
		}
		logrus.Debugf("SHA256 verified (asset digest): %s", assetName)
		return nil
	}

	// Fall back to checksums.txt for older releases.
	checksums, err := FetchReleaseChecksums(client, owner, repo, release)
	if err != nil {
		logrus.Warnf("Could not fetch checksums for %s/%s: %v", owner, repo, err)
		return nil
	}
	if checksums == nil {
		logrus.Debugf("No checksums.txt in release, skipping verification")
		return nil
	}
	expected, ok := checksums[assetName]
	if !ok {
		logrus.Warnf("No checksum entry for %s in checksums.txt", assetName)
		return nil
	}
	if err := VerifyFileChecksum(filePath, expected); err != nil {
		return fmt.Errorf("integrity check failed for %s: %w", assetName, err)
	}
	logrus.Debugf("SHA256 verified: %s", assetName)
	return nil
}

func DownloadGithubReleaseAsset(owner, repository, releaseTag, assetName, assetPath, token string, skipVerify bool) error {
	client := newGithubClient(token)
	printer := newProgressPrinter()

	ctx := context.Background()
	release, _, err := client.Repositories.GetReleaseByTag(ctx, owner, repository, releaseTag)
	if err != nil {
		return err
	}

	assets := release.Assets

	for assetId := range assets {
		if *assets[assetId].Name == assetName {
			asset := assets[assetId]
			expectedSize := int64(asset.GetSize())
			rc, _, err := client.Repositories.DownloadReleaseAsset(ctx, owner, repository, asset.GetID(), client.Client())
			if err != nil {
				return err
			}
			defer func() {
				_ = rc.Close()
			}()

			tmpPath := assetPath + ".temp"

			if err := os.MkdirAll(filepath.Dir(assetPath), 0o755); err != nil {
				return err
			}

			logrus.Debugf("Download asset to: %s", tmpPath)
			tmpFile, err := os.Create(tmpPath)
			if err != nil {
				return err
			}
			defer func() {
				_ = tmpFile.Close()
			}()

			written, err := printer.CopyWithProgress(tmpFile, rc, expectedSize, "Downloading "+assetName)
			if err != nil {
				return err
			}

			if written != expectedSize {
				return fmt.Errorf("file size mismatch: expected %d bytes, got %d bytes", expectedSize, written)
			}

			if err := tmpFile.Close(); err != nil {
				return err
			}

			if !skipVerify {
				if err := verifyAssetChecksum(client, owner, repository, release, asset, tmpPath); err != nil {
					_ = os.Remove(tmpPath)
					return err
				}
			}

			logrus.Debugf("Move asset to: %s", assetPath)
			if err := os.Rename(tmpFile.Name(), assetPath); err != nil {
				_ = os.Remove(tmpFile.Name())
				return err
			}

			return nil
		}
	}
	return errors.New("failed to find artifact in release assets")
}

func DownloadAndUnpackGithubReleaseAsset(owner, repository, releaseTag, assetName, destPath, token string, skipVerify bool) error {
	client := newGithubClient(token)
	printer := newProgressPrinter()

	ctx := context.Background()
	release, _, err := client.Repositories.GetReleaseByTag(ctx, owner, repository, releaseTag)
	if err != nil {
		return err
	}

	assets := release.Assets

	for assetId := range assets {
		if *assets[assetId].Name == assetName {
			asset := assets[assetId]
			expectedSize := int64(asset.GetSize())
			rc, _, err := client.Repositories.DownloadReleaseAsset(ctx, owner, repository, asset.GetID(), client.Client())
			if err != nil {
				return err
			}
			defer func() {
				_ = rc.Close()
			}()

			tmpPath := destPath + ".temp"

			if err := os.MkdirAll(filepath.Dir(destPath), 0o755); err != nil {
				return err
			}

			logrus.Debugf("Download asset to: %s", tmpPath)
			tmpFile, err := os.Create(tmpPath)
			if err != nil {
				return err
			}
			defer func() {
				_ = tmpFile.Close()
				_ = os.Remove(tmpPath)
			}()

			written, err := printer.CopyWithProgress(tmpFile, rc, expectedSize, "Downloading "+assetName)
			if err != nil {
				return err
			}

			if written != expectedSize {
				return fmt.Errorf("file size mismatch: expected %d bytes, got %d bytes", expectedSize, written)
			}

			if err := tmpFile.Close(); err != nil {
				return err
			}

			if !skipVerify {
				if err := verifyAssetChecksum(client, owner, repository, release, asset, tmpPath); err != nil {
					return err
				}
			}

			return extractAsset(tmpPath, assetName, destPath)
		}
	}
	return errors.New("failed to find artifact in release assets")
}

func extractAsset(tmpPath, assetName, destPath string) error {
	logrus.Debugf("Extract asset %s to: %s", assetName, destPath)

	if err := os.MkdirAll(destPath, 0755); err != nil {
		return err
	}

	f, err := os.Open(tmpPath)
	if err != nil {
		return err
	}
	defer func() {
		_ = f.Close()
	}()

	lowerName := strings.ToLower(assetName)
	if strings.HasSuffix(lowerName, ".tar.gz") || strings.HasSuffix(lowerName, ".tgz") {
		return extractTarGz(f, destPath)
	}
	return fmt.Errorf("unsupported archive format: %s", assetName)
}

func extractTarGz(f *os.File, destPath string) error {
	gz, err := gzip.NewReader(f)
	if err != nil {
		return err
	}
	defer func() {
		_ = gz.Close()
	}()

	tr := tar.NewReader(gz)
	return ExtractTar(tr, "", destPath, true)
}
