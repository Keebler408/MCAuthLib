package com.github.steveice10.mc.auth.util;

public class Sleep {
    public static void ms(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {
        }
    }
}
