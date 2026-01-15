package utils

import (
	"os"
	"path/filepath"
)

func GetSeqraHome() (string, error) {
	// Find home directory.
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	// Search config in home directory with name ".seqra" (without extension).
	path := filepath.Join(home, ".seqra")
	merr := os.MkdirAll(path, os.ModePerm)
	if merr != nil {
		return "", merr
	}

	return path, nil
}

func GetAutobuilderJarPath(version string) (string, error) {
	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	autobuilderJar := filepath.Join(seqraHomePath, "autobuilder_"+version+".jar")
	return autobuilderJar, nil
}

func GetAnalyzerJarPath(version string) (string, error) {
	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	analyzerJar := filepath.Join(seqraHomePath, "analyzer_"+version+".jar")
	return analyzerJar, nil
}

func GetRulesPath(version string) (string, error) {
	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	rulesPath := filepath.Join(seqraHomePath, "rules_"+version)
	return rulesPath, nil
}
