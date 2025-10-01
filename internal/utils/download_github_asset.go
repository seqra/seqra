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
	"strings"

	"github.com/google/go-github/v72/github"
	"github.com/sirupsen/logrus"
)

func DownloadGithubReleaseAsset(owner, repository, releaseTag, assetName, assetPath, token string) error {
	var client *github.Client
	if token == "" {
		client = github.NewClient(nil)
	} else {
		client = github.NewClient(nil).WithAuthToken(token)
	}

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

			logrus.Debugf("Download asset to: %s", tmpPath)
			tmpFile, err := os.Create(tmpPath)
			if err != nil {
				return err
			}
			defer func() {
				err = tmpFile.Close()
				_ = os.Remove(tmpFile.Name())
			}()

			written, err := io.Copy(tmpFile, rc)
			if err != nil {
				return err
			}

			if written != expectedSize {
				return fmt.Errorf("file size mismatch: expected %d bytes, got %d bytes", expectedSize, written)
			}

			logrus.Debugf("Move asset to: %s", assetPath)
			if err := os.Rename(tmpFile.Name(), assetPath); err != nil {
				return err
			}

			return nil
		}
	}
	return errors.New("can't find artifact in release assets")
}

func DownloadAndUnpackGithubReleaseArchive(owner, repository, releaseTag, assetPath, token string) error {
	var client *github.Client
	if token == "" {
		client = github.NewClient(nil)
	} else {
		client = github.NewClient(nil).WithAuthToken(token)
	}

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
