package utils

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/google/go-github/v72/github"
)

func newTestClient(serverURL string) *github.Client {
	client := github.NewClient(nil)
	u, _ := url.Parse(serverURL + "/")
	client.BaseURL = u
	return client
}

func TestComputeFileSHA256(t *testing.T) {
	t.Run("known content", func(t *testing.T) {
		dir := t.TempDir()
		fp := filepath.Join(dir, "test.txt")
		content := []byte("hello world\n")
		if err := os.WriteFile(fp, content, 0o644); err != nil {
			t.Fatal(err)
		}

		h := sha256.Sum256(content)
		expected := hex.EncodeToString(h[:])

		got, err := ComputeFileSHA256(fp)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if got != expected {
			t.Errorf("got %s, want %s", got, expected)
		}
	})

	t.Run("empty file", func(t *testing.T) {
		dir := t.TempDir()
		fp := filepath.Join(dir, "empty.txt")
		if err := os.WriteFile(fp, []byte{}, 0o644); err != nil {
			t.Fatal(err)
		}

		h := sha256.Sum256([]byte{})
		expected := hex.EncodeToString(h[:])

		got, err := ComputeFileSHA256(fp)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if got != expected {
			t.Errorf("got %s, want %s", got, expected)
		}
	})

	t.Run("nonexistent file", func(t *testing.T) {
		_, err := ComputeFileSHA256("/nonexistent/path/file.txt")
		if err == nil {
			t.Error("expected error for nonexistent file")
		}
	})
}

func TestParseChecksumFile(t *testing.T) {
	t.Run("valid multi-line", func(t *testing.T) {
		content := []byte("abc123  file1.tar.gz\ndef456  file2.zip\n")
		m, err := ParseChecksumFile(content)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(m) != 2 {
			t.Fatalf("expected 2 entries, got %d", len(m))
		}
		if m["file1.tar.gz"] != "abc123" {
			t.Errorf("file1.tar.gz: got %q, want %q", m["file1.tar.gz"], "abc123")
		}
		if m["file2.zip"] != "def456" {
			t.Errorf("file2.zip: got %q, want %q", m["file2.zip"], "def456")
		}
	})

	t.Run("empty input", func(t *testing.T) {
		m, err := ParseChecksumFile([]byte{})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(m) != 0 {
			t.Errorf("expected empty map, got %d entries", len(m))
		}
	})

	t.Run("blank lines skipped", func(t *testing.T) {
		content := []byte("\nabc123  file.tar.gz\n\n\n")
		m, err := ParseChecksumFile(content)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(m) != 1 {
			t.Fatalf("expected 1 entry, got %d", len(m))
		}
	})

	t.Run("malformed line", func(t *testing.T) {
		content := []byte("abc123 file.tar.gz\n") // single space instead of two
		_, err := ParseChecksumFile(content)
		if err == nil {
			t.Error("expected error for malformed line")
		}
	})

	t.Run("CRLF line endings", func(t *testing.T) {
		content := []byte("abc123  file1.tar.gz\r\ndef456  file2.zip\r\n")
		m, err := ParseChecksumFile(content)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(m) != 2 {
			t.Fatalf("expected 2 entries, got %d", len(m))
		}
	})

	t.Run("uppercase hash normalized", func(t *testing.T) {
		content := []byte("ABC123  file.tar.gz\n")
		m, err := ParseChecksumFile(content)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if m["file.tar.gz"] != "abc123" {
			t.Errorf("got %q, want %q", m["file.tar.gz"], "abc123")
		}
	})
}

func TestVerifyFileChecksum(t *testing.T) {
	dir := t.TempDir()
	fp := filepath.Join(dir, "test.txt")
	content := []byte("test content\n")
	if err := os.WriteFile(fp, content, 0o644); err != nil {
		t.Fatal(err)
	}
	h := sha256.Sum256(content)
	correctHash := hex.EncodeToString(h[:])

	t.Run("match", func(t *testing.T) {
		if err := VerifyFileChecksum(fp, correctHash); err != nil {
			t.Errorf("expected nil, got %v", err)
		}
	})

	t.Run("mismatch", func(t *testing.T) {
		err := VerifyFileChecksum(fp, "0000000000000000000000000000000000000000000000000000000000000000")
		if err == nil {
			t.Error("expected error for mismatch")
		}
		if !strings.Contains(err.Error(), "checksum mismatch") {
			t.Errorf("expected mismatch error, got: %v", err)
		}
	})

	t.Run("case insensitive", func(t *testing.T) {
		upper := strings.ToUpper(correctHash)
		if err := VerifyFileChecksum(fp, upper); err != nil {
			t.Errorf("expected nil for case-insensitive match, got %v", err)
		}
	})
}

func TestFetchReleaseChecksums(t *testing.T) {
	t.Run("has checksums.txt", func(t *testing.T) {
		checksumContent := "abc123  file1.tar.gz\ndef456  file2.zip\n"

		mux := http.NewServeMux()
		mux.HandleFunc("/repos/owner/repo/releases/assets/42", func(w http.ResponseWriter, r *http.Request) {
			accept := r.Header.Get("Accept")
			if accept == "application/octet-stream" {
				w.Header().Set("Content-Type", "application/octet-stream")
				fmt.Fprint(w, checksumContent)
			} else {
				w.Header().Set("Content-Type", "application/json")
				_ = json.NewEncoder(w).Encode(&github.ReleaseAsset{
					ID:   github.Ptr(int64(42)),
					Name: github.Ptr("checksums.txt"),
				})
			}
		})

		server := httptest.NewServer(mux)
		defer server.Close()

		client := newTestClient(server.URL)
		release := &github.RepositoryRelease{
			Assets: []*github.ReleaseAsset{
				{
					ID:   github.Ptr(int64(42)),
					Name: github.Ptr("checksums.txt"),
				},
			},
		}

		checksums, err := FetchReleaseChecksums(client, "owner", "repo", release)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if checksums == nil {
			t.Fatal("expected non-nil checksums")
		}
		if checksums["file1.tar.gz"] != "abc123" {
			t.Errorf("file1.tar.gz: got %q, want %q", checksums["file1.tar.gz"], "abc123")
		}
	})

	t.Run("no checksums.txt", func(t *testing.T) {
		client := github.NewClient(nil)
		release := &github.RepositoryRelease{
			Assets: []*github.ReleaseAsset{
				{
					ID:   github.Ptr(int64(1)),
					Name: github.Ptr("other-file.tar.gz"),
				},
			},
		}

		checksums, err := FetchReleaseChecksums(client, "owner", "repo", release)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if checksums != nil {
			t.Errorf("expected nil checksums, got %v", checksums)
		}
	})

	t.Run("API error", func(t *testing.T) {
		mux := http.NewServeMux()
		mux.HandleFunc("/repos/owner/repo/releases/assets/42", func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		})

		server := httptest.NewServer(mux)
		defer server.Close()

		client := newTestClient(server.URL)
		release := &github.RepositoryRelease{
			Assets: []*github.ReleaseAsset{
				{
					ID:   github.Ptr(int64(42)),
					Name: github.Ptr("checksums.txt"),
				},
			},
		}

		checksums, err := FetchReleaseChecksums(client, "owner", "repo", release)
		if err == nil {
			t.Error("expected error for API failure")
		}
		if checksums != nil {
			t.Errorf("expected nil checksums on error, got %v", checksums)
		}
	})
}
