package utils

import (
	"os"

	"github.com/sirupsen/logrus"
)

func RemoveIfExists(path string) error {
	err := os.Remove(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	return nil
}

func RemoveIfExistsOrExit(path string) {
	err := RemoveIfExists(path)
	if err != nil {
		logrus.Fatalf("Can't delete '%s': %s", path, err)
	}
}
