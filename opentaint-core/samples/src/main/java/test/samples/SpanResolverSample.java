package test.samples;

public class SpanResolverSample {
    String value;
    private String field;
    private int[] array = new int[10];

    public String simpleMethodCall() {
        return /*simpleCall:start*/value.toUpperCase()/*simpleCall:end*/;
    }

    public void assignmentWithCall() {
        /*s1:start*/String result = getValue()/*s1:end*/;
        System.out.println(result);
    }

    public /*methodEntry:start*/String getValue()/*methodEntry:end*/ {
        return field;
    /*methodExit:start*/}/*methodExit:end*/

    public void fieldAccess() {
        /*fieldRead:start*/String value = this.field/*fieldRead:end*/;
        bh(value);
        /*fieldWrite:start*/this.field/*fieldWrite:end*/ = "new value";
    }

    public int arrayAccess() {
        /*arrayRead:start*/int value = array[0]/*arrayRead:end*/;
        /*arrayWrite:start*/array[1]/*arrayWrite:end*/ = 42;
        return value;
    }

    public SpanResolverSample createObject() {
        /*newObject:start*/SpanResolverSample obj = new SpanResolverSample()/*newObject:end*/;
        return obj;
    }

    public int returnStatement() {
        /*return:start*/return 42/*return:end*/;
    }

    public void multipleStatementsOnLine() {
        int a = 1; int b = 2; /*multi:start*/int c = a + b;/*multi:end*/
        bh(c);
    }

    public void chainedCall() {
        /*chainedCall:start*/String s = value.trim().toUpperCase()/*chainedCall:end*/;
        bh(s);
    }

    public void localVariableDeclaration() {
        /*localVar:start*/String local = "value"/*localVar:end*/;
        int number = 123;
        bh(local);
        bh(number);
    }

    private void bh(Object o) {

    }

    public /*full$entry:start*/String fullMethodTest(String input)/*full$entry:end*/ {
        /*full$local:start*/String trimmed = input.trim()/*full$local:end*/;
        /*full$fieldWrite:start*/this.field/*full$fieldWrite:end*/ = trimmed;
        /*full$fieldRead:start*/String current = this.field/*full$fieldRead:end*/;
        /*full$call:start*/String upper = current.toUpperCase()/*full$call:end*/;
    /*full$return:start*/return upper/*full$return:end*/;
    /*full$exit:start*/}/*full$exit:end*/

    // Method call with 0 arguments
    public void methodCallNoArgs() {
        /*callNoArgs:start*/noArgsHelper()/*callNoArgs:end*/;
    }

    // Method call with 1 argument
    public void methodCallOneArg() {
        /*callOneArg:start*/oneArgHelper("value")/*callOneArg:end*/;
    }

    // Method call with 2 arguments
    public void methodCallTwoArgs() {
        /*callTwoArgs:start*/twoArgsHelper("a", "b")/*callTwoArgs:end*/;
    }

    // Method call with 3 arguments
    public void methodCallThreeArgs() {
        /*callThreeArgs:start*/threeArgsHelper("a", "b", "c")/*callThreeArgs:end*/;
    }

    // Method call with varargs
    public void methodCallVarargs() {
        /*callVarargs:start*/varargsHelper("a", "b", "c", "d")/*callVarargs:end*/;
    }

    private void noArgsHelper() {}
    private void oneArgHelper(String a) {}
    private void twoArgsHelper(String a, String b) {}
    private void threeArgsHelper(String a, String b, String c) {}
    private void varargsHelper(String... args) {}

    // Method calls on local variable (a.foo(...))
    public void localVarCallNoArgs() {
        String s = "test";
        /*localVarCallNoArgs:start*/int len = s.length()/*localVarCallNoArgs:end*/;
        bh(len);
    }

    public void localVarCallOneArg() {
        String s = "test";
        /*localVarCallOneArg:start*/s.charAt(0)/*localVarCallOneArg:end*/;
    }

    public void localVarCallTwoArgs() {
        String s = "test";
        /*localVarCallTwoArgs:start*/s.substring(0, 2)/*localVarCallTwoArgs:end*/;
    }

    public void localVarCallThreeArgs() {
        String s = "test value here";
        /*localVarCallThreeArgs:start*/s.replace("test", "new")/*localVarCallThreeArgs:end*/;
    }

    // Method calls on method result (getX().foo(...))
    public void chainedCallNoArgs() {
        /*chainedCallNoArgs:start*/int len = getString().length()/*chainedCallNoArgs:end*/;
        bh(len);
    }

    public void chainedCallOneArg() {
        /*chainedCallOneArg:start*/getString().charAt(0)/*chainedCallOneArg:end*/;
    }

    public void chainedCallTwoArgs() {
        /*chainedCallTwoArgs:start*/getString().substring(0, 2)/*chainedCallTwoArgs:end*/;
    }

    public void chainedCallThreeArgs() {
        /*chainedCallThreeArgs:start*/getString().replace("a", "b")/*chainedCallThreeArgs:end*/;
    }

    private String getString() { return "test"; }
    private String getFormatString() { return "%s %s %s"; }
}
