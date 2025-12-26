package semgrep

import (
	"os"
	"regexp"
	"strings"

	"github.com/sirupsen/logrus"
)

func GetSemgrepRuleId(seqraRuleId string) string {
	idStart := strings.LastIndex(seqraRuleId, ":")
	if idStart == -1 {
		logrus.Errorf("Can't convert to semgrep RuleId format. RuleId '%s' doesn't contain ':'", seqraRuleId)
		return seqraRuleId
	}
	rulePath := seqraRuleId[:idStart]
	justId := seqraRuleId[idStart+1:]
	re := regexp.MustCompile(`\s+`)
	cleanedRulePath := re.ReplaceAllString(rulePath, "")
	ruleDirs := strings.Split(cleanedRulePath, string(os.PathSeparator))
	if len(ruleDirs) > 0 {
		ruleDirs = ruleDirs[:len(ruleDirs)-1]
	}
	return strings.TrimLeft(strings.Join(ruleDirs, ".")+"."+justId, ".")
}
