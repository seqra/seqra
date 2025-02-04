# Module opentaint-ir-approximation

[Approximations] is used for overriding and extending existed classes by methods with their bodies.

Use [Approximate] annotation on class and point it to target class. 

```java
@Approximate(java.lang.Integer.class)
public class IntegerApprox {
    private final int value;

    public IntegerApprox(int value) {
        this.value = value;
    }

    public static IntegerApprox valueOf(int value) {
        return new IntegerApprox(value);
    }

    public int getValue() {
        return value;
    }
}
```

Let's assume code:

```kotlin
val db = opentaint-ir {
    installFeature(Approximations)
}
val cp = db.classpath(emptyList(), listOf(Approximations))
val clazz = cp.findClass("java.lang.Integer")
```

`clazz` object will represent mix between `java.lang.Integer` and `IntegerApprox`:
- static `valueOf` method will be from `IntegerApprox` and will reduce all complexity of java runtime
- `getValue` and `<init>` method will be also from `IntegerApprox`
- all other methods will be from `java.lang.Integer`

<!--- MODULE opentaint-ir-approximations -->
<!--- INDEX org.opentaint.ir.approximations -->

[Approximations]: https://opentaint-ir.org/docs/opentaint-ir-approximations/org.opentaint.ir.approximation/-approximations/index.html
[Approximate]: https://opentaint-ir.org/docs/opentaint-ir-approximations/org.opentaint.ir.approximation.annotation/-approximate/index.html