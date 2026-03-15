package globals

import "strings"

// ArtifactDef declaratively describes a downloadable artifact.
type ArtifactDef struct {
	Name        string // human-readable name ("Autobuilder", "Analyzer", "Rules")
	RepoName    string // GitHub repository name
	AssetName   string // GitHub release asset filename
	LibSubpath  string // path relative to lib/ directory
	CachePrefix string // cache filename prefix ("analyzer_")
	CacheSuffix string // cache filename suffix (".jar", "")
	BindVersion string // compile-time bind version
	Version     string // user-configured version
	Unpack      bool   // unpack tar.gz; also implies dir-based cache entry
}

// CacheName returns the cache filename/dirname for this artifact version.
func (a ArtifactDef) CacheName() string {
	return a.CachePrefix + a.Version + a.CacheSuffix
}

// Kind returns the lowercase artifact kind (e.g. "autobuilder").
func (a ArtifactDef) Kind() string {
	return strings.ToLower(a.Name)
}

// IsBindVersion reports whether the configured version matches the compile-time bind version.
func (a ArtifactDef) IsBindVersion() bool {
	return a.Version == a.BindVersion
}

// WithVersion returns a copy of the ArtifactDef with Version overridden.
func (a ArtifactDef) WithVersion(v string) ArtifactDef {
	a.Version = v
	return a
}

// Artifacts returns the list of artifact definitions, reading current globals and Config.
// This is a function (not a var) so it picks up mutable bind version vars and Config at call time.
func Artifacts() []ArtifactDef {
	return []ArtifactDef{
		{
			Name:        "Autobuilder",
			RepoName:    Config.Repo,
			AssetName:   AutobuilderAssetName,
			LibSubpath:  AutobuilderAssetName,
			CachePrefix: "autobuilder_",
			CacheSuffix: ".jar",
			BindVersion: AutobuilderBindVersion,
			Version:     Config.Autobuilder.Version,
		},
		{
			Name:        "Analyzer",
			RepoName:    Config.Repo,
			AssetName:   AnalyzerAssetName,
			LibSubpath:  AnalyzerAssetName,
			CachePrefix: "analyzer_",
			CacheSuffix: ".jar",
			BindVersion: AnalyzerBindVersion,
			Version:     Config.Analyzer.Version,
		},
		{
			Name:        "Rules",
			RepoName:    Config.Repo,
			AssetName:   RulesAssetName,
			LibSubpath:  "rules",
			CachePrefix: "rules_",
			CacheSuffix: "",
			BindVersion: RulesBindVersion,
			Version:     Config.Rules.Version,
			Unpack:      true,
		},
	}
}

// ArtifactByKind returns the ArtifactDef matching the given kind (lowercase name).
// Panics if the kind is not found.
func ArtifactByKind(kind string) ArtifactDef {
	for _, a := range Artifacts() {
		if a.Kind() == kind {
			return a
		}
	}
	panic("unknown artifact kind: " + kind)
}
