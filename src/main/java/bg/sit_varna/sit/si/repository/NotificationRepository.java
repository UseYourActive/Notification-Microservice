package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<NotificationRecord, String> {

    public List<NotificationRecord> findByRecipient(String recipient) {
        return find("recipient", recipient).list();
    }

    public List<NotificationRecord> findByStatus(NotificationStatus status) {
        return find("status", status).list();
    }

    public PanacheQuery<NotificationRecord> findByStatus(NotificationStatus status, Page page) {
        return find("status", Sort.by("createdAt").descending(), status).page(page);
    }
}
