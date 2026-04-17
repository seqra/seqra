package globals

import (
	_ "embed"
	"fmt"
	"time"

	"gopkg.in/yaml.v2"
)

//go:embed versions.yaml
var versionsYAML []byte

type versions struct {
	Analyzer    string `yaml:"analyzer"`
	Autobuilder string `yaml:"autobuilder"`
	Rules       string `yaml:"rules"`
	Java        int    `yaml:"java"`
}

var BindVersions = func() versions {
	var v versions
	if err := yaml.Unmarshal(versionsYAML, &v); err != nil {
		panic(fmt.Sprintf("failed to parse embedded versions.yaml: %v", err))
	}
	return v
}()

var (
	AnalyzerBindVersion    = BindVersions.Analyzer
	AutobuilderBindVersion = BindVersions.Autobuilder
	RulesBindVersion       = BindVersions.Rules
	DefaultJavaVersion     = BindVersions.Java
)

const RepoOwner = "seqra"
const RepoName = "opentaint"

const AutobuilderAssetName = "opentaint-project-auto-builder.jar"
const AnalyzerAssetName = "opentaint-project-analyzer.jar"
const RulesAssetName = "opentaint-rules.tar.gz"

type Scan struct {
	Timeout       time.Duration `mapstructure:"timeout"`
	MaxMemory     string        `mapstructure:"max_memory"`
	CodeFlowLimit int64         `mapstructure:"code_flow_limit"`
}

type Output struct {
	Debug bool   `mapstructure:"debug"`
	Color string `mapstructure:"color"`
	Quiet bool   `mapstructure:"quiet"`
}

type Github struct {
	Token string `mapstructure:"token"`
}

type Analyzer struct {
	Version string `mapstructure:"version"`
}

type Autobuilder struct {
	Version string `mapstructure:"version"`
}

type Rules struct {
	Version string `mapstructure:"version"`
}

type Java struct {
	Version int `mapstructure:"version"`
}

type ConfigType struct {
	Scan   Scan   `mapstructure:"scan"`
	Output Output `mapstructure:"output"`

	Github      Github      `mapstructure:"github"`
	Analyzer    Analyzer    `mapstructure:"analyzer"`
	Autobuilder Autobuilder `mapstructure:"autobuilder"`
	Rules       Rules       `mapstructure:"rules"`
	Java        Java        `mapstructure:"java"`
	Owner       string      `mapstructure:"owner"`
	Repo        string      `mapstructure:"repo"`
	SkipVerify  bool        `mapstructure:"skip-verify"`
}

var Config ConfigType

var LogPath string

var ConfigFile string

// GetVersionsYAML returns the raw embedded versions.yaml content.
func GetVersionsYAML() []byte {
	return versionsYAML
}
