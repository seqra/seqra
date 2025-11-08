package utils

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"github.com/sirupsen/logrus"
)

// CheckJavaVersion checks if Java 17+ is available on the system
// Returns the Java version if found, or an error if Java 17+ is not available
func CheckJavaVersion() (int, error) {
	// Determine which java executable to use
	javaCmd := getJavaExecutable()

	// Try to run java -version
	cmd := exec.Command(javaCmd, "-version")
	output, err := cmd.CombinedOutput()
	if err != nil {
		return 0, fmt.Errorf("java is not installed or not available in PATH, java 17 or later is required")
	}

	// Parse the version from the output
	version, err := parseJavaVersion(string(output))
	if err != nil {
		return 0, fmt.Errorf("failed to parse java version: %v", err)
	}

	logrus.Debugf("Detected Java version: %d", version)

	if version < 17 {
		return version, fmt.Errorf("java 17 or later is required, but found java %d", version)
	}

	return version, nil
}

// parseJavaVersion parses Java version from the output of "java -version"
func parseJavaVersion(output string) (int, error) {
	// Java version output examples:
	// Java 8: java version "1.8.0_XXX"
	// Java 11+: java version "11.0.X" or openjdk version "17.0.X"

	lines := strings.Split(output, "\n")
	if len(lines) == 0 {
		return 0, fmt.Errorf("empty java version output")
	}

	// Look for version in the first line
	versionLine := lines[0]

	// Regex to match version patterns
	// Matches: "1.8.0_XXX", "11.0.X", "17.0.X", etc.
	re := regexp.MustCompile(`"(\d+)\.(\d+)\..*?"`)
	matches := re.FindStringSubmatch(versionLine)

	if len(matches) < 3 {
		// Try alternative regex for different formats
		re2 := regexp.MustCompile(`"(\d+)\..*?"`)
		matches2 := re2.FindStringSubmatch(versionLine)
		if len(matches2) < 2 {
			return 0, fmt.Errorf("could not parse version from: %s", versionLine)
		}

		majorVersion, err := strconv.Atoi(matches2[1])
		if err != nil {
			return 0, fmt.Errorf("invalid major version: %s", matches2[1])
		}

		return majorVersion, nil
	}

	majorVersion, err := strconv.Atoi(matches[1])
	if err != nil {
		return 0, fmt.Errorf("invalid major version: %s", matches[1])
	}

	minorVersion, err := strconv.Atoi(matches[2])
	if err != nil {
		return 0, fmt.Errorf("invalid minor version: %s", matches[2])
	}

	// For Java 8 and below, the version format is "1.X.Y"
	// For Java 9+, the version format is "X.Y.Z"
	if majorVersion == 1 {
		return minorVersion, nil
	}

	return majorVersion, nil
}

// getJavaExecutable determines which java executable to use
// Prioritizes JAVA_HOME/bin/java if JAVA_HOME is set, otherwise uses 'java' from PATH
func getJavaExecutable() string {
	javaHome := os.Getenv("JAVA_HOME")
	if javaHome != "" {
		// Check if JAVA_HOME/bin/java exists
		javaPath := filepath.Join(javaHome, "bin", "java")
		if _, err := os.Stat(javaPath); err == nil {
			logrus.Debugf("Using Java from JAVA_HOME: %s", javaPath)
			return javaPath
		}
		logrus.Debugf("JAVA_HOME is set but %s not found, falling back to PATH", javaPath)
	}

	// Fall back to java from PATH
	return "java"
}
