package base;

/**
 * The rule semantics can't be represented as a set of taint rules
 * */
public @interface TaintRuleFalsePositive {
    String value();
}
