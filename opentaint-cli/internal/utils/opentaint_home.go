package utils

import (
	"os"
)

func GetOpentaintHome() (string, error) {
	// Find home directory.
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	// Search config in home directory with name ".opentaint" (without extension).
	path := home + "/.opentaint"
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
	autobuilderJar := opentaintHomePath + "/autobuilder_" + version + ".jar"
	return autobuilderJar, nil
}

func GetRulesPath(version string) (string, error) {
	opentaintHomePath, err := GetOpentaintHome()
	if err != nil {
		return "", err
	}
	rulesPath := opentaintHomePath + "/rules_" + version
	return rulesPath, nil
}
