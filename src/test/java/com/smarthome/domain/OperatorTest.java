package com.smarthome.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OperatorTest {

    @Test
    void missingStateNeverMatches() {
        for (Operator op : Operator.values()) {
            assertFalse(op.apply(null, "1"), op + " must be false on missing state");
        }
    }

    @Test
    void negativeZeroEqualsZero() {
        assertTrue(Operator.EQ.apply("-0", "0"));
        assertTrue(Operator.EQ.apply("22.0", "22"));
    }

    @Test
    void nonNumbersAreNotParsedAsNumbers() {
        assertFalse(Operator.EQ.apply("22f", "22"));
        assertFalse(Operator.GT.apply("Infinity", "5"));
        assertFalse(Operator.GT.apply("NaN", "5"));
    }

    @Test
    void orderingOnNonNumericTextIsFalse() {
        assertFalse(Operator.GT.apply("LOCKED", "ARMED"));
        assertFalse(Operator.LT.apply("ARMED", "LOCKED"));
        assertFalse(Operator.GTE.apply("ON", "OFF"));
    }

    @Test
    void textEqualityIsCaseInsensitive() {
        assertTrue(Operator.EQ.apply("ON", "on"));
        assertTrue(Operator.NEQ.apply("ON", "OFF"));
    }
}