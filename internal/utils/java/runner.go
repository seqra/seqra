package java

import (
	"fmt"
	"os/exec"
	"runtime"
	"strings"

	"github.com/sirupsen/logrus"
)

const (
	DefaultJavaVersion = 23
	LegacyJavaVersion  = 8
)

type JavaRunner interface {
	TrySystem() JavaRunner
	TrySpecificVersion(version int) JavaRunner
	GetJavaResolutions() []JavaResolution
	ExecuteJavaCommand(args []string, commandSucceeded func(error) bool) ([]byte, error)
}

type strategyType int

const (
	strategySystem strategyType = iota
	strategySpecificVersion
)

type strategy struct {
	strategyType strategyType
	version      int
}

type javaRunner struct {
	strategies []strategy
	javaPath   string
}

type JavaResolution func() (string, error)

func (j *javaRunner) GetJavaResolutions() []JavaResolution {
	if j.javaPath != "" {
		return []JavaResolution{
			func() (string, error) {
				logrus.Debugf("Using cached Java path: %s", j.javaPath)
				return j.javaPath, nil
			},
		}
	}

	if len(j.strategies) == 0 {
		return []JavaResolution{
			func() (string, error) {
				return "", fmt.Errorf("no Java resolution strategies configured")
			},
		}
	}

	logrus.Debugf("Starting Java resolution with %d strategies", len(j.strategies))

	var resolutionStrategies []JavaResolution
	for i, s := range j.strategies {
		strategyIndex := i + 1
		strategy := s
		switch strategy.strategyType {
		case strategySystem:
			resolutionStrategies = append(resolutionStrategies, func() (string, error) {
				logrus.Debugf("Trying system Java resolution (strategy %d)", strategyIndex)
				if javaPath := j.findSystemJava(); javaPath != "" {
					j.javaPath = javaPath
					logrus.Infof("Detected system Java (%s)", javaPath)
					return javaPath, nil
				}
				return "", fmt.Errorf("no suitable system Java found")
			})
		case strategySpecificVersion:
			resolutionStrategies = append(resolutionStrategies, func() (string, error) {
				logrus.Debugf("Trying specific Java version resolution (strategy %d): Java %d", strategyIndex, strategy.version)
				javaPath, err := j.ensureSpecificVersion(strategy.version)
				if err == nil {
					j.javaPath = javaPath
					logrus.Infof("Detected Java %d (%s)", strategy.version, javaPath)
					return javaPath, nil
				}
				logrus.Warnf("Failed to detect Java %d: %v", strategy.version, err)
				return "", fmt.Errorf("failed to detect Java %d: %w", strategy.version, err)
			})
		}
	}

	return resolutionStrategies
}

func (j *javaRunner) ExecuteJavaCommand(args []string, commandSucceeded func(error) bool) ([]byte, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("no Java command arguments provided")
	}

	resolutionStrategies := j.GetJavaResolutions()
	for i, resolutionStrategy := range resolutionStrategies {
		javaPath, err := resolutionStrategy()
		if err != nil {
			logrus.Debugf("Java resolution attempt %d failed: %v", i+1, err)
			continue
		}

		cmdArgs := append([]string{javaPath}, args...)
		cmd := exec.Command(cmdArgs[0], cmdArgs[1:]...)

		logrus.Debugf("Executing Java command (attempt %d): %s %v (full: %s)", i+1, javaPath, args, strings.Join(cmdArgs, " "))

		output, cmdErr := cmd.CombinedOutput()

		logrus.Debugf("Java command completed (attempt %d): output_size=%d, exit_code=%d", i+1, len(output), cmd.ProcessState.ExitCode())

		logrus.Debugf("Command output:\n%s", string(output))
		if commandSucceeded(err) {
			return output, nil
		}

		logrus.Debugf("Java command failed (attempt %d): exit_code=%d, error=%v, trying next resolution", i+1, cmd.ProcessState.ExitCode(), cmdErr)
	}

	return nil, fmt.Errorf("all Java resolution attempts failed")
}

func (j *javaRunner) TrySpecificVersion(version int) JavaRunner {
	j.strategies = append(j.strategies, strategy{
		strategyType: strategySpecificVersion,
		version:      version,
	})
	return j
}

func (j *javaRunner) TrySystem() JavaRunner {
	j.strategies = append(j.strategies, strategy{
		strategyType: strategySystem,
	})
	return j
}

func NewJavaRunner() JavaRunner {
	return &javaRunner{
		strategies: make([]strategy, 0),
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
	return ensureLocalRuntime(version, AdoptiumImageJDK, runtime.GOOS, runtime.GOARCH)
}
