package utils

import (
	"archive/tar"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/sirupsen/logrus"
)

// ExtractTar extracts the contents of a tar reader to the specified destination directory.
// basePath is the base path within the tar archive to start extraction from.
// isSourceDir indicates whether the source path in the container is a directory.
// destPath is the destination path on the host filesystem.
func ExtractTar(tr *tar.Reader, basePath, destPath string, isSourceDir bool) error {
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("error reading tar archive: %w", err)
		}

		relPath := strings.TrimPrefix(hdr.Name, basePath)
		relPath = strings.TrimPrefix(relPath, string(filepath.Separator))

		target := destPath
		if isSourceDir {
			target = filepath.Join(destPath, relPath)
		}

		switch hdr.Typeflag {
		case tar.TypeDir:
			if err := handleDirectory(target, hdr); err != nil {
				return err
			}
		case tar.TypeReg:
			if err := handleRegularFile(tr, target, hdr); err != nil {
				return err
			}
		case tar.TypeSymlink:
			if err := handleSymlink(target, hdr); err != nil {
				return err
			}
		case tar.TypeXGlobalHeader:
			logrus.Trace("Skipping global header")
		default:
			logrus.Warnf("Skipping unsupported type %c: %s", hdr.Typeflag, hdr.Name)
		}
	}
	return nil
}

func handleDirectory(target string, hdr *tar.Header) error {
	if err := os.MkdirAll(target, os.FileMode(hdr.Mode)); err != nil {
		return fmt.Errorf("failed to create directory %s: %w", target, err)
	}
	return nil
}

func handleRegularFile(tr *tar.Reader, target string, hdr *tar.Header) error {
	if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
		return fmt.Errorf("failed to create parent dir for file %s: %w", target, err)
	}

	outFile, err := os.Create(target)
	if err != nil {
		return fmt.Errorf("failed to create file %s: %w", target, err)
	}

	defer func() {
		if cerr := outFile.Close(); cerr != nil && err == nil {
			err = fmt.Errorf("failed to close file %s: %w", target, cerr)
		}
	}()

	if _, err := io.Copy(outFile, tr); err != nil {
		return fmt.Errorf("failed to copy contents to %s: %w", target, err)
	}

	return nil
}

func handleSymlink(target string, hdr *tar.Header) error {
	if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
		return fmt.Errorf("failed to create parent dir for symlink %s: %w", target, err)
	}
	if err := os.Symlink(hdr.Linkname, target); err != nil {
		return fmt.Errorf("failed to create symlink from %s to %s: %w", target, hdr.Linkname, err)
	}
	return nil
}
