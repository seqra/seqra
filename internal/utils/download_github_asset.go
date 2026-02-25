package utils

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/go-github/v74/github"
	"github.com/seqra/seqra/v2/internal/utils/ui"
	"github.com/sirupsen/logrus"
)

func copyWithProgress(dst io.Writer, src io.Reader, total int64, label string) (int64, error) {
	if !ui.IsSpinnerTerminal() || total <= 0 {
		return io.Copy(dst, src)
	}

	const (
		barWidth    = 28
		updateEvery = 100 * time.Millisecond
		bytesPerKiB = 1024
		bytesPerMiB = 1024 * bytesPerKiB
		bytesPerGiB = 1024 * bytesPerMiB
	)

	formatBytes := func(n int64) string {
		switch {
		case n >= bytesPerGiB:
			return fmt.Sprintf("%.1f GiB", float64(n)/float64(bytesPerGiB))
		case n >= bytesPerMiB:
			return fmt.Sprintf("%.1f MiB", float64(n)/float64(bytesPerMiB))
		case n >= bytesPerKiB:
			return fmt.Sprintf("%.1f KiB", float64(n)/float64(bytesPerKiB))
		default:
			return fmt.Sprintf("%d B", n)
		}
	}

	printProgress := func(written int64, force bool, lastPrinted *time.Time) {
		now := time.Now()
		if !force && !lastPrinted.IsZero() && now.Sub(*lastPrinted) < updateEvery {
			return
		}
		*lastPrinted = now

		if written > total {
			written = total
		}
		percent := float64(written) / float64(total)
		filled := int(percent * barWidth)
		if filled > barWidth {
			filled = barWidth
		}
		bar := strings.Repeat("=", filled) + strings.Repeat("-", barWidth-filled)
		fmt.Printf("\r[%s] %s %3.0f%% (%s/%s)", bar, label, percent*100, formatBytes(written), formatBytes(total))
	}

	buf := make([]byte, 32*1024)
	var written int64
	var lastPrinted time.Time

	for {
		nr, readErr := src.Read(buf)
		if nr > 0 {
			nw, writeErr := dst.Write(buf[:nr])
			if nw > 0 {
				written += int64(nw)
				printProgress(written, false, &lastPrinted)
			}
			if writeErr != nil {
				fmt.Print("\n")
				return written, writeErr
			}
			if nw < nr {
				fmt.Print("\n")
				return written, io.ErrShortWrite
			}
		}

		if readErr != nil {
			if errors.Is(readErr, io.EOF) {
				printProgress(written, true, &lastPrinted)
				fmt.Print("\n")
				return written, nil
			}
			fmt.Print("\n")
			return written, readErr
		}
	}
}

func newGithubClient(token string) *github.Client {
	if token == "" {
		return github.NewClient(nil)
	}
	return github.NewClient(nil).WithAuthToken(token)
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

			written, err := copyWithProgress(tmpFile, rc, expectedSize, "Downloading "+assetName)
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

func DownloadAndUnpackGithubReleaseArchive(owner, repository, releaseTag, assetPath, token string) error {
	client := newGithubClient(token)

	ctx := context.Background()
	release, _, err := client.Repositories.GetReleaseByTag(ctx, owner, repository, releaseTag)
	if err != nil {
		return err
	}

	archiveURL := release.TarballURL

	resp, err := client.Client().Get(*archiveURL)
	if err != nil {
		return err
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	tmpPath := assetPath + ".temp"

	out, err := os.Create(tmpPath)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, resp.Body); err != nil {
		_ = out.Close()
		return err
	}
	defer func() {
		_ = out.Close()
	}()

	f, err := os.Open(tmpPath)
	if err != nil {
		return err
	}
	defer func() {
		_ = f.Close()
		_ = os.Remove(tmpPath)
	}()

	gz1, err := gzip.NewReader(f)
	if err != nil {
		return err
	}
	tr1 := tar.NewReader(gz1)

	var basePath string
	for {
		hdr, err := tr1.Next()
		if err == io.EOF {
			return fmt.Errorf("empty tarball")
		}
		if err != nil {
			return fmt.Errorf("reading tar header: %w", err)
		}

		// Ignore extended headers like "pax_global_header" etc.
		switch hdr.Typeflag {
		case tar.TypeXGlobalHeader, tar.TypeXHeader, tar.TypeGNULongName, tar.TypeGNULongLink, tar.TypeGNUSparse:
			continue
		}

		// Normalize and extract first path segment (GitHub: owner-repo-<sha>/...)
		name := path.Clean(strings.TrimPrefix(hdr.Name, "./"))
		if name == "" || name == "." {
			continue
		}
		basePath = name
		break
	}
	err = gz1.Close()
	if err != nil {
		return err
	}

	// Rewind to start for actual extraction
	if _, err := f.Seek(0, 0); err != nil {
		return err
	}
	gz2, err := gzip.NewReader(f)
	if err != nil {
		return err
	}

	defer func() {
		_ = gz2.Close()
	}()

	tr2 := tar.NewReader(gz2)

	if err := ExtractTar(tr2, basePath, assetPath, true); err != nil {
		return err
	}

	return nil
}

func DownloadAndUnpackGithubReleaseAsset(owner, repository, releaseTag, assetName, destPath, token string, skipVerify bool) error {
	client := newGithubClient(token)

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

			written, err := copyWithProgress(tmpFile, rc, expectedSize, "Downloading "+assetName)
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
