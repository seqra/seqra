package utils

import (
	"regexp"
	"runtime"
)

func GetImageLink(version, path string) string {
	imageRefRegex := regexp.MustCompile(`^([a-zA-Z0-9.-/]*)?[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+$`)
	if imageRefRegex.MatchString(version) {
		return version
	}
	return path + "-" + GetArch() + ":" + version
}

func GetArch() string {
	return runtime.GOARCH
}

func IsSupportedArch() bool {
	arch := GetArch()
	return arch == "arm64" || arch == "amd64"
}
