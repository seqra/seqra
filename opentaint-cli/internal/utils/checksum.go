package utils

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/google/go-github/v72/github"
)

// ComputeFileSHA256 returns the lowercase hex SHA256 hash of a file.
func ComputeFileSHA256(filePath string) (string, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return "", err
	}
	defer func() { _ = f.Close() }()

	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// ParseChecksumFile parses GoReleaser's checksums.txt format (<sha256>  <filename>).
// Returns map[filename]hash. Blank lines are skipped. Returns error on malformed lines.
func ParseChecksumFile(content []byte) (map[string]string, error) {
	result := make(map[string]string)
	scanner := bufio.NewScanner(bytes.NewReader(content))
	lineNum := 0
	for scanner.Scan() {
		lineNum++
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		// GoReleaser format: "<hash>  <filename>" (two spaces)
		parts := strings.SplitN(line, "  ", 2)
		if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
			return nil, fmt.Errorf("malformed checksum line %d: %q", lineNum, line)
		}
		result[parts[1]] = strings.ToLower(parts[0])
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return result, nil
}

// FetchReleaseChecksums downloads checksums.txt from a GitHub release.
// Returns (nil, nil) if no checksums.txt asset exists (older releases).
// Returns (nil, err) on API/download errors.
func FetchReleaseChecksums(client *github.Client, owner, repo string, release *github.RepositoryRelease) (map[string]string, error) {
	ctx := context.Background()
	for _, asset := range release.Assets {
		if asset.GetName() == "checksums.txt" {
			rc, _, err := client.Repositories.DownloadReleaseAsset(ctx, owner, repo, asset.GetID(), client.Client())
			if err != nil {
				return nil, fmt.Errorf("failed to download checksums.txt: %w", err)
			}
			defer func() { _ = rc.Close() }()

			data, err := io.ReadAll(rc)
			if err != nil {
				return nil, fmt.Errorf("failed to read checksums.txt: %w", err)
			}
			return ParseChecksumFile(data)
		}
	}
	return nil, nil
}

// VerifyFileChecksum computes SHA256 of filePath and compares to expectedHash.
// Returns nil on match, descriptive error on mismatch.
func VerifyFileChecksum(filePath, expectedHash string) error {
	actual, err := ComputeFileSHA256(filePath)
	if err != nil {
		return fmt.Errorf("failed to compute checksum: %w", err)
	}
	if actual != strings.ToLower(expectedHash) {
		return fmt.Errorf("checksum mismatch: expected %s, got %s", strings.ToLower(expectedHash), actual)
	}
	return nil
}
