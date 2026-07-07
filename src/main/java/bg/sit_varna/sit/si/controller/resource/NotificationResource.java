package bg.sit_varna.sit.si.controller.resource;

import bg.sit_varna.sit.si.config.app.LocaleResolver;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.controller.api.NotificationApi;
import bg.sit_varna.sit.si.controller.base.BaseResource;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.dto.request.GetFailedNotificationsRequest;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.dto.response.FailedNotificationResponse;
import bg.sit_varna.sit.si.dto.response.PageResponse;
import bg.sit_varna.sit.si.dto.response.SendNotificationResponse;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.mapper.NotificationMapper;
import bg.sit_varna.sit.si.service.core.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class NotificationResource extends BaseResource implements NotificationApi {

    private static final Logger LOG = Logger.getLogger(NotificationResource.class);
    private static final int MAX_PAGE_SIZE = 100;

    private NotificationService notificationService;
    private NotificationMapper notificationMapper;

    @Inject
    public NotificationResource(LocaleResolver localeResolver,
                                NotificationService notificationService,
                                NotificationMapper notificationMapper) {
        super(localeResolver);
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
    }

    protected NotificationResource() {
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    /**
     * POST /api/v1/notifications/send
     */
    @Override
    public Response sendNotification(@Valid SendNotificationRequest request) {
        Locale resolvedLocale = resolveLocale();

        Notification notification = notificationMapper.toDomain(request, resolvedLocale);

        notificationService.dispatchNotification(notification);

        SendNotificationResponse response = SendNotificationResponse.of(
                notification.getId(),
                NotificationStatus.QUEUED,
                "Notification queued for delivery",
                request.recipient(),
                request.channel().toString()
        );

        return Response.status(Response.Status.ACCEPTED).entity(response).build();
    }

    /**
     * GET /api/v1/notifications/failed
     */
    @Override
    public Response listFailedNotifications(@BeanParam GetFailedNotificationsRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), MAX_PAGE_SIZE);

        List<NotificationRecord> records = notificationService.getFailedNotifications(page, size);
        long totalItems = notificationService.countFailedNotifications();

        List<FailedNotificationResponse> items = records.stream()
                .map(FailedNotificationResponse::from)
                .toList();

        PageResponse<FailedNotificationResponse> response = PageResponse.of(items, page, size, totalItems);

        return Response.ok(response).build();
    }
}
