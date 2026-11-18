package com.fincore.platform.event;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private CorrelationIds() {
    }

    public static UUID resolve(String value) {
        if (value != null && value.length() == 36 && UUID_FORMAT.matcher(value).matches()) {
            return UUID.fromString(value);
        }
        return UUID.randomUUID();
    }
}
