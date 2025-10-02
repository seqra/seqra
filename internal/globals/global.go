package globals

import (
	"time"
)

const GithubDockerHost = "ghcr.io"

const RepoOwner = "seqra"

const AnalyzerDocker = GithubDockerHost + "/" + RepoOwner + "/seqra-jvm-sast/sast-analyzer"
const AnalyzerBindVersion = "2025.10.02.eb0ff77"

const AutobuilderRepoName = "seqra-jvm-autobuilder"
const AutobuilderDocker = GithubDockerHost + "/" + RepoOwner + "/" + AutobuilderRepoName + "/sast-autobuilder"
const AutobuilderBindVersion = "2025.10.01.da82029"
const AutobuilderAssetName = "seqra-project-auto-builder.jar"

const RulesRepoName = "seqra-rules"
const RulesBindVersion = "v1.0.0"

type Compile struct {
	Type string `mapstructure:"type"`
}

type Scan struct {
	Timeout time.Duration `mapstructure:"timeout"`
	Ruleset string        `mapstructure:"ruleset"`
}

type Log struct {
	Verbosity string `mapstructure:"verbosity"`
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

type ConfigType struct {
	Scan        Scan        `mapstructure:"scan"`
	Log         Log         `mapstructure:"log"`
	Github      Github      `mapstructure:"github"`
	Analyzer    Analyzer    `mapstructure:"analyzer"`
	Autobuilder Autobuilder `mapstructure:"autobuilder"`
	Compile     Compile     `mapstructure:"compile"`
	Quiet       bool        `mapstructure:"quiet"`
}

var Config ConfigType

var LogPath string

var ConfigFile string
