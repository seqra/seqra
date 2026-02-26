package java

import (
	"os"
	"os/exec"
	"regexp"
	"strconv"
	"strings"

	"github.com/seqra/seqra/v2/internal/output"
	"github.com/seqra/seqra/v2/internal/utils"
)

var (
	versionRegex       = regexp.MustCompile(`version "([^"]+)"`)
	majorVersionRegex  = regexp.MustCompile(`^(\d+)`)
	legacyVersionRegex = regexp.MustCompile(`^1\.(\d+)`)
)

func DetectSystemJava() *JavaInstallation {
	output.LogDebug("Starting system Java detection")

	if javaHome := os.Getenv("JAVA_HOME"); javaHome != "" {
		javaPath := utils.JavaBinaryPath(javaHome)
		output.LogDebugf("Checking JAVA_HOME: %s (java path: %s)", javaHome, javaPath)

		if installation := validateJavaInstallation(javaPath); installation != nil {
			output.LogDebugf("Found Java via JAVA_HOME: %s v%s (%s)", installation.Path, installation.FullVersion, installation.Vendor)
			return installation
		} else {
			output.LogDebugf("JAVA_HOME points to invalid Java: %s", javaPath)
		}
	} else {
		output.LogDebug("JAVA_HOME not set")
	}

	if javaPath, err := exec.LookPath("java"); err == nil {
		output.LogDebugf("Checking PATH java: %s", javaPath)

		if installation := validateJavaInstallation(javaPath); installation != nil {
			output.LogDebugf("Found Java via PATH: %s v%s (%s)", installation.Path, installation.FullVersion, installation.Vendor)
			return installation
		} else {
			output.LogDebugf("PATH java is invalid: %s", javaPath)
		}
	} else {
		output.LogDebugf("No java found in PATH: %v", err)
	}

	return nil
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
		output.LogDebugf("Java executable not found at %s: %v", javaPath, err)
		return nil
	}

	cmd := exec.Command(javaPath, "-version")
	cmdOutput, err := cmd.CombinedOutput()
	if err != nil {
		output.LogDebugf("Java executable validation failed for %s: %v (output: %s)", javaPath, err, string(cmdOutput))
		return nil
	}

	versionOutput := string(cmdOutput)
	majorVersion, fullVersion, err := ParseJavaVersion(versionOutput)
	if err != nil {
		output.LogDebugf("Failed to parse Java version for %s: %v (output: %s)", javaPath, err, versionOutput)
		return nil
	}

	vendor := extractVendor(versionOutput)

	installation := &JavaInstallation{
		Path:         javaPath,
		MajorVersion: majorVersion,
		FullVersion:  fullVersion,
		Vendor:       vendor,
	}

	output.LogDebugf("Validated Java installation: %s v%s (major: %d, vendor: %s)", javaPath, fullVersion, majorVersion, vendor)

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
