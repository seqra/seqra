package java

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

var adoptiumBaseURL = "https://api.adoptium.net"

type adoptiumBinaryPackage struct {
	Checksum string `json:"checksum"`
}

type adoptiumBinary struct {
	Package      adoptiumBinaryPackage `json:"package"`
	OS           string                `json:"os"`
	Architecture string                `json:"architecture"`
	ImageType    string                `json:"image_type"`
}

type adoptiumAsset struct {
	Binary adoptiumBinary `json:"binary"`
}

// FetchAdoptiumChecksum queries the Adoptium metadata API and returns the SHA256
// hash for the matching binary. Returns ("", nil) if API succeeds but no match found.
func FetchAdoptiumChecksum(javaVersion int, adoptiumOS AdoptiumOS, adoptiumArch AdoptiumArch, imageType AdoptiumImageType) (string, error) {
	url := fmt.Sprintf("%s/v3/assets/latest/%d/hotspot?image_type=%s&os=%s&architecture=%s",
		adoptiumBaseURL, javaVersion, imageType, adoptiumOS, adoptiumArch)

	resp, err := http.Get(url)
	if err != nil {
		return "", fmt.Errorf("failed to query Adoptium API: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("Adoptium API returned status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read Adoptium API response: %w", err)
	}

	var assets []adoptiumAsset
	if err := json.Unmarshal(body, &assets); err != nil {
		return "", fmt.Errorf("failed to parse Adoptium API response: %w", err)
	}

	for _, asset := range assets {
		b := asset.Binary
		if b.OS == string(adoptiumOS) && b.Architecture == string(adoptiumArch) && b.ImageType == string(imageType) {
			return strings.ToLower(b.Package.Checksum), nil
		}
	}

	return "", nil
}
