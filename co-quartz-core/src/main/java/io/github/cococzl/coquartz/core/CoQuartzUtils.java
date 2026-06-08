package io.github.cococzl.coquartz.core;

public final class CoQuartzUtils {

    private CoQuartzUtils() {
    }

    public static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    public static String getStackTraceAsString(Throwable t) {
        if (t == null) return null;
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}