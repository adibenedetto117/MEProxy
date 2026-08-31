package com.jakeberryman.meproxy.content.bridge;

public final class BridgeGuard {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private BridgeGuard() {
    }

    public static boolean enter() {
        if (ACTIVE.get()) {
            return false;
        }
        ACTIVE.set(true);
        return true;
    }

    public static void exit() {
        ACTIVE.set(false);
    }
}
