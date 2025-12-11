## v1.6.1
### fix: Revert project cmd, update prerelease, add pre-release gen
- fix: Revert `project` command
- chore: Add pre-release generation for every `release/**` branch
## v1.6.0
### feat: Add `project` command, rework compilation, update README
- feat: Add `project` command
- docs: Update README.md
- feat: Rework native compilation logic
#### seqra-jvm-sast
- Use dependencies from infra
- Update project model
- Consider project package
- Test project analyzer
- Update core
- AST-based column resolver
- Add dependencies version on CI
- Fix ci container
- Publish with dependencies image
- Publish workflow dispatch
- Dependencies image
- Fix tree any accessor
- Fix rule ids
- More on annotations & is-null 
- Spring annotation inheritance & minor fixes
- Update core
- Remove absolute paths from rule ids
- Rewrite taint mark name generation
- Minor fixes
- Add lambda captures resolution
- Add apache FilenameUtils
- Support join with taint rules
- Initial join rules support
#### seqra-jvm-autobuilder
- Login to ghcr with seqra token
- Use infra dependencies
- Support subdir copy
- Use push instead of load
- Dependencies docker
- Downgrade `logback` to be compatible with Java 8
- Add Maven executable search list
## v1.5.2
### fix: Bump version
## v1.5.1
### fix: Correct name/version and update SARIF parser
- fix: Fix name and semantic version
- chore: Update SARIF parser
## v1.5.0
### feat: Bump version
- chore: Remove binary
#### seqra-jvm-sast
- Fix spring controller rules
- Add inner paths of calls to path reporting
- Fix trace generation
- Taint spring controller args
- Support Spring cross-controller analysis
## v1.4.1
### fix: Update autobuilder, add docs for endpoint mapping, fix typo
- chore: Add spring boot endpoint extraction docs
- fix: Upd autobuilder
- chore: Fix typo
#### seqra-jvm-autobuilder
- fix: Add symlink resolving
## v1.4.0
### feat: Bump version
- feat: Bump version
#### seqra-jvm-sast
- Fix spring paths
- Support complex taint requires
## v1.3.0
### feat: Add `relatedLocations` and update README with blog link and demo
- feat: Add `relatedLocations`
- chore: Update README with new blog link and demo section
#### seqra-jvm-sast
- Safe load for semgrep yaml rules
- Fix exit sink bases
- Fix controller name
- Fix recursion in sarif traits
- Add spring controller info
- Don't reset heap alias on calls without heap access
#### seqra-rules
- chore: Fix `xss` rule description
## v1.2.0
### feat: Enable native scanning and update Discord link
- feat: Allow to scan in native environment
- chore: Update Discord link
#### seqra-jvm-sast
- Fix a bunch of automata generation issues
- Load default config from resources
- Handle loop-vars more correctly
- Better handling for loop-assign vars
- Publish analyzer jar
- Try to match taints to path starts
- Initial support for arrays
- Generate at least one trace for each entry point
- Enable alias analysis by default
- Annotate all rules with rule-info
- Fix signature patterns
#### seqra-rules
- feat: Add a rule for SQLI and some fixes
## v1.1.0
### feat: Add emoji for severity and require some options
- feat: Use individual emoji for each severity level
- chore: Add pre-commit
- fix: Require a couple of options and put the path to log at the end of the output
- chore(deps): Bump github.com/docker/docker from 28.2.2+incompatible to 28.3.3+incompatible
## v1.0.2
### fix: Bump version
- fix: Bump version
## v1.0.1
### fix: Bump version
- fix: Bump version
## v1.0.0
### feat: Publish release
- feat: Publish release
