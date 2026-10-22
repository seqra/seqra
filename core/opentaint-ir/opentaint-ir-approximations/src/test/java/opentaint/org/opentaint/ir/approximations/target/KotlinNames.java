package opentaint.org.opentaint.ir.approximations.target;

import org.opentaint.ir.approximation.annotation.ApproximatedFieldName;
import org.opentaint.ir.approximation.annotation.ApproximatedMethodName;

public class KotlinNames {
    @ApproximatedFieldName("field-with-dash")
    public int fieldWithDash;

    @ApproximatedMethodName("method-with-dash")
    public int methodWithDash(int value) {
        return value;
    }
}
