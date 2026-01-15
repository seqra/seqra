package java

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"strings"

	"github.com/sirupsen/logrus"
)

const (
	DefaultJavaVersion = 23
	LegacyJavaVersion  = 8
)

type ResolutionStrategy int

const (
	System ResolutionStrategy = iota
	Specific
	None
)

type JavaRunner interface {
	TrySystem() JavaRunner
	TrySpecificVersion(version int) JavaRunner
	GetJavaResolutions() []JavaResolution
	ExecuteJavaCommand(args []string, commandSucceeded func(error) bool) error
}

type javaRunner struct {
	trySystemStrategy bool
	specificStrategy  *int
}

type JavaResolution func() (string, ResolutionStrategy, error)

func (j *javaRunner) GetJavaResolutions() []JavaResolution {
	if !j.trySystemStrategy && j.specificStrategy == nil {
		return []JavaResolution{
			func() (string, ResolutionStrategy, error) {
				return "", None, fmt.Errorf("no Java resolution strategies configured")
			},
		}
	}

	// Implement fallback behavior when both strategies are configured
	if j.trySystemStrategy && j.specificStrategy != nil {
		version := *j.specificStrategy
		logrus.Debugf("Starting Java resolution with system first, fallback to Java %d", version)
		return []JavaResolution{
			func() (string, ResolutionStrategy, error) {
				logrus.Debugf("Trying system Java first (fallback strategy)")
				if javaPath := j.findSystemJava(); javaPath != "" {
					logrus.Debugf("System Java found (%s), using it", javaPath)
					return javaPath, System, nil
				}

				logrus.Debugf("System Java not found, falling back to Java %d", version)
				javaPath, err := j.ensureSpecificVersion(version)
				if err == nil {
					logrus.Debugf("Fallback Java %d found (%s)", version, javaPath)
					return javaPath, Specific, nil
				}

				logrus.Warnf("Both system Java and Java %d failed: %v", version, err)
				return "", None, fmt.Errorf("failed to find system Java or Java %d: %w", version, err)
			},
		}
	}

	// Single strategy cases
	var resolutionStrategies []JavaResolution

	if j.trySystemStrategy {
		logrus.Debugf("Starting Java resolution with system strategy only")
		resolutionStrategies = append(resolutionStrategies, func() (string, ResolutionStrategy, error) {
			logrus.Debugf("Trying system Java resolution")
			if javaPath := j.findSystemJava(); javaPath != "" {
				logrus.Debugf("Detected system Java (%s)", javaPath)
				return javaPath, System, nil
			}
			return "", None, fmt.Errorf("no suitable system Java found")
		})
	}

	if j.specificStrategy != nil {
		version := *j.specificStrategy
		logrus.Debugf("Starting Java resolution with specific version strategy only: Java %d", version)
		resolutionStrategies = append(resolutionStrategies, func() (string, ResolutionStrategy, error) {
			logrus.Debugf("Trying specific Java version resolution: Java %d", version)
			javaPath, err := j.ensureSpecificVersion(version)
			if err == nil {
				logrus.Debugf("Detected Java %d (%s)", version, javaPath)
				return javaPath, Specific, nil
			}
			logrus.Warnf("Failed to detect Java %d: %v", version, err)
			return "", None, fmt.Errorf("failed to detect Java %d: %w", version, err)
		})
	}

	return resolutionStrategies
}

func (j *javaRunner) ExecuteJavaCommand(args []string, commandSucceeded func(error) bool) error {
	if len(args) == 0 {
		return fmt.Errorf("no Java command arguments provided")
	}

	resolutionStrategies := j.GetJavaResolutions()
	for i, resolutionStrategy := range resolutionStrategies {
		javaPath, resolutionStrategy, err := resolutionStrategy()
		if err != nil {
			logrus.Debugf("Java resolution attempt %d failed: %v", i+1, err)
			continue
		}

		cmdArgs := append([]string{javaPath}, args...)
		cmd := exec.Command(cmdArgs[0], cmdArgs[1:]...)

		// Set clean environment for specific version strategy
		if resolutionStrategy == Specific {
			cmd.Env = j.getCleanEnvironment()
			logrus.Debug("Using clean environment for specific Java version strategy")
		}

		logrus.Debugf("Executing Java command (attempt %d): %s %v (full: %s)", i+1, javaPath, args, strings.Join(cmdArgs, " "))

		// Create pipes for stdout and stderr
		stdoutPipe, err := cmd.StdoutPipe()
		if err != nil {
			logrus.Fatalf("Failed to create stdout pipe: %v", err)
		}

		stderrPipe, err := cmd.StderrPipe()
		if err != nil {
			logrus.Fatalf("Failed to create stderr pipe: %v", err)
		}

		// Start the command
		if err := cmd.Start(); err != nil {
			logrus.Fatalf("Failed to start autobuilder: %v", err)
		}

		// Function to read from a reader and log each line
		logOutput := func(pipe io.Reader) {
			scanner := bufio.NewScanner(pipe)
			for scanner.Scan() {
				logrus.Debug(scanner.Text())
			}
			if err := scanner.Err(); err != nil {
				logrus.Debugf("Error reading autobuilder output: %v", err)
			}
		}

		// Start goroutines to read and log stdout and stderr
		go logOutput(stdoutPipe)
		go logOutput(stderrPipe)

		// Wait for the command to finish
		err = cmd.Wait()

		// Log any errors
		if err != nil {
			exitCode := 1
			if exitErr, ok := err.(*exec.ExitError); ok {
				exitCode = exitErr.ExitCode()
			}
			logrus.Errorf("Autobuilder exited with code %d: %v", exitCode, err)
		}

		logrus.Debugf("Java command completed (attempt %d): exit_code=%d", i+1, cmd.ProcessState.ExitCode())

		if commandSucceeded(err) {
			return nil
		}

		logrus.Debugf("Java command failed (attempt %d): exit_code=%d, trying next resolution", i+1, cmd.ProcessState.ExitCode())
	}

	return fmt.Errorf("all Java resolution attempts failed")
}

func (j *javaRunner) TrySpecificVersion(version int) JavaRunner {
	j.specificStrategy = &version
	return j
}

func (j *javaRunner) TrySystem() JavaRunner {
	j.trySystemStrategy = true
	return j
}

// unsetJavaEnvironmentVariables unsets Java-related environment variables
// to ensure a clean environment when using specific Java versions
func unsetJavaEnvironmentVariables() {
	javaEnvVars := []string{
		"JAVA_HOME",
		"JAVA_8_HOME",
		"JAVA_11_HOME",
		"JAVA_17_HOME",
		"JAVA_LATEST_HOME",
	}

	logrus.Debug("Unsetting Java environment variables for clean environment")

	for _, envVar := range javaEnvVars {
		if value := os.Getenv(envVar); value != "" {
			logrus.Debugf("Unsetting %s (was: %s)", envVar, value)
			if err := os.Unsetenv(envVar); err != nil {
				logrus.Warnf("Failed to unset %s: %v", envVar, err)
			}
		} else {
			logrus.Debugf("%s not set, skipping", envVar)
		}
	}

	logrus.Debug("Java environment variables unset for clean environment")
}

// getCleanEnvironment returns environment variables with Java-related variables excluded
// for use in command execution with specific Java versions
func (j *javaRunner) getCleanEnvironment() []string {
	javaEnvVars := map[string]bool{
		"JAVA_HOME":        true,
		"JAVA_8_HOME":      true,
		"JAVA_11_HOME":     true,
		"JAVA_17_HOME":     true,
		"JAVA_LATEST_HOME": true,
	}

	var cleanEnv []string
	for _, env := range os.Environ() {
		parts := strings.SplitN(env, "=", 2)
		if len(parts) == 2 {
			if !javaEnvVars[parts[0]] {
				cleanEnv = append(cleanEnv, env)
			} else {
				logrus.Debugf("Excluding %s from command environment", parts[0])
			}
		}
	}

	logrus.Debugf("Created clean environment with %d variables (excluded Java variables)", len(cleanEnv))
	return cleanEnv
}

func NewJavaRunner() JavaRunner {
	return &javaRunner{
		trySystemStrategy: false,
		specificStrategy:  nil,
	}
}

func (j *javaRunner) findSystemJava() string {
	installation := DetectSystemJava()
	if installation != nil {
		logrus.Debugf("Found system Java: %s v%s (%s)", installation.Path, installation.FullVersion, installation.Vendor)
		return installation.Path
	}

	logrus.Debug("No system Java found")
	return ""
}

func (j *javaRunner) ensureSpecificVersion(version int) (string, error) {
	if version < 8 || version > 25 {
		return "", fmt.Errorf("unsupported Java version: %d (supported range: 8-25)", version)
	}

	// Unset Java environment variables for clean environment when using specific version
	unsetJavaEnvironmentVariables()

	return ensureLocalRuntime(version, AdoptiumImageJDK, runtime.GOOS, runtime.GOARCH)
}
