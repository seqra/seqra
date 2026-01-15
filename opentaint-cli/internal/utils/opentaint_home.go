package utils

import (
	"os"
	"path/filepath"
)

func GetOpentaintHome() (string, error) {
	// Find home directory.
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	// Search config in home directory with name ".opentaint" (without extension).
	path := filepath.Join(home, ".opentaint")
	merr := os.MkdirAll(path, os.ModePerm)
	if merr != nil {
		return "", merr
	}

	return path, nil
}

func GetAutobuilderJarPath(version string) (string, error) {
	opentaintHomePath, err := GetOpentaintHome()
	if err != nil {
		return "", err
	}
	autobuilderJar := filepath.Join(opentaintHomePath, "autobuilder_"+version+".jar")
	return autobuilderJar, nil
}

func GetAnalyzerJarPath(version string) (string, error) {
	opentaintHomePath, err := GetOpentaintHome()
	if err != nil {
		return "", err
	}
	analyzerJar := filepath.Join(opentaintHomePath, "analyzer_"+version+".jar")
	return analyzerJar, nil
}

func GetRulesPath(version string) (string, error) {
	opentaintHomePath, err := GetOpentaintHome()
	if err != nil {
		return "", err
	}
	rulesPath := filepath.Join(opentaintHomePath, "rules_"+version)
	return rulesPath, nil
}
