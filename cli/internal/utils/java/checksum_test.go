package java

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestFetchAdoptiumChecksum(t *testing.T) {
	t.Run("valid response", func(t *testing.T) {
		assets := []adoptiumAsset{
			{
				Binary: adoptiumBinary{
					Package:      adoptiumBinaryPackage{Checksum: "ABC123DEF456"},
					OS:           "linux",
					Architecture: "x64",
					ImageType:    "jdk",
				},
			},
		}

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(assets)
		}))
		defer server.Close()

		orig := adoptiumBaseURL
		adoptiumBaseURL = server.URL
		defer func() { adoptiumBaseURL = orig }()

		checksum, err := FetchAdoptiumChecksum(21, AdoptiumOSLinux, AdoptiumArchX64, AdoptiumImageJDK)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if checksum != "abc123def456" {
			t.Errorf("got %q, want %q", checksum, "abc123def456")
		}
	})

	t.Run("empty array", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode([]adoptiumAsset{})
		}))
		defer server.Close()

		orig := adoptiumBaseURL
		adoptiumBaseURL = server.URL
		defer func() { adoptiumBaseURL = orig }()

		checksum, err := FetchAdoptiumChecksum(21, AdoptiumOSLinux, AdoptiumArchX64, AdoptiumImageJDK)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if checksum != "" {
			t.Errorf("expected empty checksum, got %q", checksum)
		}
	})

	t.Run("HTTP 404", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusNotFound)
		}))
		defer server.Close()

		orig := adoptiumBaseURL
		adoptiumBaseURL = server.URL
		defer func() { adoptiumBaseURL = orig }()

		_, err := FetchAdoptiumChecksum(99, AdoptiumOSLinux, AdoptiumArchX64, AdoptiumImageJDK)
		if err == nil {
			t.Error("expected error for HTTP 404")
		}
	})
}
