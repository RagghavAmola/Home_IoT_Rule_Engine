package com.smarthome.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Relational comparison operators for evaluating device state values.
 * <p>
 * If both operands are plain decimal numbers, an exact numeric comparison is applied.
 * Otherwise only {@link #EQ} and {@link #NEQ} are meaningful, using case-insensitive
 * text comparison; ordering operators return false. Any comparison against a missing
 * (null) state value returns false, so unknown state never triggers a rule.
 * </p>
 */
public enum Operator {
    EQ,
    NEQ,
    GT,
    LT,
    GTE,
    LTE;

    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)");

    /**
     * Evaluates this operator comparing the actual telemetry value against the expected target.
     *
     * @param actual   the observed state value (null if the state is absent)
     * @param expected the expected value specified in the condition
     * @return true if the comparison holds, false otherwise (including when either side is null)
     */
    public boolean apply(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String act = actual.trim();
        String exp = expected.trim();

        if (isNumeric(act) && isNumeric(exp)) {
            return matches(new BigDecimal(act).compareTo(new BigDecimal(exp)));
        }
        if (this == EQ || this == NEQ) {
            return matches(act.compareToIgnoreCase(exp));
        }
        return false;
    }

    private boolean matches(int cmp) {
        return switch (this) {
            case EQ -> cmp == 0;
            case NEQ -> cmp != 0;
            case GT -> cmp > 0;
            case LT -> cmp < 0;
            case GTE -> cmp >= 0;
            case LTE -> cmp <= 0;
        };
    }

    private static boolean isNumeric(String s) {
        return NUMBER.matcher(s).matches();
    }
}