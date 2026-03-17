package org.opentaint.ir.testing.cfg;

import java.math.BigDecimal;

public class MultiplyTests {

    private static int multiplyTests() {
        int failures = 0;

        BigDecimal[] bd1 = {
                new BigDecimal("123456789"),
        };

        BigDecimal[] bd2 = {
                new BigDecimal("987654321"),
        };

        // Two dimensonal array recording bd1[i] * bd2[j] &
        // 0 <= i <= 2 && 0 <= j <= 2;
        BigDecimal[][] expectedResults = {
                {new BigDecimal("121932631112635269"),
                },
                { new BigDecimal("1219326319027587258"),
                },
                { new BigDecimal("12193263197189452827"),
                }
        };

        for (int i = 0; i < bd1.length; i++) {
            for (int j = 0; j < bd2.length; j++) {
                if (!bd1[i].multiply(bd2[j]).equals(expectedResults[i][j])) {
                    failures++;
                }
            }
        }
        return failures;
    }

    public static Object test() {

        int failures = 0;

        failures += multiplyTests();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures +
                    " failures while testing multiply.");
        }
        System.out.println("OK");
        return null;
    }
}