package java

import (
	"archive/tar"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
	"github.com/seqra/seqra/v2/internal/utils"
)

var errJavaFound = errors.New("java-binary-found")

// Adoptium image type identifiers.
type AdoptiumImageType string

const (
	AdoptiumImageJDK AdoptiumImageType = "jdk"
	AdoptiumImageJRE AdoptiumImageType = "jre"
)

// AdoptiumOS enumerates supported operating systems for API downloads.
type AdoptiumOS string

const (
	AdoptiumOSLinux   AdoptiumOS = "linux"
	AdoptiumOSWindows AdoptiumOS = "windows"
	AdoptiumOSMac     AdoptiumOS = "mac"
)

// AdoptiumArch enumerates supported architectures for API downloads.
type AdoptiumArch string

const (
	AdoptiumArchX64     AdoptiumArch = "x64"
	AdoptiumArchAARCH64 AdoptiumArch = "aarch64"
)

// EnsureLocalRuntimeAt downloads and installs Temurin runtime directly into targetDir
// (flat layout: targetDir/bin/java) and returns the path to the java binary.
func EnsureLocalRuntimeAt(requiredJavaVersion int, imageType AdoptiumImageType,
	targetDir string, goOs, goArch string, skipVerify bool) (string, error) {
	return ensureLocalRuntimeAt(requiredJavaVersion, imageType, targetDir, goOs, goArch, skipVerify, true)
}

func ensureLocalRuntimeAt(requiredJavaVersion int, imageType AdoptiumImageType,
	targetDir string, goOs, goArch string, skipVerify bool, showProgress bool) (string, error) {

	adoptiumOS, adoptiumArch, err := MapPlatformToAdoptium(goOs, goArch)
	if err != nil {
		return "", err
	}

	javaBinary, err := mapOSToJavaBinary(goOs)
	if err != nil {
		return "", err
	}

	javaPath := filepath.Join(targetDir, "bin", javaBinary)
	if fileExists(javaPath) {
		output.LogDebugf("Using installed Java: %s", javaPath)
		return javaPath, nil
	}

	url := fmt.Sprintf(
		"https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/%s/hotspot/normal/eclipse",
		requiredJavaVersion, adoptiumOS, adoptiumArch, imageType,
	)

	artefactTar := string(imageType)

	return withTmpDir(targetDir+"-tmp", func(tmpDir string) (string, error) {
		tmpArchive := filepath.Join(tmpDir, artefactTar)
		if err := ensureDownloaded(url, tmpArchive, showProgress); err != nil {
			return "", err
		}

		if !skipVerify {
			expected, err := FetchAdoptiumChecksum(requiredJavaVersion, adoptiumOS, adoptiumArch, imageType)
			if err != nil {
				output.LogInfof("Could not fetch Adoptium checksum: %v", err)
			} else if expected != "" {
				if err := utils.VerifyFileChecksum(tmpArchive, expected); err != nil {
					return "", fmt.Errorf("JRE integrity check failed: %w", err)
				}
				output.LogDebugf("SHA256 verified: Temurin Java %d", requiredJavaVersion)
			}
		}

		if err := unpack(tmpArchive, tmpDir); err != nil {
			return "", err
		}
		javaPath, err := finalizeJavaInstall(tmpDir, targetDir, javaBinary, requiredJavaVersion)
		if err != nil {
			return "", err
		}
		output.LogDebugf("Java installed at: %s", javaPath)
		return javaPath, nil
	})
}

// MapPlatformToAdoptium converts Go OS/arch to Adoptium naming.
func MapPlatformToAdoptium(osName, arch string) (AdoptiumOS, AdoptiumArch, error) {
	var adoptiumOS AdoptiumOS
	switch osName {
	case "darwin":
		adoptiumOS = AdoptiumOSMac
	case "linux":
		adoptiumOS = AdoptiumOSLinux
	case "windows":
		adoptiumOS = AdoptiumOSWindows
	default:
		return "", "", fmt.Errorf("unsupported OS for java runtime bootstrap: %s", osName)
	}

	var adoptiumArch AdoptiumArch
	switch arch {
	case "arm64":
		adoptiumArch = AdoptiumArchAARCH64
	case "amd64":
		adoptiumArch = AdoptiumArchX64
	default:
		return "", "", fmt.Errorf("unsupported arch for java runtime bootstrap: %s", arch)
	}

	return adoptiumOS, adoptiumArch, nil
}

func mapOSToJavaBinary(osName string) (string, error) {
	var javaBinary string
	switch osName {
	case "darwin":
		javaBinary = "java"
	case "linux":
		javaBinary = "java"
	case "windows":
		javaBinary = "java.exe"
	default:
		return "", fmt.Errorf("unsupported OS for java runtime bootstrap: %s", osName)
	}

	return javaBinary, nil
}

// withTmpDir manages a temporary directory lifecycle and cleanup.
func withTmpDir(path string, fn func(string) (string, error)) (result string, err error) {
	if err := os.RemoveAll(path); err != nil {
		return "", fmt.Errorf("failed to clean temporary directory: %w", err)
	}
	defer func() {
		if cleanupErr := os.RemoveAll(path); cleanupErr != nil {
			output.LogInfof("failed to clean temporary directory %s: %v", path, cleanupErr)
		}
	}()

	// Create directory if it doesn't exist
	if err := os.MkdirAll(path, 0o755); err != nil {
		return "", fmt.Errorf("failed to create temporary directory: %w", err)
	}

	return fn(path)
}

// ensureDownloaded downloads a file if it doesn't already exist.
func ensureDownloaded(url, dest string, showProgress bool) error {
	output.LogDebugf("trying to tap into %s...", dest)
	if fileExists(dest) {
		output.LogDebugf("Reusing downloaded Java archive: %s", dest)
		return nil
	}
	output.LogDebugf("Downloading Temurin Java from %s", url)
	err := downloadFile(url, dest, showProgress)
	if err == nil {
		output.LogDebugf("Successfully downloaded Temurin Java to %s", dest)
	}
	return err
}

func unpack(archivePath, targetDir string) error {
	output.LogDebugf("Unpacking Java archive %s into %s", archivePath, targetDir)
	f, err := os.Open(archivePath)
	if err != nil {
		return err
	}
	defer func() { _ = f.Close() }()

	buff := make([]byte, 512)
	if _, err = f.Read(buff); err != nil {
		return err
	}
	fileType := http.DetectContentType(buff)

	switch fileType {
	case "application/x-gzip":
		file, err := os.Open(archivePath)
		if err != nil {
			return err
		}
		defer func() { _ = file.Close() }()
		gz, err := gzip.NewReader(file)
		if err != nil {
			return err
		}
		defer func() { _ = gz.Close() }()

		tr := tar.NewReader(gz)
		return utils.ExtractTar(tr, "", targetDir, true)
	case "application/zip":
		return utils.ExtractZip(archivePath, targetDir)
	default:
		return fmt.Errorf("unsupported archive format: %s", fileType)
	}
}

func finalizeJavaInstall(tmpDir, jreRoot, javaBinary string, javaVersion int) (string, error) {
	javaBinaryPath, err := findJavaBinary(tmpDir, javaBinary)
	if err != nil {
		return "", err
	}

	javaHome, javaBinaryName, err := validateJavaBinaryLocation(javaBinaryPath, tmpDir)
	if err != nil {
		return "", err
	}

	extractedJavaPath := filepath.Join(javaHome, "bin", javaBinaryName)
	if err := validateJavaStructure(javaHome, extractedJavaPath, javaVersion); err != nil {
		return "", err
	}

	if err := relocateJava(javaHome, jreRoot); err != nil {
		return "", err
	}

	finalJavaPath := filepath.Join(jreRoot, "bin", javaBinaryName)
	if err := validateJavaStructure(jreRoot, finalJavaPath, javaVersion); err != nil {
		return "", err
	}

	return finalJavaPath, nil
}

func validateJavaBinaryLocation(javaBinaryPath, tmpDir string) (javaHome, javaBinaryName string, err error) {
	binDir := filepath.Dir(javaBinaryPath)
	if filepath.Base(binDir) != "bin" {
		return "", "", fmt.Errorf("java binary located outside bin directory: %s", javaBinaryPath)
	}

	javaHome = filepath.Dir(binDir)
	javaBinaryName = filepath.Base(javaBinaryPath)

	rel, err := filepath.Rel(tmpDir, javaHome)
	if err != nil {
		return "", "", fmt.Errorf("failed to evaluate extracted Java location: %w", err)
	}
	if strings.HasPrefix(rel, "..") {
		return "", "", fmt.Errorf("extracted Java escaped temporary directory: %s", javaHome)
	}

	return javaHome, javaBinaryName, nil
}

func relocateJava(javaHome, jreRoot string) error {
	if err := os.RemoveAll(jreRoot); err != nil {
		return fmt.Errorf("failed to clean existing Java directory %s: %w", jreRoot, err)
	}
	if err := os.MkdirAll(filepath.Dir(jreRoot), 0o755); err != nil {
		return fmt.Errorf("failed to create Java parent directory %s: %w", filepath.Dir(jreRoot), err)
	}
	return os.Rename(javaHome, jreRoot)
}

func findJavaBinary(root, javaBinary string) (string, error) {
	var javaPath string

	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.Type().IsRegular() && filepath.Base(path) == javaBinary && filepath.Base(filepath.Dir(path)) == "bin" {
			javaPath = path
			return errJavaFound
		}
		return nil
	})
	if err != nil && !errors.Is(err, errJavaFound) {
		return "", err
	}
	if javaPath == "" {
		return "", fmt.Errorf("java executable not found in %s", root)
	}
	return javaPath, nil
}

func validateJavaStructure(root, javaExecutable string, javaVersion int) error {
	st, err := os.Stat(javaExecutable)
	if err != nil {
		return fmt.Errorf("java executable not found: %w", err)
	}
	if st.IsDir() {
		return fmt.Errorf("java executable path points to directory: %s", javaExecutable)
	}

	libDir := filepath.Join(root, "lib")
	libInfo, err := os.Stat(libDir)
	if err != nil {
		return fmt.Errorf("java lib directory missing: %w", err)
	}
	if !libInfo.IsDir() {
		return fmt.Errorf("java lib path is not a directory: %s", libDir)
	}

	// Java 9+ uses the modular lib/modules file (JPMS/Jigsaw)
	// Java 8 and earlier use lib/dt.jar (runtime classes)
	if javaVersion >= 9 {
		modulesPath := filepath.Join(libDir, "modules")
		if _, err := os.Stat(modulesPath); err != nil {
			return fmt.Errorf("java modules file missing: %w", err)
		}
	} else {
		rtJarPath := filepath.Join(libDir, "dt.jar")
		if _, err := os.Stat(rtJarPath); err != nil {
			return fmt.Errorf("java dt.jar file missing: %w", err)
		}
	}

	return nil
}

func fileExists(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir()
}

func newProgressPrinter() *output.Printer {
	return output.NewConfigured(globals.Config.Log.Color, globals.Config.Quiet)
}

// downloadFile downloads url to dest path
func downloadFile(url, dest string, showProgress bool) error {
	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return fmt.Errorf("download failed: %s", resp.Status)
	}

	f, err := os.Create(dest)
	if err != nil {
		return err
	}
	defer func() { _ = f.Close() }()

	label := "Downloading " + filepath.Base(dest)
	printer := newProgressPrinter()
	if showProgress && printer.IsInteractive() && resp.ContentLength > 0 {
		_, err = printer.CopyWithProgress(f, resp.Body, resp.ContentLength, label)
	} else {
		_, err = io.Copy(f, resp.Body)
	}
	return err
}
