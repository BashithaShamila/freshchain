package com.freshchain.fulfillment.support;

import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.MDC;

/** Puts orderId in the logging context so one order can be grepped end to end. */
public final class OrderMdc {

    private static final String KEY = "orderId";

    public static <T> T with(UUID orderId, Supplier<T> action) {
        MDC.put(KEY, String.valueOf(orderId));
        try {
            return action.get();
        } finally {
            MDC.remove(KEY);
        }
    }

    public static void run(UUID orderId, Runnable action) {
        with(orderId, () -> {
            action.run();
            return null;
        });
    }

    private OrderMdc() {
    }
}
