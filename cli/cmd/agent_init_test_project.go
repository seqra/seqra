package cmd

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/internal/testutil"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/spf13/cobra"
)

var initTestProjectDeps []string

var agentInitTestProjectCmd = &cobra.Command{
	Use:   "init-test-project <output-dir>",
	Short: "Bootstrap a rule test project with build.gradle.kts and test utility JAR",
	Long: `Creates a minimal Gradle project structure for testing OpenTaint rules.

The project includes:
  - build.gradle.kts with compile-only dependencies
  - settings.gradle.kts
  - libs/opentaint-sast-test-util.jar (provides @PositiveRuleSample and @NegativeRuleSample annotations)
  - src/main/java/test/ directory for test sample sources

Use --dependency to add Maven dependencies (e.g., servlet-api, Spring Web).`,
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		outputDir := args[0]

		// 1. Create directory structure
		dirs := []string{
			filepath.Join(outputDir, "libs"),
			filepath.Join(outputDir, "src", "main", "java", "test"),
		}
		for _, d := range dirs {
			if err := os.MkdirAll(d, 0o755); err != nil {
				out.Fatalf("Failed to create directory %s: %s", d, err)
			}
		}

		// 2. Resolve and copy opentaint-sast-test-util.jar
		testUtilJarSrc, err := resolveTestUtilJar()
		if err != nil {
			out.Fatalf("Failed to resolve test-util JAR: %s", err)
		}
		testUtilJarDst := filepath.Join(outputDir, "libs", "opentaint-sast-test-util.jar")
		if err := copyFile(testUtilJarSrc, testUtilJarDst); err != nil {
			out.Fatalf("Failed to copy test-util JAR: %s", err)
		}

		// 3. Generate build.gradle.kts
		if err := generateBuildGradle(outputDir, initTestProjectDeps); err != nil {
			out.Fatalf("Failed to generate build.gradle.kts: %s", err)
		}

		// 4. Generate settings.gradle.kts
		if err := generateSettingsGradle(outputDir); err != nil {
			out.Fatalf("Failed to generate settings.gradle.kts: %s", err)
		}

		fmt.Printf("Test project initialized at %s\n", outputDir)
	},
}

func init() {
	agentCmd.AddCommand(agentInitTestProjectCmd)
	agentInitTestProjectCmd.Flags().StringArrayVar(&initTestProjectDeps, "dependency", nil,
		"Maven dependency coordinates to add (e.g., 'javax.servlet:javax.servlet-api:4.0.1')")
}

// resolveTestUtilJar finds the opentaint-sast-test-util.jar.
// Resolution order:
//  1. Bundled path next to binary: <exe-dir>/lib/opentaint-sast-test-util.jar
//  2. Install path: ~/.opentaint/install/lib/opentaint-sast-test-util.jar
//  3. Dev build: <repo-root>/core/opentaint-sast-test-util/build/libs/opentaint-sast-test-util.jar
func resolveTestUtilJar() (string, error) {
	const jarName = "opentaint-sast-test-util.jar"

	// Tier 1: Bundled next to binary
	if libPath := utils.GetBundledLibPath(); libPath != "" {
		candidate := filepath.Join(libPath, jarName)
		if _, err := os.Stat(candidate); err == nil {
			return candidate, nil
		}
	}

	// Tier 2: Install path
	if libPath := utils.GetInstallLibPath(); libPath != "" {
		candidate := filepath.Join(libPath, jarName)
		if _, err := os.Stat(candidate); err == nil {
			return candidate, nil
		}
	}

	// Tier 3: Dev build — walk up from exe dir to find core/opentaint-sast-test-util/build/libs/
	if exe, err := os.Executable(); err == nil {
		exe, _ = filepath.EvalSymlinks(exe)
		// exe is typically at cli/bin/opentaint, so repo root is ../../
		dir := filepath.Dir(exe)
		for i := 0; i < 4; i++ {
			candidate := filepath.Join(dir, "core", "opentaint-sast-test-util", "build", "libs", jarName)
			if _, err := os.Stat(candidate); err == nil {
				return candidate, nil
			}
			dir = filepath.Dir(dir)
		}
	}

	// Tier 4: Extract from embedded binary
	if extracted, err := testutil.ExtractJar(); err == nil {
		return extracted, nil
	}

	return "", fmt.Errorf(
		"%s not found; build it with 'cd core && ./gradlew :opentaint-sast-test-util:jar' or reinstall opentaint",
		jarName,
	)
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return fmt.Errorf("open source: %w", err)
	}
	defer in.Close()

	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return fmt.Errorf("create parent dir: %w", err)
	}

	outFile, err := os.Create(dst)
	if err != nil {
		return fmt.Errorf("create destination: %w", err)
	}
	defer outFile.Close()

	if _, err := io.Copy(outFile, in); err != nil {
		return fmt.Errorf("copy: %w", err)
	}
	return nil
}

func generateBuildGradle(outputDir string, dependencies []string) error {
	var sb strings.Builder
	sb.WriteString(`plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/opentaint-sast-test-util.jar"))
`)
	for _, dep := range dependencies {
		sb.WriteString(fmt.Sprintf("    compileOnly(\"%s\")\n", dep))
	}
	sb.WriteString("}\n")

	path := filepath.Join(outputDir, "build.gradle.kts")
	return os.WriteFile(path, []byte(sb.String()), 0o644)
}

func generateSettingsGradle(outputDir string) error {
	content := `rootProject.name = "opentaint-rule-test"
`
	path := filepath.Join(outputDir, "settings.gradle.kts")
	return os.WriteFile(path, []byte(content), 0o644)
}
