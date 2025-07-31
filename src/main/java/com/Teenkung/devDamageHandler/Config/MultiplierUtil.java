package com.Teenkung.devDamageHandler.Config;

import java.util.Collection;
import java.util.Locale;

public final class MultiplierUtil {

    private MultiplierUtil() {}

    public static double combine(Collection<Double> values, CombineMode mode) {
        if (values.isEmpty()) return 1.0;

        return switch (mode) {
            case PRODUCT -> {
                double out = 1.0;
                for (double v : values) out *= v;
                yield out;
            }
            case MIN -> {
                double min = Double.POSITIVE_INFINITY;
                for (double v : values) if (v < min) min = v;
                yield min == Double.POSITIVE_INFINITY ? 1.0 : min;
            }
            case MAX -> {
                double max = Double.NEGATIVE_INFINITY;
                for (double v : values) if (v > max) max = v;
                yield max == Double.NEGATIVE_INFINITY ? 1.0 : max;
            }
            case AVERAGE -> {
                double sum = 0; int n = 0;
                for (double v : values) { sum += v; n++; }
                yield n == 0 ? 1.0 : sum / n;
            }
            case SOFT_ADD -> {
                // Treat inputs as "kept fraction" (multiplier). Soft-union:
                // M_total = 1 - Π(1 - m_i)
                double invProd = 1.0;
                for (double m : values) invProd *= (1.0 - m);
                double result = 1.0 - invProd;
                // Clamp just in case
                yield Math.max(0, Math.min(1, result));
            }
            case RESIST_ADD -> {
                // Usually you'd pass "resists", but we'll mimic SOFT_ADD here unless you want different semantics.
                double invProd = 1.0;
                for (double m : values) invProd *= (1.0 - m);
                double result = 1.0 - invProd;
                yield Math.max(0, Math.min(1, result));
            }
        };
    }

    public static String fmt(double d) {
        return String.format(Locale.US, "%.3f", d);
    }
}
