package org.opentaint.ir.approximations.legacy;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.ir.approximations.target.LegacyTarget;

@Approximate(LegacyTarget.class)
public class LegacyTargetApprox {
    public int identity(int value) {
        return value;
    }
}
