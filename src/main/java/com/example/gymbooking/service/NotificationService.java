package com.example.gymbooking.service;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.GymComment;
import com.example.gymbooking.model.Notification;
import com.example.gymbooking.model.Payment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.NotificationRepository;
import com.example.gymbooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Хэрэглэгчийн бүх мэдэгдлийг авах
     */
    public List<Notification> getAllNotificationsByUserId(Long userId) {
        return notificationRepository.findByUserId(userId);
    }

    /**
     * Хэрэглэгчийн өөрийн мэдэгдлүүдийг авах (User объектоор)
     */
    public List<Notification> getMyNotifications(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Хэрэглэгч хоосон байж болохгүй");
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    /**
     * Хэрэглэгчийн өөрийн мэдэгдлүүдийг авах (userId-ээр)
     */
    public List<Notification> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Хэрэглэгчийн мэдэгдлүүдийг огноогоор буурахаар эрэмбэлэн авах
     */
    public List<Notification> getNotificationsByUserIdOrderByDateDesc(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Хэрэглэгчийн уншигдаагүй мэдэгдлүүдийг авах
     */
    public List<Notification> getUnreadNotificationsByUserId(Long userId) {
        return notificationRepository.findByUserIdAndRead(userId, false);
    }

    /**
     * Хэрэглэгчийн уншигдаагүй мэдэгдлүүдийг огноогоор буурахаар эрэмбэлэн авах
     */
    public List<Notification> getUnreadNotificationsByUserIdOrderByDateDesc(Long userId) {
        return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false);
    }

    /**
     * Хэрэглэгчийн уншигдсан мэдэгдлүүдийг авах
     */
    public List<Notification> getReadNotificationsByUserId(Long userId) {
        return notificationRepository.findByUserIdAndRead(userId, true);
    }

    /**
     * Мэдэгдлийг ID-ээр нь авах
     */
    public Optional<Notification> getNotificationById(Long id) {
        return notificationRepository.findById(id);
    }
    /**
     * Gym-д элсэх хүсэлт гаргасан үед мэдэгдэл үүсгэх
     */
    @Transactional
    public Notification createGymRequestNotification(User requester, User admin, Gym gym) {
        if (requester == null) {
            throw new IllegalArgumentException("Хүсэлт гаргасан хэрэглэгч хоосон байж болохгүй");
        }
        if (admin == null) {
            throw new IllegalArgumentException("Админ хэрэглэгч хоосон байж болохгүй");
        }
        if (gym == null) {
            throw new IllegalArgumentException("Gym хоосон байж болохгүй");
        }

        Notification notification = new Notification();
        notification.setUserId(admin.getId());
        notification.setUser(admin);
        notification.setTitle("📋 Шинэ Gym Хүсэлт");
        notification.setMessage(String.format("\"%s\" хэрэглэгч \"%s\" биеийн тамирын заалд элсэх хүсэлт гаргалаа.",
                requester.getUsername(), gym.getName()));
        notification.setRead(false);

        return notificationRepository.save(notification);
    }
    /**
     * Шинэ мэдэгдэл үүсгэх
     */
    @Transactional
    public Notification createNotification(Long userId, String title, String message) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            throw new RuntimeException("Хэрэглэгч олдсонгүй. ID: " + userId);
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setUser(userOptional.get());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);

        return notificationRepository.save(notification);
    }

    /**
     * Олон хэрэглэгчдэд мэдэгдэл илгээх
     */
    @Transactional
    public void createNotificationsForUsers(List<Long> userIds, String title, String message) {
        for (Long userId : userIds) {
            createNotification(userId, title, message);
        }
    }

    /**
     * Бүх хэрэглэгчдэд мэдэгдэл илгээх
     */
    @Transactional
    public void createNotificationForAllUsers(String title, String message) {
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            createNotification(user.getId(), title, message);
        }
    }

    /**
     * Gym батлагдсан үед мэдэгдэл үүсгэх
     */
    @Transactional
    public Notification createGymApprovedNotification(User ownerUser, Gym gym) {
        if (ownerUser == null) {
            throw new IllegalArgumentException("Хэрэглэгч хоосон байж болохгүй");
        }
        if (gym == null) {
            throw new IllegalArgumentException("Gym хоосон байж болохгүй");
        }

        Notification notification = new Notification();
        notification.setUserId(ownerUser.getId());
        notification.setUser(ownerUser);
        notification.setTitle("✅ Gym Батлагдсан");
        notification.setMessage(String.format("Таны \"%s\" gym амжилттай батлагдлаа.", gym.getName()));
        notification.setRead(false);

        return notificationRepository.save(notification);
    }

    /**
     * Gym татгалзсан үед мэдэгдэл үүсгэх
     */
    @Transactional
    public Notification createGymRejectedNotification(User ownerUser, Gym gym, String reason) {
        if (ownerUser == null) {
            throw new IllegalArgumentException("Хэрэглэгч хоосон байж болохгүй");
        }
        if (gym == null) {
            throw new IllegalArgumentException("Gym хоосон байж болохгүй");
        }

        Notification notification = new Notification();
        notification.setUserId(ownerUser.getId());
        notification.setUser(ownerUser);
        notification.setTitle("❌ Gym Татгалзсан");

        String message;
        if (reason != null && !reason.trim().isEmpty()) {
            message = String.format("Таны \"%s\" gym-ийн хүсэлт дараах шалтгаанаар татгалзлаа: %s",
                    gym.getName(), reason);
        } else {
            message = String.format("Таны \"%s\" gym-ийн хүсэлт татгалзлаа.", gym.getName());
        }

        notification.setMessage(message);
        notification.setRead(false);

        return notificationRepository.save(notification);
    }

    /**
     * Gym татгалзсан үед мэдэгдэл үүсгэх (шалтгаангүй)
     */
    @Transactional
    public Notification createGymRejectedNotification(User ownerUser, Gym gym) {
        return createGymRejectedNotification(ownerUser, gym, null);
    }

    /**
     * Мэдэгдлийг уншсан болгох
     */
    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Мэдэгдэл олдсонгүй. ID: " + notificationId));

        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    /**
     * Хэрэглэгчийн бүх мэдэгдлийг уншсан болгох
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository.findByUserIdAndRead(userId, false);
        for (Notification notification : unreadNotifications) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    /**
     * Хэрэглэгчийн хуучин мэдэгдлүүдийг устгах
     */
    @Transactional
    public void deleteOldNotifications(Long userId, int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<Notification> oldNotifications = notificationRepository.findByUserIdAndCreatedAtBefore(userId, cutoffDate);
        notificationRepository.deleteAll(oldNotifications);
    }

    /**
     * Нэг мэдэгдлийг устгах
     */
    @Transactional
    public void deleteNotification(Long notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    /**
     * Хэрэглэгчийн бүх мэдэгдлийг устгах
     */
    @Transactional
    public void deleteAllNotificationsByUserId(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserId(userId);
        notificationRepository.deleteAll(notifications);
    }

    /**
     * Хэрэглэгчийн уншигдаагүй мэдэгдлийн тоог авах
     */
    public long getUnreadNotificationCount(Long userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    /**
     * Хэрэглэгчийн нийт мэдэгдлийн тоог авах
     */
    public long getTotalNotificationCount(Long userId) {
        return notificationRepository.countByUserId(userId);
    }

    @Transactional
    public void createGymBookingNotificationForAdmins(Booking booking) {
        if (booking == null || booking.getGym() == null) {
            return;
        }

        Gym gym = booking.getGym();
        Set<Long> recipientIds = resolveGymAdminRecipientIds(gym);

        for (Long recipientId : recipientIds) {
            createNotification(
                    recipientId,
                    "🆕 Танай заалд шинэ захиалга",
                    String.format("%s хэрэглэгч %s %s цагт \"%s\" заалд захиалга хийлээ.",
                            booking.getUser() != null ? booking.getUser().getUsername() : "Хэрэглэгч",
                            booking.getDate(),
                            booking.getTime(),
                            gym.getName())
            );
        }
    }

    @Transactional
    public void createGymCommentNotificationForAdmins(GymComment comment) {
        if (comment == null || comment.getGym() == null) {
            return;
        }

        Gym gym = comment.getGym();
        Set<Long> recipientIds = resolveGymAdminRecipientIds(gym);

        for (Long recipientId : recipientIds) {
            createNotification(
                    recipientId,
                    "💬 Танай заалд шинэ сэтгэгдэл",
                    String.format("\"%s\" заалд %s хэрэглэгч сэтгэгдэл үлдээлээ: %s",
                            gym.getName(),
                            comment.getUser() != null ? comment.getUser().getUsername() : "Хэрэглэгч",
                            comment.getComment())
            );
        }
    }

    private Set<Long> resolveGymAdminRecipientIds(Gym gym) {
        Set<Long> recipientIds = new LinkedHashSet<>();

        if (gym.getOwnerUser() != null && gym.getOwnerUser().getId() != null) {
            recipientIds.add(gym.getOwnerUser().getId());
        }

        List<User> gymAdmins = userRepository.findByGym_IdAndRoleIn(
                gym.getId(),
                List.of("GYM_ADMIN", "ROLE_GYM_ADMIN")
        );

        for (User admin : gymAdmins) {
            if (admin.getId() != null) {
                recipientIds.add(admin.getId());
            }
        }

        return recipientIds;
    }

    @Transactional
    public Notification createPaymentSuccessNotification(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Төлбөр хоосон байж болохгүй");
        }

        User user = payment.getUser();
        if (user == null) {
            throw new IllegalArgumentException("Хэрэглэгч олдсонгүй");
        }

        Notification notification = new Notification();
        notification.setUserId(user.getId());
        notification.setUser(user);
        notification.setTitle("💳 Төлбөр амжилттай");
        notification.setMessage(String.format("Таны %s ₮ төлбөр амжилттай хийгдлээ. Гүйлгээний ID: %s",
                payment.getAmount(), payment.getTransactionId()));
        notification.setRead(false);

        return notificationRepository.save(notification);
    }
    /**
     * Сүүлийн N шинэ мэдэгдлийг авах
     */
    public List<Notification> getRecentNotifications(Long userId, int limit) {
        List<Notification> allNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (allNotifications.size() > limit) {
            return allNotifications.subList(0, limit);
        }
        return allNotifications;
    }

    /**
     * Тодорхой огнооны дараах мэдэгдлүүдийг авах
     */
    public List<Notification> getNotificationsAfterDate(Long userId, LocalDateTime date) {
        return notificationRepository.findByUserIdAndCreatedAtAfter(userId, date);
    }

    /**
     * Мэдэгдэл байгаа эсэхийг шалгах
     */
    public boolean existsNotification(Long notificationId) {
        return notificationRepository.existsById(notificationId);
    }

    /**
     * Хэрэглэгчид уншигдаагүй мэдэгдэл байгаа эсэхийг шалгах
     */
    public boolean hasUnreadNotifications(Long userId) {
        return getUnreadNotificationCount(userId) > 0;
    }

}
