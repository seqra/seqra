package java

import (
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"github.com/sirupsen/logrus"
)

var (
	versionRegex       = regexp.MustCompile(`version "([^"]+)"`)
	majorVersionRegex  = regexp.MustCompile(`^(\d+)`)
	legacyVersionRegex = regexp.MustCompile(`^1\.(\d+)`)
)

func DetectSystemJava() *JavaInstallation {
	logrus.Debug("Starting system Java detection")

	if javaHome := os.Getenv("JAVA_HOME"); javaHome != "" {
		javaPath := filepath.Join(javaHome, "bin", "java")
		logrus.Debugf("Checking JAVA_HOME: %s (java path: %s)", javaHome, javaPath)

		if installation := validateJavaInstallation(javaPath); installation != nil {
			logrus.Debugf("Found Java via JAVA_HOME: %s v%s (%s)", installation.Path, installation.FullVersion, installation.Vendor)
			return installation
		} else {
			logrus.Debugf("JAVA_HOME points to invalid Java: %s", javaPath)
		}
	} else {
		logrus.Debug("JAVA_HOME not set")
	}

	if javaPath, err := exec.LookPath("java"); err == nil {
		logrus.Debugf("Checking PATH java: %s", javaPath)

		if installation := validateJavaInstallation(javaPath); installation != nil {
			logrus.Debugf("Found Java via PATH: %s v%s (%s)", installation.Path, installation.FullVersion, installation.Vendor)
			return installation
		} else {
			logrus.Debugf("PATH java is invalid: %s", javaPath)
		}
	} else {
		logrus.Debugf("No java found in PATH: %v", err)
	}

	return nil
}

func ValidateJavaExecutable(javaPath string) bool {
	if javaPath == "" {
		logrus.Debug("Empty Java path provided for validation")
		return false
	}

	logrus.Debugf("Validating Java executable: %s", javaPath)
	result := validateJavaInstallation(javaPath) != nil

	logrus.Debugf("Java executable validation result for %s: valid=%t", javaPath, result)

	return result
}

func ParseJavaVersion(versionOutput string) (int, string, error) {
	matches := versionRegex.FindStringSubmatch(versionOutput)
	if len(matches) < 2 {
		return 0, "", &JavaVersionError{Output: versionOutput}
	}

	versionStr := matches[1]

	if legacyMatches := legacyVersionRegex.FindStringSubmatch(versionStr); len(legacyMatches) >= 2 {
		majorVersion, err := strconv.Atoi(legacyMatches[1])
		if err != nil {
			return 0, "", &JavaVersionError{Output: versionOutput}
		}
		return majorVersion, versionStr, nil
	}

	if majorMatches := majorVersionRegex.FindStringSubmatch(versionStr); len(majorMatches) >= 2 {
		majorVersion, err := strconv.Atoi(majorMatches[1])
		if err != nil {
			return 0, "", &JavaVersionError{Output: versionOutput}
		}
		return majorVersion, versionStr, nil
	}

	return 0, "", &JavaVersionError{Output: versionOutput}
}

type JavaInstallation struct {
	Path         string
	MajorVersion int
	FullVersion  string
	Vendor       string
}

type JavaVersionError struct {
	Output string
}

func (e *JavaVersionError) Error() string {
	return "failed to parse Java version from output: " + e.Output
}

func validateJavaInstallation(javaPath string) *JavaInstallation {
	if javaPath == "" {
		return nil
	}

	if _, err := os.Stat(javaPath); err != nil {
		logrus.Debugf("Java executable not found at %s: %v", javaPath, err)
		return nil
	}

	cmd := exec.Command(javaPath, "-version")
	output, err := cmd.CombinedOutput()
	if err != nil {
		logrus.Debugf("Java executable validation failed for %s: %v (output: %s)", javaPath, err, string(output))
		return nil
	}

	versionOutput := string(output)
	majorVersion, fullVersion, err := ParseJavaVersion(versionOutput)
	if err != nil {
		logrus.Debugf("Failed to parse Java version for %s: %v (output: %s)", javaPath, err, versionOutput)
		return nil
	}

	vendor := extractVendor(versionOutput)

	installation := &JavaInstallation{
		Path:         javaPath,
		MajorVersion: majorVersion,
		FullVersion:  fullVersion,
		Vendor:       vendor,
	}

	logrus.Debugf("Validated Java installation: %s v%s (major: %d, vendor: %s)", javaPath, fullVersion, majorVersion, vendor)

	return installation
}

func extractVendor(versionOutput string) string {
	output := strings.ToLower(versionOutput)

	if strings.Contains(output, "adoptium") || strings.Contains(output, "temurin") || strings.Contains(output, "eclipse") {
		return "Adoptium"
	}
	if strings.Contains(output, "amazon") || strings.Contains(output, "corretto") {
		return "Amazon Corretto"
	}
	if strings.Contains(output, "azul") || strings.Contains(output, "zulu") {
		return "Azul Zulu"
	}
	if strings.Contains(output, "hotspot") || strings.Contains(output, "java(tm)") {
		return "Oracle"
	}
	if strings.Contains(output, "openjdk") {
		return "OpenJDK"
	}

	return "Unknown"
}
