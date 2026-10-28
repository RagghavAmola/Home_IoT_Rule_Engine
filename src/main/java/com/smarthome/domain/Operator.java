package com.smarthome.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public enum Operator {
    EQ,
    NEQ,
    GT,
    LT,
    GTE,
    LTE;

    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)");

    
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