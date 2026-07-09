package bg.sit_varna.sit.si.constant;

import jakarta.ws.rs.core.Response;

/**
 * Error codes for notification service
 * Each error code contains:
 * - code: Unique error identifier (e.g., "NOTIF_001")
 * - messageKey: Key for i18n message lookup
 * - titleKey: Key for i18n title lookup
 * - status: HTTP status code to return
 */
public enum NotificationErrorCode {

    // Email errors (NOTIF_001 - NOTIF_010)
    EMAIL_SEND_FAILED(
            "NOTIF_001",
            "error.notification.email.send-failed",
            "error.title.email-failed",
            Response.Status.BAD_GATEWAY // 502 - External service (SMTP) failed
    ),
    EMAIL_INVALID_RECIPIENT(
            "NOTIF_002",
            "error.notification.email.invalid-recipient",
            "error.title.invalid-data",
            Response.Status.BAD_REQUEST
    ),
    EMAIL_CONFIGURATION_ERROR(
            "NOTIF_003",
            "error.notification.email.configuration-error",
            "error.title.configuration-error",
            Response.Status.SERVICE_UNAVAILABLE // 503 - Service not properly configured
    ),

    // SMS errors (NOTIF_011 - NOTIF_020)
    SMS_SEND_FAILED(
            "NOTIF_011",
            "error.notification.sms.send-failed",
            "error.title.sms-failed",
            Response.Status.BAD_GATEWAY // 502 - External service (Twilio) failed
    ),
    SMS_PROVIDER_ERROR(
            "NOTIF_012",
            "error.notification.sms.provider-error",
            "error.title.sms-failed",
            Response.Status.BAD_GATEWAY // 502 - Provider API issue
    ),
    SMS_INVALID_PHONE(
            "NOTIF_013",
            "error.notification.sms.invalid-phone",
            "error.title.invalid-data",
            Response.Status.BAD_REQUEST
    ),
    SMS_CONFIGURATION_ERROR(
            "NOTIF_014",
            "error.notification.sms.configuration-error",
            "error.title.configuration-error",
            Response.Status.SERVICE_UNAVAILABLE // 503 - Service not properly configured
    ),

    // Telegram errors (NOTIF_021 - NOTIF_030)
    TELEGRAM_SEND_FAILED(
            "NOTIF_021",
            "error.notification.telegram.send-failed",
            "error.title.telegram-failed",
            Response.Status.BAD_GATEWAY
    ),
    TELEGRAM_INVALID_CHAT_ID(
            "NOTIF_022",
            "error.notification.telegram.invalid-chat-id",
            "error.title.invalid-data",
            Response.Status.BAD_REQUEST
    ),
    TELEGRAM_BOT_ERROR(
            "NOTIF_023",
            "error.notification.telegram.bot-error",
            "error.title.telegram-failed",
            Response.Status.BAD_GATEWAY
    ),
    TELEGRAM_CONFIGURATION_ERROR(
            "NOTIF_024",
            "error.notification.telegram.configuration-error",
            "error.title.configuration-error",
            Response.Status.SERVICE_UNAVAILABLE
    ),
    TELEGRAM_INVALID_PARAMETERS(
            "NOTIF_025",
            "error.notification.telegram.invalid-parameters",
            "error.title.invalid-data",
            Response.Status.BAD_REQUEST
    ),
    TELEGRAM_INVALID_RECIPIENT(
            "NOTIF_026",
            "error.notification.telegram.invalid-recipient",
            "error.title.invalid-data",
            Response.Status.BAD_REQUEST
    ),
    TELEGRAM_RATE_LIMITED(
            "NOTIF_027",
            "error.notification.telegram.rate-limited",
            "error.title.rate-limit-exceeded",
            Response.Status.TOO_MANY_REQUESTS
    ),

    // Template errors (NOTIF_041 - NOTIF_060)
    TEMPLATE_NOT_FOUND(
            "NOTIF_041",
            "error.template.not-found",
            "error.title.template-not-found",
            Response.Status.NOT_FOUND // 404 - Resource doesn't exist
    ),
    TEMPLATE_RENDER_ERROR(
            "NOTIF_042",
            "error.template.render-error",
            "error.title.template-error",
            Response.Status.BAD_REQUEST // 400 - Template data/syntax issue (client's template data is bad)
    ),
    TEMPLATE_SCAN_ERROR(
            "NOTIF_043",
            "error.template.scan-error",
            "error.title.template-error",
            Response.Status.SERVICE_UNAVAILABLE // 503 - Can't read template directory
    ),
    TEMPLATE_INVALID_PATH(
            "NOTIF_044",
            "error.template.invalid-path",
            "error.title.invalid-data",
            Response.Status.BAD_REQUEST
    ),

    // Validation errors (NOTIF_061 - NOTIF_080)
    INVALID_NOTIFICATION_REQUEST(
            "NOTIF_061",
            "error.validation.invalid-request",
            "error.title.validation-failed",
            Response.Status.BAD_REQUEST
    ),
    MISSING_REQUIRED_FIELD(
            "NOTIF_062",
            "error.validation.missing-field",
            "error.title.validation-failed",
            Response.Status.BAD_REQUEST
    ),
    INVALID_CHANNEL(
            "NOTIF_063",
            "error.validation.invalid-channel",
            "error.title.validation-failed",
            Response.Status.BAD_REQUEST
    ),
    INVALID_LOCALE(
            "NOTIF_064",
            "error.validation.invalid-locale",
            "error.title.validation-failed",
            Response.Status.BAD_REQUEST
    ),
    CHANNEL_NOT_SUPPORTED(
            "NOTIF_065",
            "error.validation.channel-not-supported",
            "error.title.validation-failed",
            Response.Status.NOT_IMPLEMENTED // 501 - Feature not implemented
    ),
    RATE_LIMIT_EXCEEDED(
        "NOTIF_066",
                "error.rate-limit.exceeded",
                "error.title.rate-limit-exceeded",
        Response.Status.TOO_MANY_REQUESTS // 429
    ),

    // Conflict errors (NOTIF_081 - NOTIF_090)
    DUPLICATE_NOTIFICATION(
            "NOTIF_081",
            "error.notification.duplicate",
            "error.title.duplicate-notification",
            Response.Status.CONFLICT // 409 - notification id already exists
    );

    private final String code;
    private final String messageKey;
    private final String titleKey;
    private final Response.Status status;

    NotificationErrorCode(String code, String messageKey, String titleKey, Response.Status status) {
        this.code = code;
        this.messageKey = messageKey;
        this.titleKey = titleKey;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public Response.Status getStatus() {
        return status;
    }

    public int getStatusCode() {
        return status.getStatusCode();
    }
}
