package kotlin

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"strings"

	"github.com/sirupsen/logrus"
)

const (
	DefaultKotlinVersion = "1.9.0"
)

type KotlinRunner interface {
	TrySystem() KotlinRunner
	GetKotlinResolutions() []KotlinResolution
	ExecuteKotlinCommand(args []string, commandSucceeded func(error) bool) error
}

type kotlinStrategyType int

const (
	strategySystem kotlinStrategyType = iota
)

type kotlinStrategy struct {
	strategyType kotlinStrategyType
	version      string
}

type kotlinRunner struct {
	strategies []kotlinStrategy
	kotlinPath string
}

type KotlinResolution func() (string, error)

func (k *kotlinRunner) GetKotlinResolutions() []KotlinResolution {
	if k.kotlinPath != "" {
		return []KotlinResolution{
			func() (string, error) {
				logrus.Debugf("Using cached Kotlin path: %s", k.kotlinPath)
				return k.kotlinPath, nil
			},
		}
	}

	if len(k.strategies) == 0 {
		return []KotlinResolution{
			func() (string, error) {
				return "", fmt.Errorf("no Kotlin resolution strategies configured")
			},
		}
	}

	logrus.Debugf("Starting Kotlin resolution with %d strategies", len(k.strategies))

	var resolutionStrategies []KotlinResolution
	for i, s := range k.strategies {
		strategyIndex := i + 1
		strategy := s
		switch strategy.strategyType {
		case strategySystem:
			resolutionStrategies = append(resolutionStrategies, func() (string, error) {
				logrus.Debugf("Trying system Kotlin resolution (strategy %d)", strategyIndex)
				if kotlinPath := k.findSystemKotlin(); kotlinPath != "" {
					k.kotlinPath = kotlinPath
					logrus.Debugf("Detected system Kotlin (%s)", kotlinPath)
					return kotlinPath, nil
				}
				return "", fmt.Errorf("no suitable system Kotlin found")
			})
		}
	}

	return resolutionStrategies
}

func (k *kotlinRunner) findSystemKotlin() string {
	if installation := DetectSystemKotlin(); installation != nil {
		return installation.Path
	}
	return ""
}

func (k *kotlinRunner) TrySystem() KotlinRunner {
	k.strategies = append(k.strategies, kotlinStrategy{strategyType: strategySystem})
	return k
}

func (k *kotlinRunner) ExecuteKotlinCommand(args []string, commandSucceeded func(error) bool) error {
	if len(args) == 0 {
		return fmt.Errorf("no Kotlin command arguments provided")
	}

	resolutions := k.GetKotlinResolutions()
	if len(resolutions) == 0 {
		return fmt.Errorf("no Kotlin resolution strategies available")
	}

	var lastErr error
	for _, resolve := range resolutions {
		kotlinPath, err := resolve()
		if err != nil {
			logrus.Debugf("Kotlin resolution failed: %v", err)
			lastErr = err
			continue
		}

		logrus.Debugf("Executing Kotlin command with: %s %s", kotlinPath, strings.Join(args, " "))

		cmd := exec.Command(kotlinPath, args...)
		cmd.Stderr = os.Stderr

		if !k.isBackgroundCommand(args) {
			stdout, err := cmd.StdoutPipe()
			if err != nil {
				lastErr = fmt.Errorf("failed to create stdout pipe: %w", err)
				continue
			}

			if err := cmd.Start(); err != nil {
				lastErr = fmt.Errorf("failed to start Kotlin command: %w", err)
				continue
			}

			if runtime.GOOS == "windows" {
				// On Windows, we need to read from stdout in a separate goroutine
				go func() {
					scanner := bufio.NewScanner(stdout)
					for scanner.Scan() {
						fmt.Println(scanner.Text())
					}
				}()
			} else {
				// On Unix-like systems, we can redirect stdout directly
				cmd.Stdout = os.Stdout
			}

			err = cmd.Wait()
		} else {
			err = cmd.Run()
		}

		if commandSucceeded != nil && commandSucceeded(err) {
			return nil
		}

		if err != nil {
			lastErr = fmt.Errorf("Kotlin command failed: %w", err)
		} else {
			return nil
		}
	}

	return lastErr
}

func (k *kotlinRunner) isBackgroundCommand(args []string) bool {
	// Kotlin doesn't typically have background commands like Java's compiler
	return false
}

func NewKotlinRunner() KotlinRunner {
	return &kotlinRunner{}
}