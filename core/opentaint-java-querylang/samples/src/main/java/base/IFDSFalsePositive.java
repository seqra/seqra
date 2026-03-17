package base;

/**
 * The rule semantics can't be represented in IFDS framework (e.g. distributivity issues)
 * */
public @interface IFDSFalsePositive {
    String value();
}
