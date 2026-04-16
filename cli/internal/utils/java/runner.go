package java

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
)

type ResolutionStrategy int

const (
	System ResolutionStrategy = iota
	Bundled
	Specific
	None
)

type JavaRunner interface {
	TrySystem() JavaRunner
	TrySpecificVersion(version int) JavaRunner
	WithImageType(imageType AdoptiumImageType) JavaRunner
	WithSkipVerify(skipVerify bool) JavaRunner
	WithDebugOutput(writer DebugLineWriter) JavaRunner
	WithStreamOutput(stream bool) JavaRunner
	GetJavaResolutions() []JavaResolution
	// EnsureJava resolves and downloads Java if needed, returning the path.
	// Call this before wrapping ExecuteJavaCommand in a spinner to avoid
	// download progress bars overlapping with spinner output.
	EnsureJava() (string, error)
	ExecuteJavaCommand(args []string, commandSucceeded func(error) bool) error
}

type DebugLineWriter interface {
	WriteLine(text string)
}

type javaRunner struct {
	trySystemStrategy bool
	specificStrategy  *int
	imageType         AdoptiumImageType
	skipVerify        bool
	streamOutput      bool
	resolvedJavaPath  string
	debugOutput       DebugLineWriter
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
		output.LogDebugf("Starting Java resolution with system first, fallback to Java %d", version)
		return []JavaResolution{
			func() (string, ResolutionStrategy, error) {
				output.LogDebugf("Trying system Java first (fallback strategy)")
				if javaPath := j.findSystemJava(); javaPath != "" {
					output.LogDebugf("System Java found (%s), using it", javaPath)
					return javaPath, System, nil
				}

				output.LogDebugf("System Java not found, falling back to Java %d", version)
				javaPath, err := j.ensureSpecificVersion(version)
				if err == nil {
					output.LogDebugf("Fallback Java %d found (%s)", version, javaPath)
					return javaPath, Specific, nil
				}

				output.LogInfof("Both system Java and Java %d failed: %v", version, err)
				return "", None, fmt.Errorf("failed to find system Java or Java %d: %w", version, err)
			},
		}
	}

	// Single strategy cases
	var resolutionStrategies []JavaResolution

	if j.trySystemStrategy {
		output.LogDebugf("Starting Java resolution with system strategy only")
		resolutionStrategies = append(resolutionStrategies, func() (string, ResolutionStrategy, error) {
			output.LogDebugf("Trying system Java resolution")
			if javaPath := j.findSystemJava(); javaPath != "" {
				output.LogDebugf("Detected system Java (%s)", javaPath)
				return javaPath, System, nil
			}
			return "", None, fmt.Errorf("no suitable system Java found")
		})
	}

	if j.specificStrategy != nil {
		version := *j.specificStrategy
		output.LogDebugf("Starting Java resolution with specific version strategy only: Java %d", version)

		// Try bundled JRE first (only when not using system strategy)
		if !j.trySystemStrategy {
			resolutionStrategies = append(resolutionStrategies, func() (string, ResolutionStrategy, error) {
				output.LogDebugf("Trying bundled JRE resolution")
				if javaPath := j.findBundledJRE(); javaPath != "" {
					output.LogDebugf("Found bundled JRE (%s)", javaPath)
					return javaPath, Bundled, nil
				}
				return "", None, fmt.Errorf("no bundled JRE found")
			})
		}

		resolutionStrategies = append(resolutionStrategies, func() (string, ResolutionStrategy, error) {
			output.LogDebugf("Trying specific Java version resolution: Java %d", version)
			javaPath, err := j.ensureSpecificVersion(version)
			if err == nil {
				output.LogDebugf("Detected Java %d (%s)", version, javaPath)
				return javaPath, Specific, nil
			}
			output.LogInfof("Failed to detect Java %d: %v", version, err)
			return "", None, fmt.Errorf("failed to detect Java %d: %w", version, err)
		})
	}

	return resolutionStrategies
}

func (j *javaRunner) EnsureJava() (string, error) {
	resolutionStrategies := j.GetJavaResolutions()
	for i, strategy := range resolutionStrategies {
		javaPath, _, err := strategy()
		if err != nil {
			output.LogDebugf("Java resolution attempt %d failed: %v", i+1, err)
			continue
		}
		j.resolvedJavaPath = javaPath
		return javaPath, nil
	}
	return "", fmt.Errorf("all Java resolution attempts failed")
}

func (j *javaRunner) ExecuteJavaCommand(args []string, commandSucceeded func(error) bool) error {
	if len(args) == 0 {
		return fmt.Errorf("no Java command arguments provided")
	}

	// If EnsureJava was called, use the pre-resolved path directly
	if j.resolvedJavaPath != "" {
		return j.executeWithJava(j.resolvedJavaPath, Specific, args, commandSucceeded)
	}

	resolutionStrategies := j.GetJavaResolutions()
	for i, resolutionStrategy := range resolutionStrategies {
		javaPath, strategy, err := resolutionStrategy()
		if err != nil {
			output.LogDebugf("Java resolution attempt %d failed: %v", i+1, err)
			continue
		}

		if err := j.executeWithJava(javaPath, strategy, args, commandSucceeded); err == nil {
			return nil
		}

		output.LogDebugf("Java command failed (attempt %d), trying next resolution", i+1)
	}

	return fmt.Errorf("all Java resolution attempts failed")
}

func (j *javaRunner) executeWithJava(javaPath string, strategy ResolutionStrategy, args []string, commandSucceeded func(error) bool) error {
	cmdArgs := append([]string{javaPath}, args...)
	cmd := exec.Command(cmdArgs[0], cmdArgs[1:]...)

	// Set clean environment for bundled or specific version strategy
	if strategy == Bundled || strategy == Specific {
		cmd.Env = j.getCleanEnvironment()
		output.LogDebug("Using clean environment for managed Java version strategy")
	}

	output.LogDebugf("Executing Java command: %s %v (full: %s)", javaPath, args, strings.Join(cmdArgs, " "))

	// Create pipes for stdout and stderr
	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("failed to create stdout pipe: %w", err)
	}

	stderrPipe, err := cmd.StderrPipe()
	if err != nil {
		return fmt.Errorf("failed to create stderr pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("failed to start Java command: %w", err)
	}

	streamToTerminal := shouldStreamJavaOutput(globals.Config.Log.Verbosity, j.streamOutput)

	// Function to read from a reader and log each line
	logOutput := func(pipe io.Reader) {
		scanner := bufio.NewScanner(pipe)
		for scanner.Scan() {
			line := scanner.Text()
			output.LogDebug(line)
			if streamToTerminal {
				if j.debugOutput != nil {
					j.debugOutput.WriteLine(line)
				} else {
					fmt.Fprintln(os.Stderr, line)
				}
			}
		}
		if err := scanner.Err(); err != nil {
			output.LogDebugf("Error reading command output: %v", err)
		}
	}

	// Start goroutines to read and log stdout and stderr
	go logOutput(stdoutPipe)
	go logOutput(stderrPipe)

	// Wait for the command to finish
	err = cmd.Wait()

	// Log any errors at debug level (caller decides severity)
	if err != nil {
		exitCode := 1
		if exitErr, ok := err.(*exec.ExitError); ok {
			exitCode = exitErr.ExitCode()
		}
		output.LogDebugf("Java command exited with code %d: %v", exitCode, err)
	}

	if commandSucceeded(err) {
		return nil
	}

	return fmt.Errorf("java command failed")
}

func shouldStreamJavaOutput(verbosity string, forceStream bool) bool {
	level := strings.ToLower(strings.TrimSpace(verbosity))
	return level == "debug" || forceStream
}

func (j *javaRunner) TrySpecificVersion(version int) JavaRunner {
	j.specificStrategy = &version
	return j
}

func (j *javaRunner) TrySystem() JavaRunner {
	j.trySystemStrategy = true
	return j
}

func (j *javaRunner) WithImageType(imageType AdoptiumImageType) JavaRunner {
	j.imageType = imageType
	return j
}

func (j *javaRunner) WithSkipVerify(skipVerify bool) JavaRunner {
	j.skipVerify = skipVerify
	return j
}

func (j *javaRunner) WithDebugOutput(writer DebugLineWriter) JavaRunner {
	j.debugOutput = writer
	return j
}

func (j *javaRunner) WithStreamOutput(stream bool) JavaRunner {
	j.streamOutput = stream
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

	output.LogDebug("Unsetting Java environment variables for clean environment")

	for _, envVar := range javaEnvVars {
		if value := os.Getenv(envVar); value != "" {
			output.LogDebugf("Unsetting %s (was: %s)", envVar, value)
			if err := os.Unsetenv(envVar); err != nil {
				output.LogInfof("Failed to unset %s: %v", envVar, err)
			}
		} else {
			output.LogDebugf("%s not set, skipping", envVar)
		}
	}

	output.LogDebug("Java environment variables unset for clean environment")
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
				output.LogDebugf("Excluding %s from command environment", parts[0])
			}
		}
	}

	output.LogDebugf("Created clean environment with %d variables (excluded Java variables)", len(cleanEnv))
	return cleanEnv
}

func NewJavaRunner() JavaRunner {
	return &javaRunner{
		trySystemStrategy: false,
		specificStrategy:  nil,
		imageType:         AdoptiumImageJDK,
	}
}

func (j *javaRunner) findBundledJRE() string {
	tiers := utils.CurrentTiers(utils.ManagedJRETiers(), utils.IsInstallCurrent())
	if tier := utils.FindExistingJRE(tiers); tier != nil {
		return utils.JavaBinaryPath(tier.Path)
	}
	return ""
}

func (j *javaRunner) findSystemJava() string {
	installation := DetectSystemJava()
	if installation != nil {
		output.LogDebugf("Found system Java: %s v%s (%s)", installation.Path, installation.FullVersion, installation.Vendor)
		return installation.Path
	}

	output.LogDebug("No system Java found")
	return ""
}

func (j *javaRunner) ensureSpecificVersion(version int) (string, error) {
	if version < 8 || version > 25 {
		return "", fmt.Errorf("unsupported Java version: %d (supported range: 8-25)", version)
	}

	// Unset Java environment variables for clean environment when using specific version
	unsetJavaEnvironmentVariables()

	opentaintHome, err := utils.GetOpenTaintHome()
	if err != nil {
		return "", err
	}
	adoptiumOS, adoptiumArch, err := MapPlatformToAdoptium(runtime.GOOS, runtime.GOARCH)
	if err != nil {
		return "", err
	}
	cacheDir := filepath.Join(opentaintHome, "jre", fmt.Sprintf("temurin-%d-jre-%s-%s", version, adoptiumOS, adoptiumArch))

	tiers := utils.JRETiers(version, cacheDir)
	if len(tiers) == 0 {
		return "", fmt.Errorf("no storage tiers available for Java %d", version)
	}

	if found := utils.FindExistingJRE(utils.CurrentTiers(tiers, utils.IsInstallCurrent())); found != nil {
		return utils.JavaBinaryPath(found.Path), nil
	}

	// Match artifact behavior: for default/bind versions prefer install tier,
	// for non-default versions use cache tier.
	downloadTarget := tiers[len(tiers)-1].Path
	return ensureLocalRuntimeAt(version, j.imageType, downloadTarget, runtime.GOOS, runtime.GOARCH, j.skipVerify, nil)
}
