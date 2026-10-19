/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.util;

import java.util.regex.Pattern;

public final class SecretRedaction {
    private static final Pattern KEY_VALUE = Pattern.compile("(?i)([\"']?(?:api[_-]?key|secret|password|passwd|token|authorization|bearer|client[_-]?secret|access[_-]?key|app[_-]?secret|private[_-]?key|model[_-]?key)[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._\\-]{12,}");
    private static final Pattern SK_TOKEN = Pattern.compile("(?i)(sk-[A-Za-z0-9_-]{8,})");
    private static final Pattern LONG_SECRET = Pattern.compile("([a-zA-Z0-9+/=_-]{20,})");

    private SecretRedaction() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = KEY_VALUE.matcher(text).replaceAll("$1***");
        s = BEARER.matcher(s).replaceAll("$1***");
        s = SK_TOKEN.matcher(s).replaceAll("***");
        s = LONG_SECRET.matcher(s).replaceAll(m -> {
            String g = m.group(1);
            if (g.length() >= 24 && g.matches(".*[a-zA-Z].*") && g.matches(".*[0-9].*")) {
                return g.substring(0, 4) + "\u2026" + g.substring(g.length() - 4);
            }
            return g;
        });
        return s;
    }
}

