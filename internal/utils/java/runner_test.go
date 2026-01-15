package java

import (
	"os"
	"strings"
	"testing"
)

func TestUnsetJavaEnvironmentVariables(t *testing.T) {
	tests := []struct {
		name        string
		setupEnv    map[string]string
		expectedMsg []string
	}{
		{
			name: "unset_multiple_java_variables",
			setupEnv: map[string]string{
				"JAVA_HOME":        "/usr/lib/jvm/java-11",
				"JAVA_8_HOME":      "/usr/lib/jvm/java-8",
				"JAVA_17_HOME":     "/usr/lib/jvm/java-17",
				"JAVA_LATEST_HOME": "/usr/lib/jvm/java-latest",
				"NON_JAVA_VAR":     "should_remain",
			},
			expectedMsg: []string{
				"Unsetting JAVA_HOME",
				"Unsetting JAVA_8_HOME",
				"Unsetting JAVA_17_HOME",
				"Unsetting JAVA_LATEST_HOME",
			},
		},
		{
			name: "unset_partial_java_variables",
			setupEnv: map[string]string{
				"JAVA_HOME":    "/usr/lib/jvm/java-11",
				"JAVA_11_HOME": "/usr/lib/jvm/java-11",
				"OTHER_VAR":    "keep_this",
			},
			expectedMsg: []string{
				"Unsetting JAVA_HOME",
				"Unsetting JAVA_11_HOME",
			},
		},
		{
			name: "no_java_variables_set",
			setupEnv: map[string]string{
				"PATH": "/usr/bin",
				"HOME": "/home/user",
			},
			expectedMsg: []string{
				"JAVA_HOME not set",
				"JAVA_8_HOME not set",
				"JAVA_11_HOME not set",
				"JAVA_17_HOME not set",
				"JAVA_LATEST_HOME not set",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Save original environment
			origEnv := make(map[string]string)
			javaVars := []string{"JAVA_HOME", "JAVA_8_HOME", "JAVA_11_HOME", "JAVA_17_HOME", "JAVA_LATEST_HOME"}
			for _, v := range javaVars {
				if val := os.Getenv(v); val != "" {
					origEnv[v] = val
				}
			}

			// Clear Java environment variables first
			for _, v := range javaVars {
				_ = os.Unsetenv(v)
			}

			// Setup test environment
			for key, value := range tt.setupEnv {
				_ = os.Setenv(key, value)
			}

			// Call function under test
			unsetJavaEnvironmentVariables()

			// Verify Java variables are unset
			for _, v := range javaVars {
				if val := os.Getenv(v); val != "" {
					t.Errorf("Expected %s to be unset, but found value: %s", v, val)
				}
			}

			// Verify non-Java variables remain
			for key, expectedValue := range tt.setupEnv {
				if !strings.HasPrefix(key, "JAVA_") {
					if actual := os.Getenv(key); actual != expectedValue {
						t.Errorf("Expected %s=%s to remain, but got %s", key, expectedValue, actual)
					}
				}
			}

			// Cleanup: restore original environment
			for _, v := range javaVars {
				_ = os.Unsetenv(v)
				if val, exists := origEnv[v]; exists {
					_ = os.Setenv(v, val)
				}
			}
			for key := range tt.setupEnv {
				if !strings.HasPrefix(key, "JAVA_") {
					_ = os.Unsetenv(key)
				}
			}
		})
	}
}

func TestGetCleanEnvironment(t *testing.T) {
	tests := []struct {
		name          string
		setupEnv      map[string]string
		shouldExclude []string
		shouldInclude []string
	}{
		{
			name: "exclude_all_java_variables",
			setupEnv: map[string]string{
				"JAVA_HOME":        "/usr/lib/jvm/java-11",
				"JAVA_8_HOME":      "/usr/lib/jvm/java-8",
				"JAVA_11_HOME":     "/usr/lib/jvm/java-11",
				"JAVA_17_HOME":     "/usr/lib/jvm/java-17",
				"JAVA_LATEST_HOME": "/usr/lib/jvm/java-latest",
				"PATH":             "/usr/bin:/bin",
				"HOME":             "/home/user",
				"USER":             "testuser",
			},
			shouldExclude: []string{"JAVA_HOME", "JAVA_8_HOME", "JAVA_11_HOME", "JAVA_17_HOME", "JAVA_LATEST_HOME"},
			shouldInclude: []string{"PATH", "HOME", "USER"},
		},
		{
			name: "exclude_partial_java_variables",
			setupEnv: map[string]string{
				"JAVA_HOME":   "/usr/lib/jvm/java-11",
				"JAVA_8_HOME": "/usr/lib/jvm/java-8",
				"PATH":        "/usr/bin:/bin",
				"SHELL":       "/bin/bash",
			},
			shouldExclude: []string{"JAVA_HOME", "JAVA_8_HOME"},
			shouldInclude: []string{"PATH", "SHELL"},
		},
		{
			name: "no_java_variables_in_environment",
			setupEnv: map[string]string{
				"PATH":  "/usr/bin:/bin",
				"HOME":  "/home/user",
				"SHELL": "/bin/bash",
			},
			shouldExclude: []string{},
			shouldInclude: []string{"PATH", "HOME", "SHELL"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Save original environment
			origEnv := os.Environ()

			// Clear environment
			os.Clearenv()

			// Setup test environment
			for key, value := range tt.setupEnv {
				_ = os.Setenv(key, value)
			}

			// Create runner and get clean environment
			runner := &javaRunner{}
			cleanEnv := runner.getCleanEnvironment()

			// Convert to map for easier testing
			envMap := make(map[string]string)
			for _, env := range cleanEnv {
				parts := strings.SplitN(env, "=", 2)
				if len(parts) == 2 {
					envMap[parts[0]] = parts[1]
				}
			}

			// Verify excluded variables are not present
			for _, excludedVar := range tt.shouldExclude {
				if _, exists := envMap[excludedVar]; exists {
					t.Errorf("Expected %s to be excluded from clean environment, but it was present", excludedVar)
				}
			}

			// Verify included variables are present with correct values
			for _, includedVar := range tt.shouldInclude {
				expectedValue, expectedExists := tt.setupEnv[includedVar]
				actualValue, actualExists := envMap[includedVar]

				if !expectedExists {
					continue // Skip if variable wasn't set in test
				}

				if !actualExists {
					t.Errorf("Expected %s to be included in clean environment, but it was missing", includedVar)
				} else if actualValue != expectedValue {
					t.Errorf("Expected %s=%s in clean environment, but got %s=%s", includedVar, expectedValue, includedVar, actualValue)
				}
			}

			// Restore original environment
			os.Clearenv()
			for _, env := range origEnv {
				parts := strings.SplitN(env, "=", 2)
				if len(parts) == 2 {
					_ = os.Setenv(parts[0], parts[1])
				}
			}
		})
	}
}

func TestNewJavaRunner(t *testing.T) {
	runner := NewJavaRunner()

	// Type assertion to access internal fields
	jr, ok := runner.(*javaRunner)
	if !ok {
		t.Fatal("Expected *javaRunner type")
	}

	if jr.trySystemStrategy {
		t.Error("Expected trySystemStrategy to be false by default")
	}

	if jr.specificStrategy != nil {
		t.Error("Expected specificStrategy to be nil by default")
	}
}

func TestJavaRunner_TrySystem(t *testing.T) {
	runner := NewJavaRunner()
	runner = runner.TrySystem()

	jr, ok := runner.(*javaRunner)
	if !ok {
		t.Fatal("Expected *javaRunner type")
	}

	if !jr.trySystemStrategy {
		t.Error("Expected trySystemStrategy to be true after TrySystem()")
	}
}

func TestJavaRunner_TrySpecificVersion(t *testing.T) {
	tests := []struct {
		name    string
		version int
	}{
		{"java_8", 8},
		{"java_11", 11},
		{"java_17", 17},
		{"java_21", 21},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			runner := NewJavaRunner()
			runner = runner.TrySpecificVersion(tt.version)

			jr, ok := runner.(*javaRunner)
			if !ok {
				t.Fatal("Expected *javaRunner type")
			}

			if jr.specificStrategy == nil {
				t.Error("Expected specificStrategy to be set after TrySpecificVersion()")
			} else if *jr.specificStrategy != tt.version {
				t.Errorf("Expected specificStrategy to be %d, got %d", tt.version, *jr.specificStrategy)
			}
		})
	}
}

func TestJavaRunner_GetJavaResolutions_NoStrategy(t *testing.T) {
	runner := NewJavaRunner()
	resolutions := runner.GetJavaResolutions()

	if len(resolutions) != 1 {
		t.Errorf("Expected 1 resolution for no strategy, got %d", len(resolutions))
	}

	// Test that the resolution returns an error
	_, _, err := resolutions[0]()
	if err == nil {
		t.Error("Expected error when no strategy is configured")
	}
	if !strings.Contains(err.Error(), "no Java resolution strategies configured") {
		t.Errorf("Expected specific error message, got: %s", err.Error())
	}
}

func TestJavaRunner_GetJavaResolutions_SystemStrategy(t *testing.T) {
	runner := NewJavaRunner().TrySystem()
	resolutions := runner.GetJavaResolutions()

	if len(resolutions) != 1 {
		t.Errorf("Expected 1 resolution for system strategy, got %d", len(resolutions))
	}

	// Note: We can't easily test the actual resolution without mocking the system detection
	// This test verifies the strategy is set up correctly
}

func TestJavaRunner_GetJavaResolutions_SpecificStrategy(t *testing.T) {
	runner := NewJavaRunner().TrySpecificVersion(11)
	resolutions := runner.GetJavaResolutions()

	if len(resolutions) != 1 {
		t.Errorf("Expected 1 resolution for specific strategy, got %d", len(resolutions))
	}
}

func TestJavaRunner_GetJavaResolutions_BothStrategies(t *testing.T) {
	runner := NewJavaRunner().TrySystem().TrySpecificVersion(11)
	resolutions := runner.GetJavaResolutions()

	if len(resolutions) != 1 {
		t.Errorf("Expected 1 resolution for fallback strategy, got %d", len(resolutions))
	}
}

func TestJavaRunner_ExecuteJavaCommand_NoArgs(t *testing.T) {
	runner := NewJavaRunner()

	err := runner.ExecuteJavaCommand([]string{}, func(error) bool { return true })

	if err == nil {
		t.Error("Expected error when no arguments provided")
	}
	if !strings.Contains(err.Error(), "no Java command arguments provided") {
		t.Errorf("Expected specific error message, got: %s", err.Error())
	}
}

func TestEnvironmentVariableList(t *testing.T) {
	// Test that we're tracking the correct Java environment variables
	expectedVars := []string{
		"JAVA_HOME",
		"JAVA_8_HOME",
		"JAVA_11_HOME",
		"JAVA_17_HOME",
		"JAVA_LATEST_HOME",
	}

	// This test ensures consistency between unsetJavaEnvironmentVariables
	// and getCleanEnvironment methods
	runner := &javaRunner{}

	// Setup environment with all Java variables
	for _, v := range expectedVars {
		_ = os.Setenv(v, "/test/path")
	}
	_ = os.Setenv("NON_JAVA", "keep")

	cleanEnv := runner.getCleanEnvironment()

	// Check that Java variables are excluded
	envMap := make(map[string]string)
	for _, env := range cleanEnv {
		parts := strings.SplitN(env, "=", 2)
		if len(parts) == 2 {
			envMap[parts[0]] = parts[1]
		}
	}

	for _, javaVar := range expectedVars {
		if _, exists := envMap[javaVar]; exists {
			t.Errorf("Java variable %s should be excluded from clean environment", javaVar)
		}
	}

	if _, exists := envMap["NON_JAVA"]; !exists {
		t.Error("Non-Java variable should be included in clean environment")
	}

	// Cleanup
	for _, v := range expectedVars {
		_ = os.Unsetenv(v)
	}
	_ = os.Unsetenv("NON_JAVA")
}
