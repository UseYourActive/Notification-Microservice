package bg.sit_varna.sit.si.constant;

public enum ErrorCategory {
    NOTIFICATION_SENDING("NOTIFICATION_SENDING"),
    TEMPLATE_PROCESSING("TEMPLATE_PROCESSING"),
    VALIDATION("VALIDATION"),
    CONFIGURATION("CONFIGURATION"),
    MESSAGING("MESSAGING"),
    RATE_LIMIT("RATE_LIMIT"),
    CONFLICT("CONFLICT"),
    SYSTEM("SYSTEM");

    private final String value;

    ErrorCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
