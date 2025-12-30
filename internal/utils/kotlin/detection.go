package kotlin

import (
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"

	"github.com/sirupsen/logrus"
)

var (
	kotlinVersionRegex = regexp.MustCompile(`Kotlin version (\d+\.\d+\.\d+)`)
)

type KotlinInstallation struct {
	Path         string
	FullVersion  string
	Vendor       string
	MajorVersion int
}

func (k KotlinInstallation) String() string {
	return k.Path
}

func validateKotlinInstallation(kotlinPath string) *KotlinInstallation {
	logrus.Debugf("Validating Kotlin installation at: %s", kotlinPath)

	cmd := exec.Command(kotlinPath, "-version")
	output, err := cmd.Output()
	if err != nil {
		logrus.Debugf("Failed to run 'kotlin -version': %v", err)
		return nil
	}

	versionMatch := kotlinVersionRegex.FindStringSubmatch(string(output))
	if len(versionMatch) < 2 {
		logrus.Debugf("Could not parse Kotlin version from output: %s", string(output))
		return nil
	}

	fullVersion := versionMatch[1]
	logrus.Debugf("Detected Kotlin version: %s", fullVersion)

	// Parse major version
	majorVersion := 1 // default
	if match := regexp.MustCompile(`^(\d+)`).FindStringSubmatch(fullVersion); len(match) > 1 {
		if v, err := strconv.Atoi(match[1]); err == nil {
			majorVersion = v
		}
	}

	return &KotlinInstallation{
		Path:         kotlinPath,
		FullVersion:  fullVersion,
		Vendor:       "JetBrains", // Kotlin is from JetBrains
		MajorVersion: majorVersion,
	}
}

func DetectSystemKotlin() *KotlinInstallation {
	logrus.Debug("Starting system Kotlin detection")

	// Check for kotlinc in PATH
	if kotlinPath, err := exec.LookPath("kotlinc"); err == nil {
		logrus.Debugf("Checking PATH kotlinc: %s", kotlinPath)

		if installation := validateKotlinInstallation(kotlinPath); installation != nil {
			logrus.Debugf("Found Kotlin via PATH: %s v%s", installation.Path, installation.FullVersion)
			return installation
		} else {
			logrus.Debugf("PATH kotlinc is invalid: %s", kotlinPath)
		}
	} else {
		logrus.Debugf("No kotlinc found in PATH: %v", err)
	}

	// Check common installation paths
	commonPaths := []string{
		"/usr/local/kotlin/bin/kotlinc",
		"/opt/kotlin/bin/kotlinc",
		filepath.Join(os.Getenv("HOME"), ".kotlin", "bin", "kotlinc"),
	}

	for _, path := range commonPaths {
		logrus.Debugf("Checking common path: %s", path)
		if _, err := os.Stat(path); err == nil {
			if installation := validateKotlinInstallation(path); installation != nil {
				logrus.Debugf("Found Kotlin at common path: %s v%s", installation.Path, installation.FullVersion)
				return installation
			}
		}
	}

	return nil
}