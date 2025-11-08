package utils

import (
	"os"
)

func GetSeqraHome() (string, error) {
	// Find home directory.
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	// Search config in home directory with name ".seqra" (without extension).
	path := home + "/.seqra"
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
	autobuilderJar := seqraHomePath + "/autobuilder_" + version + ".jar"
	return autobuilderJar, nil
}

func GetAnalyzerJarPath(version string) (string, error) {
	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	analyzerJar := seqraHomePath + "/analyzer_" + version + ".jar"
	return analyzerJar, nil
}

func GetRulesPath(version string) (string, error) {
	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	rulesPath := seqraHomePath + "/rules_" + version
	return rulesPath, nil
}
