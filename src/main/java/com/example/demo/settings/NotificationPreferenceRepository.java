package com.example.demo.settings;

import com.example.demo.auth.AppUser;
import com.example.demo.notification.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    List<NotificationPreference> findByUser(AppUser user);

    Optional<NotificationPreference> findByUserAndType(AppUser user, NotificationType type);

    void deleteByUser(AppUser user);
}
