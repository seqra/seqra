package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/log"
	"github.com/seqra/opentaint/internal/version"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
	"github.com/spf13/viper"
)

const experimentalFlagName = "experimental"

var (
	toolVersion      bool
	experimentalMode bool
)

// out is the global output printer used by all commands for user-facing output.
// It is configured in PersistentPreRunE after logging is set up.
var out = output.New()

// updateHintCh receives an update hint message if a new version is available.
var updateHintCh = make(chan string, 1)

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Use:           "opentaint",
	Short:         "OpenTaint Analyzer",
	Long:          `OpenTaint is a CLI tool that analyzes Java and Kotlin projects to find vulnerabilities`,
	SilenceErrors: true,
	SilenceUsage:  true,

	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		applyExperimentalFlagVisibility(cmd.Root(), experimentalMode)

		if err := log.SetUpLogs(); err != nil {
			return fmt.Errorf("failed to set up logging: %w", err)
		}

		// Configure the output printer.
		out.Configure(globals.Config.Output.Color, globals.Config.Output.Quiet)
		out.SetDebug(globals.Config.Output.Debug)

		// Reconcile install-tier version marker if needed (lightweight: a few Stat calls).
		utils.ReconcileInstallMarker()

		// Start async update check (non-blocking, at most once per day)
		if !globals.Config.Output.Quiet {
			go checkForUpdateAsync()
		}

		return nil
	},
	PersistentPostRun: func(cmd *cobra.Command, args []string) {
		// Print update hint if available
		select {
		case hint := <-updateHintCh:
			out.Blank()
			out.Print(hint)
		default:
		}
	},
	Run: func(cmd *cobra.Command, args []string) {
		if toolVersion {
			fmt.Printf("opentaint version %s\n", version.GetVersion())
		} else {
			_ = cmd.Help()
		}
	},
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %s\n", output.Humanize(err))
		fmt.Fprintln(os.Stderr, "Run 'opentaint --help' for usage.")
		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(initConfig)
	configureExperimentalFlagVisibility()

	// Here you will define your flags and configuration settings.
	// Cobra supports persistent flags, which, if defined here,
	// will be global for your application.

	rootCmd.PersistentFlags().StringVar(&globals.ConfigFile, "config", "", "Path to a config file")
	rootCmd.PersistentFlags().BoolVar(&experimentalMode, experimentalFlagName, false, "Show experimental and hidden flags")
	_ = rootCmd.PersistentFlags().MarkHidden(experimentalFlagName)

	rootCmd.Flags().BoolVarP(&toolVersion, "version", "v", false, "Print the version information")

	rootCmd.PersistentFlags().BoolVarP(&globals.Config.Output.Debug, "debug", "d", false, "Enable debug output (stream JAR subprocess output, show debug-only fields)")
	_ = viper.BindPFlag("output.debug", rootCmd.PersistentFlags().Lookup("debug"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Output.Color, "color", "auto", "Color mode (auto, always, never)")
	_ = viper.BindPFlag("output.color", rootCmd.PersistentFlags().Lookup("color"))

	rootCmd.PersistentFlags().BoolVarP(&globals.Config.Output.Quiet, "quiet", "q", false, "Suppress interactive UI elements (spinners, progress bars) and JAR streaming")
	_ = viper.BindPFlag("output.quiet", rootCmd.PersistentFlags().Lookup("quiet"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Analyzer.Version, "analyzer-version", globals.AnalyzerBindVersion, "Version of opentaint analyzer")
	_ = rootCmd.PersistentFlags().MarkHidden("analyzer-version")
	_ = viper.BindPFlag("analyzer.version", rootCmd.PersistentFlags().Lookup("analyzer-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Autobuilder.Version, "autobuilder-version", globals.AutobuilderBindVersion, "Version of opentaint autobuilder")
	_ = rootCmd.PersistentFlags().MarkHidden("autobuilder-version")
	_ = viper.BindPFlag("autobuilder.version", rootCmd.PersistentFlags().Lookup("autobuilder-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Rules.Version, "rules-version", globals.RulesBindVersion, "Version of opentaint rules")
	_ = rootCmd.PersistentFlags().MarkHidden("rules-version")
	_ = viper.BindPFlag("rules.version", rootCmd.PersistentFlags().Lookup("rules-version"))

	rootCmd.PersistentFlags().IntVar(&globals.Config.Java.Version, "java-version", globals.DefaultJavaVersion, "Java version to use for running analyzer")
	_ = viper.BindPFlag("java.version", rootCmd.PersistentFlags().Lookup("java-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Github.Token, "github-token", "", "Token for docker image pull from ghcr.io")
	_ = rootCmd.PersistentFlags().MarkHidden("github-token")
	_ = viper.BindPFlag("github.token", rootCmd.PersistentFlags().Lookup("github-token"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Owner, "owner", globals.RepoOwner, "Organization owner for GitHub repositories")
	_ = rootCmd.PersistentFlags().MarkHidden("owner")
	_ = viper.BindPFlag("owner", rootCmd.PersistentFlags().Lookup("owner"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Repo, "repo", globals.RepoName, "GitHub repository name")
	_ = rootCmd.PersistentFlags().MarkHidden("repo")
	_ = viper.BindPFlag("repo", rootCmd.PersistentFlags().Lookup("repo"))

	rootCmd.PersistentFlags().BoolVar(&globals.Config.SkipVerify, "skip-verify", false, "Skip SHA256 checksum verification of downloaded artifacts")
	_ = viper.BindPFlag("skip-verify", rootCmd.PersistentFlags().Lookup("skip-verify"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Analyzer.JarPath, "analyzer-jar", "", "Path to analyzer JAR (dev override, skips download)")
	_ = rootCmd.PersistentFlags().MarkHidden("analyzer-jar")
	_ = viper.BindPFlag("analyzer.jar_path", rootCmd.PersistentFlags().Lookup("analyzer-jar"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Autobuilder.JarPath, "autobuilder-jar", "", "Path to autobuilder JAR (dev override, skips download)")
	_ = rootCmd.PersistentFlags().MarkHidden("autobuilder-jar")
	_ = viper.BindPFlag("autobuilder.jar_path", rootCmd.PersistentFlags().Lookup("autobuilder-jar"))
}

// initConfig reads in config file and ENV variables if set.
func initConfig() {
	if globals.ConfigFile != "" {
		// Use config file from the flag.
		viper.SetConfigFile(globals.ConfigFile)
	}

	viper.SetEnvPrefix("opentaint")
	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	viper.AutomaticEnv() // read in environment variables that match

	_ = viper.ReadInConfig()
	_ = viper.Unmarshal(&globals.Config)
}

// hasNestedKey reports whether a dotted key path is present in a viper settings map.
// Each path segment must resolve to a non-nil value; intermediate segments must be maps.
func hasNestedKey(m map[string]any, parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	v, ok := m[parts[0]]
	if !ok {
		return false
	}
	if len(parts) == 1 {
		return true
	}
	nested, ok := v.(map[string]any)
	if !ok {
		return false
	}
	return hasNestedKey(nested, parts[1:])
}

// addConfigFields appends config fields to a SectionBuilder if PrintConfig annotation is set.
func addConfigFields(cmd *cobra.Command, sb *output.SectionBuilder) {
	if cmd.Annotations != nil && cmd.Annotations["PrintConfig"] == "true" {
		if globals.Config.Output.Debug {
			sb.Field("Log level", "debug")
			if viper.ConfigFileUsed() != "" {
				sb.Field("Config file", viper.ConfigFileUsed())
			}
			if globals.LogPath != "" {
				sb.Field("Log file", globals.LogPath)
			}
		}
	}
}

func configureExperimentalFlagVisibility() {
	defaultHelpFunc := rootCmd.HelpFunc()
	defaultUsageFunc := rootCmd.UsageFunc()

	rootCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		applyExperimentalFlagVisibility(cmd.Root(), experimentalMode)
		defaultHelpFunc(cmd, args)
	})
	rootCmd.SetUsageFunc(func(cmd *cobra.Command) error {
		applyExperimentalFlagVisibility(cmd.Root(), experimentalMode)
		return defaultUsageFunc(cmd)
	})
}

func applyExperimentalFlagVisibility(root *cobra.Command, enabled bool) {
	if !enabled || root == nil {
		return
	}

	visitCommandTree(root, func(cmd *cobra.Command) {
		setFlagSetHidden(cmd.LocalFlags(), false)
		setFlagSetHidden(cmd.PersistentFlags(), false)
	})
}

func visitCommandTree(root *cobra.Command, visit func(*cobra.Command)) {
	if root == nil {
		return
	}

	visit(root)
	for _, child := range root.Commands() {
		visitCommandTree(child, visit)
	}
}

func setFlagSetHidden(flags *pflag.FlagSet, hidden bool) {
	if flags == nil {
		return
	}

	flags.VisitAll(func(flag *pflag.Flag) {
		flag.Hidden = hidden
	})
}

// checkForUpdateAsync checks for a newer version in the background, throttled to once per day.
func checkForUpdateAsync() {
	currentVersion := version.GetVersion()
	if currentVersion == "dev" || strings.HasPrefix(currentVersion, "dev-") {
		return
	}

	opentaintHome, err := utils.GetOpenTaintHome()
	if err != nil {
		return
	}

	cacheFile := filepath.Join(opentaintHome, ".last-update-check")

	// Check if we've already checked today
	if info, err := os.Stat(cacheFile); err == nil {
		if time.Since(info.ModTime()) < 24*time.Hour {
			// Read cached version
			data, err := os.ReadFile(cacheFile)
			if err == nil {
				latestVersion := strings.TrimSpace(string(data))
				if latestVersion != "" {
					cmp, err := version.CompareVersions(currentVersion, latestVersion)
					if err == nil && cmp < 0 {
						updateHintCh <- utils.UpdateHint(latestVersion)
					}
				}
			}
			return
		}
	}

	// Fetch latest release
	latestVersion, _, err := utils.GetLatestRelease(globals.Config.Owner, globals.Config.Repo, globals.Config.Github.Token)
	if err != nil {
		return
	}

	// Cache the result
	_ = os.WriteFile(cacheFile, []byte(latestVersion), 0o644)

	cmp, err := version.CompareVersions(currentVersion, latestVersion)
	if err == nil && cmp < 0 {
		updateHintCh <- utils.UpdateHint(latestVersion)
	}
}
