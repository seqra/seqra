package example;

import base.RuleSample;
import base.RuleSet;

import java.util.Random;

@RuleSet("example/RuleWithAllowedConstant.yaml")
public abstract class RuleWithAllowedConstant implements RuleSample {
    String src() {
        return "tainted string " + generateString(new Random(), "abc", 3);
    }

    void sink(String data) {

    }

    final static class PositiveSimple extends RuleWithAllowedConstant {
        @Override
        public void entrypoint() {
            String data = src();
            sink(data);
        }
    }

    final static class PositiveWithEllipsis extends RuleWithAllowedConstant {
        @Override
        public void entrypoint() {
            String data = src();
            System.out.println(data);
            sink(data);
        }
    }

    final static class PositiveIterProc extends RuleWithAllowedConstant {
        @Override
        public void entrypoint() {
            String data = src();
            sinkWrapper(data);
        }

        void sinkWrapper(String data) {
            sink(data);
        }
    }

    final static class NegativeSimple extends RuleWithAllowedConstant {
        @Override
        public void entrypoint() {
            sink("Constant");
        }
    }

    static String generateString(Random rng, String characters, int length)
    {
        char[] text = new char[length];
        for (int i = 0; i < length; i++)
        {
            text[i] = characters.charAt(rng.nextInt(characters.length()));
        }
        return new String(text);
    }
}
