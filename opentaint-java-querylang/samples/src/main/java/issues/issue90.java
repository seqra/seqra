package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i90.CookieEater;
import issues.i90.CookieMock;

@RuleSet("issues/issue90.yaml")
public abstract class issue90 implements RuleSample {
    static class PositiveCookie extends issue90 {
        @Override
        public void entrypoint() {
            CookieMock cookie = new CookieMock();
            CookieEater eater = new CookieEater();
            eater.addCookie(cookie);
        }
    }

    static class NegativeCookie extends issue90 {
        @Override
        public void entrypoint() {
            CookieMock cookie = new CookieMock();
            cookie.setSecure(true);
            CookieEater eater = new CookieEater();
            eater.addCookie(cookie);
        }
    }
}
