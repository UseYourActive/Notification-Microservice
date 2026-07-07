package bg.sit_varna.sit.si.controller.api;

import bg.sit_varna.sit.si.dto.request.GetFailedNotificationsRequest;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.dto.response.PageResponse;
import bg.sit_varna.sit.si.dto.response.SendNotificationResponse;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST API interface for sending notifications across multiple delivery channels.
 *
 * <p>This is the primary interface for dispatching notifications via EMAIL, SMS, TELEGRAM channels.
 * The API handles template rendering, locale resolution, rate limiting,
 * deduplication, and delivery confirmation.</p>
 *
 * <p><strong>Core Features:</strong></p>
 * <ul>
 * <li><strong>Multi-Channel Delivery:</strong> Single unified API for all notification channels</li>
 * <li><strong>Template-Based Content:</strong> Dynamic content rendering with variable substitution</li>
 * <li><strong>Internationalization:</strong> Automatic locale resolution from Accept-Language header</li>
 * <li><strong>Rate Limiting:</strong> Per-channel rate limits to prevent abuse and ensure fair usage</li>
 * <li><strong>Deduplication:</strong> Prevents duplicate notifications within configurable time windows</li>
 * <li><strong>Validation:</strong> Comprehensive input validation for recipients, templates, and parameters</li>
 * </ul>
 *
 * <p><strong>Request Processing Flow:</strong></p>
 * <ol>
 * <li>Request validation (recipient format, template existence, required parameters)</li>
 * <li>Locale resolution from Accept-Language header with fallback to default</li>
 * <li>Rate limit check (per recipient and channel)</li>
 * <li>Deduplication check (prevent duplicate sends within time window)</li>
 * <li>Template rendering with provided parameters</li>
 * <li>Channel-specific delivery via appropriate provider (SendGrid, Twilio, Telegram Bot API)</li>
 * <li>Delivery confirmation and logging</li>
 * <li>Metrics collection and success/failure tracking</li>
 * </ol>
 *
 * <p><strong>Rate Limiting:</strong></p>
 * Default rate limits per recipient (configurable):
 * <ul>
 * <li>EMAIL: 10 notifications per hour</li>
 * <li>SMS: 5 notifications per hour</li>
 * <li>TELEGRAM: 20 notifications per hour</li>
 * </ul>
 *
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 * <li>Validate recipient addresses to prevent abuse</li>
 * <li>Sanitize template parameters to prevent injection attacks</li>
 * <li>Implement authentication/authorization for production use (not included in base API)</li>
 * <li>Monitor for spam patterns and unusual activity</li>
 * <li>Log all notification attempts for audit trails</li>
 * </ul>
 *
 * @version 1.0
 * @since 1.0
 */
@Path("/api/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(
        name = "Notification Delivery",
        description = "Send notifications across multiple channels (EMAIL, SMS, TELEGRAM) with template support"
)
public interface NotificationApi {

    /**
     * POST /api/v1/notifications/send
     * Locale is automatically resolved from Accept-Language header
     */
    @POST
    @Path("/send")
    @Operation(
            summary = "Send a notification",
            description = """
            Delivers a notification to a recipient via the specified channel using template-based content.
            
            **Channel Selection:**
            Choose the appropriate channel based on your use case:
            
            - **EMAIL**:
              - Rich HTML content with images, links, and formatting
              - Suitable for: newsletters, reports, detailed communications
              - Delivery time: 1-30 seconds (depends on recipient mail server)
              - Best for: Non-urgent, content-rich messages
            
            - **SMS**:
              - Plain text only, character limits apply
              - Suitable for: OTP codes, critical alerts, appointment reminders
              - Delivery time: 1-5 seconds
              - Best for: Time-sensitive, brief messages
            
            - **TELEGRAM**:
              - Rich text with markdown, buttons, and media
              - Suitable for: Interactive notifications, bot communications
              - Delivery time: < 1 second (instant)
              - Best for: Real-time updates, interactive workflows
            
            **Locale Handling:**
            The notification language is determined by the Accept-Language header:
            - Supported: 'en' (English), 'bg' (Bulgarian)
            - Default fallback: 'en'
            - Header format: Accept-Language: bg, en;q=0.9
            
            **Template Parameters:**
            Templates use mustache-style placeholders ({{variableName}}). Ensure all required
            parameters for your template are provided in the parameters object.
            
            **Common Parameters:**
            - firstName, lastName: User names
            - email: User email
            - actionUrl: Call-to-action link
            - code: Verification or OTP code
            - expiryMinutes: Code expiration time
            
            **Rate Limiting:**
            Each channel has independent rate limits per recipient:
            - EMAIL: 10/hour
            - SMS: 5/hour
            - TELEGRAM: 20/hour
            
            Exceeding limits returns HTTP 429 with retry-after information.
            
            **Deduplication:**
            Identical notifications (same recipient, template, channel) within 5 minutes
            are automatically deduplicated to prevent spam.
            
            **Best Practices:**
            1. Always validate recipient format before calling the API
            2. Include all required template parameters
            3. Handle rate limit responses gracefully with backoff
            4. Use appropriate channel for message urgency and content type
            5. Set Accept-Language header to user's preferred language
            6. Store the returned notificationId for tracking and debugging
            7. Implement retry logic for 502/503 errors with exponential backoff
            """
    )
    @Parameter(
            name = "Accept-Language",
            in = ParameterIn.HEADER,
            description = """
            Preferred language for notification content. Determines which template variant to use.
            
            **Supported Values:**
            - `en`: English (default)
            - `bg`: Bulgarian
            
            **Format:**
            Standard HTTP Accept-Language header format with optional quality values.
            Examples:
            - Accept-Language: bg
            - Accept-Language: bg, en;q=0.9
            
            **Fallback Behavior:**
            If the requested locale is not supported or if the template doesn't exist
            in that locale, the system automatically falls back to English (en).
            
            **Quality Values:**
            When multiple locales are specified, the system selects the highest-priority
            supported locale. Quality values (q=) determine priority.
            """,
            schema = @Schema(
                    type = SchemaType.STRING,
                    defaultValue = "en",
                    enumeration = {"en", "bg"}
            ),
            example = "bg"
    )
    @RequestBody(
            description = "Notification request payload with examples for different channels",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = SendNotificationRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "1. Telegram (Daily Update)",
                                    summary = "Send a Telegram Daily Summary to Alex",
                                    value = """
                                        {
                                          "channel": "TELEGRAM",
                                          "recipient": "1898155128",
                                          "templateName": "telegram/daily_reminder",
                                          "data": {
                                            "firstName": "Alex",
                                            "taskCount": "5",
                                            "messageCount": "1",
                                            "nextEvent": "Project Demo 🚀"
                                          }
                                        }
                                    """
                            ),
                            @ExampleObject(
                                    name = "2. Telegram (Raw Message)",
                                    summary = "Send a direct text message without a template",
                                    value = """
                                        {
                                          "channel": "TELEGRAM",
                                          "recipient": "1898155128",
                                          "message": "🚨 System Alert: High CPU Usage detected on production server."
                                        }
                                    """
                            ),
                            @ExampleObject(
                                    name = "3. Email (Welcome)",
                                    summary = "Send a Welcome Email (Replace recipient with real email)",
                                    value = """
                                        {
                                          "channel": "EMAIL",
                                          "recipient": "alexorozov@gmail.com",
                                          "templateName": "email/welcome",
                                          "data": {
                                            "firstName": "Alex",
                                            "actionUrl": "https://myapp.com/login",
                                            "supportEmail": "help@myapp.com",
                                            "appName": "QuarkusNotif",
                                            "year": "2025"
                                          }
                                        }
                                    """
                            ),
                            @ExampleObject(
                                    name = "4. SMS (OTP)",
                                    summary = "Send an OTP via SMS",
                                    value = """
                                        {
                                          "channel": "SMS",
                                          "recipient": "+15005550006",
                                          "templateName": "sms/verification_code",
                                          "data": {
                                            "code": "482910",
                                            "expiryMinutes": "5",
                                            "appName": "QuarkusNotif"
                                          }
                                        }
                                    """
                            )
                    }
            )
    )
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = """
                Notification sent successfully and delivery confirmed by the provider.
                
                The response includes:
                - Unique notification ID for tracking
                - Delivery status (SENT)
                - Success message
                - Echo of recipient and channel for verification
                
                **Note:** A 200 response indicates the notification was accepted and delivered
                by the channel provider, but does not guarantee end-user receipt (e.g., email
                could still bounce, SMS could be blocked by carrier filters).
                """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = SendNotificationResponse.class),
                            examples = @ExampleObject(
                                    name = "Successful Delivery",
                                    value = """
                        {
                          "notificationId": "550e8400-e29b-41d4-a716-446655440000",
                          "status": "SENT",
                          "message": "Notification sent successfully",
                          "recipient": "john.doe@example.com",
                          "channel": "EMAIL",
                          "timestamp": "2025-10-31T14:23:45Z"
                        }
                        """
                            )
                    )
            ),
            @APIResponse(
                    responseCode = "400",
                    description = """
                Bad Request - Invalid input data or validation failure.
                
                **Common Causes:**
                - Missing required fields (recipient, channel, template, parameters)
                - Invalid recipient format for the specified channel
                - Template does not exist for the resolved locale
                - Missing required template parameters
                - Invalid channel enum value
                - Malformed JSON request body
                
                **Resolution:**
                - Verify all required fields are present
                - Check recipient format matches channel requirements
                - Validate template exists using Template API
                - Ensure all template parameters are provided
                - Check JSON syntax and structure
                """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "Validation Error",
                                    value = """
                        {
                          "error": "VALIDATION_ERROR",
                          "message": "Invalid email address format",
                          "field": "recipient",
                          "value": "invalid-email",
                          "timestamp": "2025-10-31T14:23:45Z"
                        }
                        """
                            )
                    )
            ),
            @APIResponse(
                    responseCode = "429",
                    description = """
                Rate Limit Exceeded - Too many requests for this recipient and channel.
                
                **Rate Limits:**
                - EMAIL: 10 notifications per hour
                - SMS: 5 notifications per hour
                - TELEGRAM: 20 notifications per hour
                
                **Response Headers:**
                - X-RateLimit-Limit: Maximum allowed requests
                - X-RateLimit-Remaining: Remaining requests in current window
                - X-RateLimit-Reset: Unix timestamp when limit resets
                - Retry-After: Seconds to wait before retrying
                
                **Resolution:**
                - Implement exponential backoff retry strategy
                - Respect Retry-After header
                - Consider distributing load across time
                - For bulk operations, implement queuing system
                """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "Rate Limit Error",
                                    value = """
                        {
                          "error": "RATE_LIMIT_EXCEEDED",
                          "message": "Rate limit exceeded for SMS channel",
                          "channel": "SMS",
                          "recipient": "+359876543210",
                          "limit": 5,
                          "window": "1 hour",
                          "retryAfter": 1847,
                          "timestamp": "2025-10-31T14:23:45Z"
                        }
                        """
                            )
                    )
            ),
            @APIResponse(
                    responseCode = "503",
                    description = """
                Service Unavailable - Notification service is temporarily down or channel not configured.
                
                **Common Causes:**
                - Notification service is in maintenance mode
                - Required channel provider is not configured
                - Database or Redis is unreachable
                - System resources exhausted (memory, connections)
                - Service is starting up or shutting down
                
                **Resolution:**
                - Check service health endpoint
                - Verify all required channel configurations are present
                - Wait and retry after a delay (respect Retry-After header if present)
                - Check system resource availability
                - Review application logs for configuration errors
                
                **Retry Strategy:**
                Wait 30-60 seconds before retrying. If issue persists, check system status.
                """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "Service Unavailable",
                                    value = """
                        {
                          "error": "SERVICE_UNAVAILABLE",
                          "message": "Email channel is not configured",
                          "channel": "EMAIL",
                          "reason": "Missing SendGrid API key configuration",
                          "timestamp": "2025-10-31T14:23:45Z"
                        }
                        """
                            )
                    )
            )
    })
    Response sendNotification(@Valid SendNotificationRequest request);

    /**
     * GET /api/v1/notifications/failed
     */
    @GET
    @Path("/failed")
    @Operation(
            summary = "List failed notifications",
            description = """
            Returns a paginated list of notifications whose delivery status is FAILED,
            ordered by creation date (most recent first).

            **Pagination:**
            - `page`: 0-based page index (default 0)
            - `size`: number of items per page, capped at 100 (default 20)
            """
    )
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = "Paginated list of failed notifications",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(
                                    name = "Failed Notifications Page",
                                    value = """
                        {
                          "items": [
                            {
                              "notificationId": "550e8400-e29b-41d4-a716-446655440000",
                              "recipient": "ivan.petrov@example.com",
                              "channel": "EMAIL",
                              "templateName": "email/welcome",
                              "status": "FAILED",
                              "createdAt": "2025-10-26T14:30:00",
                              "updatedAt": "2025-10-26T14:31:12"
                            }
                          ],
                          "page": 0,
                          "size": 20,
                          "totalItems": 1,
                          "totalPages": 1
                        }
                        """
                            )
                    )
            )
    })
    Response listFailedNotifications(@BeanParam GetFailedNotificationsRequest request);
}
