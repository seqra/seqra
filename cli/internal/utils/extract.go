package utils

import (
	"archive/tar"
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/internal/output"
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
			output.LogDebug("Skipping global header")
		default:
			output.LogInfof("Skipping unsupported type %c: %s", hdr.Typeflag, hdr.Name)
		}
	}
	return nil
}

// ExtractZipPrefix extracts entries whose names begin with prefix from the zip
// at src into destDir, stripping prefix from each entry's relative path.
// Directory entries are skipped — parent directories are created on demand.
// Returns an error if the zip cannot be opened or any entry fails to write.
func ExtractZipPrefix(src, prefix, destDir string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return fmt.Errorf("failed to open zip: %w", err)
	}
	defer func() { _ = r.Close() }()

	for _, f := range r.File {
		if !strings.HasPrefix(f.Name, prefix) || f.FileInfo().IsDir() {
			continue
		}
		relPath := strings.TrimPrefix(f.Name, prefix)
		if relPath == "" {
			continue
		}
		if err := copyZipEntry(f, filepath.Join(destDir, relPath)); err != nil {
			return err
		}
	}
	return nil
}

func copyZipEntry(f *zip.File, destPath string) (err error) {
	if err := os.MkdirAll(filepath.Dir(destPath), 0755); err != nil {
		return err
	}
	src, err := f.Open()
	if err != nil {
		return err
	}
	defer func() {
		if cerr := src.Close(); cerr != nil && err == nil {
			err = cerr
		}
	}()
	dst, err := os.Create(destPath)
	if err != nil {
		return err
	}
	defer func() {
		if cerr := dst.Close(); cerr != nil && err == nil {
			err = cerr
		}
	}()
	_, err = io.Copy(dst, src)
	return err
}

// ExtractZip extracts the contents of a ZIP file to the specified destination directory.
func ExtractZip(src, dest string) error {
	zr, err := zip.OpenReader(src)
	if err != nil {
		return fmt.Errorf("failed to open zip file: %w", err)
	}
	defer func() { _ = zr.Close() }()

	for _, f := range zr.File {
		path := filepath.Join(dest, f.Name)
		if !strings.HasPrefix(path, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("invalid file path: %s", f.Name)
		}

		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(path, f.FileInfo().Mode()); err != nil {
				return fmt.Errorf("failed to create directory: %w", err)
			}
			continue
		}

		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			return fmt.Errorf("failed to create parent directory: %w", err)
		}

		rc, err := f.Open()
		if err != nil {
			return fmt.Errorf("failed to open file in zip: %w", err)
		}

		outFile, err := os.Create(path)
		if err != nil {
			_ = rc.Close()
			return fmt.Errorf("failed to create output file: %w", err)
		}

		_, err = io.Copy(outFile, rc)
		_ = outFile.Close()
		_ = rc.Close()

		if err != nil {
			return fmt.Errorf("failed to extract file: %w", err)
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

	outFile, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, os.FileMode(hdr.Mode))
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
