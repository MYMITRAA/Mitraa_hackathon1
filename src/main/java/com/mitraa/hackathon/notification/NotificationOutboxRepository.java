package com.mitraa.hackathon.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant; import java.util.List;
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox,Long>{ List<NotificationOutbox> findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAt(NotificationStatus s, Instant now); long countByStatus(NotificationStatus s); boolean existsByEventTypeAndReferenceId(String eventType,String referenceId); }
