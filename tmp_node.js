const fs = require('fs');
const path = 'src/main/java/com/example/gymbooking/controller/BookingController.java';
let content = fs.readFileSync(path, 'utf8');
const pattern = /    private void safelyCreateBookingNotifications[\s\S]*?(?=    private String normalizeTime)/;
const replacement =     private void safelyCreateBookingNotifications(User currentUser, Booking savedBooking) {
        try {
            notificationService.createNotification(
                    currentUser.getId(),
                     ✅ Захиалга баталгаажлаа,
                    String.format(Таны %s %s цагийн захиалга амжилттай баталгаажлаа.,
                            savedBooking.getDate(), savedBooking.getTime())
            );
        } catch (RuntimeException ignored) {
            // Non-critical failure should not block booking creation.
        }
        try {
            notifyGymAdminAboutBooking(currentUser, savedBooking);
        } catch (RuntimeException ignored) {
            // Non-critical failure should not block booking creation.
        }
    }

    private void notifyGymAdminAboutBooking(User currentUser, Booking savedBooking) {
        if (savedBooking == null || savedBooking.getGym() == null) {
            return;
        }
        User gymOwner = savedBooking.getGym().getOwnerUser();
        if (gymOwner == null) {
            return;
        }

        notificationService.createNotification(
                gymOwner.getId(),
                📣 Шинэ захиалга ирлээ,
                String.format(%s хэрэглэгч %s %s цагт \%s\ заалд захиалга хийлээ.,
                        currentUser.getUsername(),
                        savedBooking.getDate(),
                        savedBooking.getTime(),
                        savedBooking.getGym().getName())
        );
    }

;
content = content.replace(pattern, replacement);
fs.writeFileSync(path, content, 'utf8');
