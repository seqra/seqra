package semgrep

import (
	"os"
	"regexp"
	"strings"

	"github.com/sirupsen/logrus"
)

func GetRuleIdPathStart(userRulesPath string) string {
	workDir, err := os.Getwd()
	if err != nil {
		logrus.Error(err)
	}
	var ruleStart string
	if userRulesPath != "" {
		ruleStart = strings.TrimPrefix(userRulesPath, workDir)
	} else {
		ruleStart = ""
	}

	return strings.TrimSuffix(ruleStart, string(os.PathSeparator))
}

func GetSemgrepRuleId(seqraRuleId, absRulesPath, ruleStart string) string {
	newRuleId, isCut := strings.CutPrefix(seqraRuleId, absRulesPath)
	if isCut {
		newRuleId = ruleStart + newRuleId
	} else {
		logrus.Errorf("Can't convert to semgrep RuleId format. '%s' not start with waited path '%s'", seqraRuleId, absRulesPath)
		return seqraRuleId
	}
	idStart := strings.LastIndex(newRuleId, ":")
	if idStart == -1 {
		logrus.Errorf("Can't convert to semgrep RuleId format. RuleId '%s' doesn't contain ':'", seqraRuleId)
		return seqraRuleId
	}
	rulePath := newRuleId[:idStart]
	justId := newRuleId[idStart+1:]
	re := regexp.MustCompile(`\s+`)
	cleanedRulePath := re.ReplaceAllString(rulePath, "")
	ruleDirs := strings.Split(cleanedRulePath, string(os.PathSeparator))
	if len(ruleDirs) > 0 {
		ruleDirs = ruleDirs[:len(ruleDirs)-1]
	}
	return strings.TrimLeft(strings.Join(ruleDirs, ".")+"."+justId, ".")
}
