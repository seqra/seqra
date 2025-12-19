package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i93.Connection;
import issues.i93.Statement;

@RuleSet("issues/issue93.yaml")
public abstract class issue93 implements RuleSample {
    static class NegativeTaint extends issue93 {
        @Override
        public void entrypoint() {
            Connection c = new Connection();
            String smth = c.getString(0);
            String sql = "hello more " + smth + " please";
            Statement s = new Statement();
            s.executeQuery();
        }
    }

    static class PositiveTaint extends issue93 {
        @Override
        public void entrypoint() {
            Connection c = new Connection();
            String smth = c.getString(0);
            String sql = "hello more " + smth + " please";
            Statement s = new Statement();
            s.executeQuery(sql);
        }
    }
}
