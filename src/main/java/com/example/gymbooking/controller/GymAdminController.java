package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Slot;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.repository.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/gym-admin", "/gym-admin"})
@CrossOrigin(origins = "http://localhost:3000")
public class GymAdminController {

    private final GymRepository gymRepository;
    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public GymAdminController(GymRepository gymRepository,
                              BookingRepository bookingRepository,
                              SlotRepository slotRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder) {
        this.gymRepository = gymRepository;
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Helper method to get gym by admin
    private Gym getGymByAdmin(User admin) {
        List<Gym> gyms = gymRepository.findByOwnerUser(admin);
        return gyms.isEmpty() ? null : gyms.get(0);
    }

    // ================== GYM INFO ==================

    @GetMapping("/my-gym")
    public ResponseEntity<?> getMyGym(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gym);
    }

    @PutMapping("/my-gym")
    public ResponseEntity<?> updateMyGym(@AuthenticationPrincipal User admin,
                                         @RequestBody Gym updatedGym) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        if (updatedGym.getName() != null) gym.setName(updatedGym.getName());
        if (updatedGym.getLocation() != null) gym.setLocation(updatedGym.getLocation());
        if (updatedGym.getDescription() != null) gym.setDescription(updatedGym.getDescription());
        if (updatedGym.getPhone() != null) gym.setPhone(updatedGym.getPhone());

        return ResponseEntity.ok(gymRepository.save(gym));
    }

    @PutMapping("/my-account")
    public ResponseEntity<?> updateMyAccount(@AuthenticationPrincipal User admin,
                                             @RequestBody Map<String, String> payload) {
        if (admin == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String newPhone = payload.get("phone");
        String currentPassword = payload.get("currentPassword");
        String newPassword = payload.get("newPassword");

        if ((newPhone == null || newPhone.isBlank()) && (newPassword == null || newPassword.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone эсвэл шинэ нууц үг оруулна уу"));
        }

        if (newPhone != null) {
            newPhone = newPhone.trim();
            Optional<User> phoneOwner = userRepository.findByPhone(newPhone);
            if (phoneOwner.isPresent() && !phoneOwner.get().getId().equals(admin.getId())) {
                return ResponseEntity.status(409).body(Map.of("error", "Энэ утасны дугаар бүртгэлтэй байна"));
            }

            admin.setPhone(newPhone);
            admin.setUsername(newPhone);
        }

        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword == null || currentPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Одоогийн нууц үгээ оруулна уу"));
            }

            if (!passwordsMatch(currentPassword.trim(), admin.getPassword())) {
                return ResponseEntity.status(401).body(Map.of("error", "Одоогийн нууц үг буруу байна"));
            }

            admin.setPassword(passwordEncoder.encode(newPassword.trim()));
        }

        User savedAdmin = userRepository.save(admin);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Мэдээлэл амжилттай шинэчлэгдлээ",
                "user", Map.of(
                        "id", savedAdmin.getId(),
                        "phone", savedAdmin.getPhone(),
                        "email", savedAdmin.getEmail(),
                        "firstName", savedAdmin.getFirstName(),
                        "lastName", savedAdmin.getLastName(),
                        "role", savedAdmin.getRole()
                )
        ));
    }

    private boolean passwordsMatch(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        return rawPassword.equals(storedPassword);
    }

    private boolean isBcryptHash(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    // ================== BOOKINGS ==================

    @GetMapping("/bookings")
    public ResponseEntity<List<Booking>> getAllBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGym(gym));
    }

    @GetMapping("/bookings/today")
    public ResponseEntity<?> getTodaysBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        List<Booking> bookings = bookingRepository.findTodaysBookingsByGym(gym);
        Map<String, Object> response = new HashMap<>();
        response.put("date", LocalDate.now());
        response.put("bookings", bookings);
        response.put("total", bookings.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings/date")
    public ResponseEntity<?> getBookingsByDate(@AuthenticationPrincipal User admin,
                                               @RequestParam String date) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Booking> bookings = bookingRepository.findByGym_IdAndSlot_Date(gym.getId(), parsedDate);

            Map<String, Object> response = new HashMap<>();
            response.put("date", parsedDate);
            response.put("bookings", bookings);
            response.put("total", bookings.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    @GetMapping("/bookings/confirmed")
    public ResponseEntity<List<Booking>> getConfirmedBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGymAndStatus(gym, "CONFIRMED"));
    }

    @GetMapping("/bookings/pending")
    public ResponseEntity<List<Booking>> getPendingBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGymAndStatus(gym, "PENDING"));
    }

    @GetMapping("/bookings/cancelled")
    public ResponseEntity<List<Booking>> getCancelledBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGymAndStatus(gym, "CANCELLED"));
    }

    @GetMapping("/bookings/stats")
    public ResponseEntity<?> getBookingStats(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", bookingRepository.countByGym(gym));
        stats.put("confirmed", bookingRepository.countByGymAndStatus(gym, "CONFIRMED"));
        stats.put("pending", bookingRepository.countByGymAndStatus(gym, "PENDING"));
        stats.put("cancelled", bookingRepository.countByGymAndStatus(gym, "CANCELLED"));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<?> getReportSummary(@AuthenticationPrincipal User admin,
                                              @RequestParam(defaultValue = "WEEK") String period,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            DateRange dateRange = resolveDateRange(period, startDate, endDate);
            List<Booking> bookings = bookingRepository.findByGymAndDateBetweenOrderByDateAscTimeAsc(
                    gym,
                    dateRange.startDate(),
                    dateRange.endDate()
            );

            return ResponseEntity.ok(buildReportResponse(gym, dateRange, bookings));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/reports/export")
    public ResponseEntity<?> exportReport(@AuthenticationPrincipal User admin,
                                          @RequestParam(defaultValue = "WEEK") String period,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            DateRange dateRange = resolveDateRange(period, startDate, endDate);
            List<Booking> bookings = bookingRepository.findByGymAndDateBetweenOrderByDateAscTimeAsc(
                    gym,
                    dateRange.startDate(),
                    dateRange.endDate()
            );

            byte[] workbookBytes = buildReportWorkbook(gym, dateRange, bookings);
            String filename = String.format(
                    "gym-report-%s-%s-to-%s.xlsx",
                    sanitizeFileName(gym.getName()),
                    dateRange.startDate(),
                    dateRange.endDate()
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentLength(workbookBytes.length)
                    .body(new ByteArrayResource(workbookBytes));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate report file"));
        }
    }

    // ================== SLOTS ==================

    @GetMapping("/slots")
    public ResponseEntity<List<Slot>> getAllSlots(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(slotRepository.findByGym(gym));
    }

    @GetMapping("/slots/date")
    public ResponseEntity<?> getSlotsByDate(@AuthenticationPrincipal User admin,
                                            @RequestParam String date) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Slot> slots = slotRepository.findByGymAndDate(gym, parsedDate);

            Map<String, Object> response = new HashMap<>();
            response.put("date", parsedDate);
            response.put("slots", slots);
            response.put("total", slots.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    @GetMapping("/slots/available")
    public ResponseEntity<List<Slot>> getAvailableSlots(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(slotRepository.findByGymAndAvailableTrue(gym));
    }

    @PostMapping("/slots")
    public ResponseEntity<?> createSlot(@AuthenticationPrincipal User admin,
                                        @RequestBody Slot slot) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        slot.setGym(gym);
        slot.setAvailable(true);
        if (slot.getMaxCapacity() == null) slot.setMaxCapacity(1);
        if (slot.getCurrentBookings() == null) slot.setCurrentBookings(0);

        return ResponseEntity.ok(slotRepository.save(slot));
    }

    @PutMapping("/slots/{slotId}")
    public ResponseEntity<?> updateSlot(@AuthenticationPrincipal User admin,
                                        @PathVariable Long slotId,
                                        @RequestBody Slot updatedSlot) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        Slot slot = slotRepository.findById(slotId).orElse(null);
        if (slot == null || !slot.getGym().getId().equals(gym.getId())) {
            return ResponseEntity.notFound().build();
        }

        if (updatedSlot.getDate() != null) slot.setDate(updatedSlot.getDate());
        if (updatedSlot.getTime() != null) slot.setTime(updatedSlot.getTime());
        if (updatedSlot.getPrice() != null) slot.setPrice(updatedSlot.getPrice());
        if (updatedSlot.getMaxCapacity() != null) slot.setMaxCapacity(updatedSlot.getMaxCapacity());

        return ResponseEntity.ok(slotRepository.save(slot));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<?> deleteSlot(@AuthenticationPrincipal User admin,
                                        @PathVariable Long slotId) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        Slot slot = slotRepository.findById(slotId).orElse(null);
        if (slot == null || !slot.getGym().getId().equals(gym.getId())) {
            return ResponseEntity.notFound().build();
        }

        slotRepository.delete(slot);
        return ResponseEntity.ok(Map.of("success", true, "message", "Slot deleted successfully"));
    }

    // ================== DASHBOARD ==================

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        List<Booking> todaysBookings = bookingRepository.findTodaysBookingsByGym(gym);
        List<Slot> todaysSlots = slotRepository.findByGymAndDate(gym, LocalDate.now());

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("gym", gym);
        dashboard.put("todaysBookings", todaysBookings);
        dashboard.put("todaysBookingsCount", todaysBookings.size());
        dashboard.put("todaysSlots", todaysSlots);
        dashboard.put("todaysSlotsCount", todaysSlots.size());
        dashboard.put("stats", Map.of(
                "totalBookings", bookingRepository.countByGym(gym),
                "confirmedBookings", bookingRepository.countByGymAndStatus(gym, "CONFIRMED"),
                "pendingBookings", bookingRepository.countByGymAndStatus(gym, "PENDING"),
                "totalSlots", slotRepository.countByGym(gym),
                "availableSlots", slotRepository.countByGymAndAvailableTrue(gym)
        ));

        return ResponseEntity.ok(dashboard);
    }

    private Map<String, Object> buildReportResponse(Gym gym, DateRange dateRange, List<Booking> bookings) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("gymId", gym.getId());
        response.put("gymName", gym.getName());
        response.put("period", dateRange.label());
        response.put("startDate", dateRange.startDate());
        response.put("endDate", dateRange.endDate());
        response.put("generatedAt", LocalDateTime.now());
        response.put("summary", buildSummary(bookings));
        response.put("dailyBreakdown", buildDailyBreakdown(bookings));
        response.put("bookings", buildBookingRows(bookings));
        return response;
    }

    private Map<String, Object> buildSummary(List<Booking> bookings) {
        long confirmed = countByStatus(bookings, "CONFIRMED");
        long pending = countByStatus(bookings, "PENDING");
        long cancelled = countByStatus(bookings, "CANCELLED");
        long rejected = countByStatus(bookings, "REJECTED");

        BigDecimal totalRevenue = sumRevenue(bookings);
        BigDecimal confirmedRevenue = sumRevenueByStatus(bookings, "CONFIRMED");
        BigDecimal activeRevenue = bookings.stream()
                .filter(booking -> !statusEquals(booking, "CANCELLED") && !statusEquals(booking, "REJECTED"))
                .map(Booking::getTotalPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalBookings", bookings.size());
        summary.put("confirmedBookings", confirmed);
        summary.put("pendingBookings", pending);
        summary.put("cancelledBookings", cancelled);
        summary.put("rejectedBookings", rejected);
        summary.put("totalRevenue", totalRevenue);
        summary.put("confirmedRevenue", confirmedRevenue);
        summary.put("activeRevenue", activeRevenue);
        return summary;
    }

    private List<Map<String, Object>> buildDailyBreakdown(List<Booking> bookings) {
        Map<LocalDate, List<Booking>> grouped = new LinkedHashMap<>();
        bookings.stream()
                .sorted(Comparator.comparing(Booking::getDate).thenComparing(Booking::getTime, Comparator.nullsLast(String::compareTo)))
                .forEach(booking -> grouped.computeIfAbsent(booking.getDate(), ignored -> new ArrayList<>()).add(booking));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Booking>> entry : grouped.entrySet()) {
            List<Booking> dailyBookings = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("totalBookings", dailyBookings.size());
            row.put("confirmedBookings", countByStatus(dailyBookings, "CONFIRMED"));
            row.put("pendingBookings", countByStatus(dailyBookings, "PENDING"));
            row.put("cancelledBookings", countByStatus(dailyBookings, "CANCELLED"));
            row.put("totalRevenue", sumRevenue(dailyBookings));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildBookingRows(List<Booking> bookings) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Booking booking : bookings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bookingId", booking.getId());
            row.put("date", booking.getDate());
            row.put("time", booking.getTime());
            row.put("status", booking.getStatus());
            row.put("approved", booking.isApproved());
            row.put("memberName", resolveMemberName(booking));
            row.put("memberPhone", booking.getUser() != null ? booking.getUser().getPhone() : null);
            row.put("amount", booking.getTotalPrice());
            row.put("createdAt", booking.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private byte[] buildReportWorkbook(Gym gym, DateRange dateRange, List<Booking> bookings) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            writeSummarySheet(workbook.createSheet("Summary"), headerStyle, gym, dateRange, bookings);
            writeDailySheet(workbook.createSheet("Daily"), headerStyle, bookings);
            writeBookingsSheet(workbook.createSheet("Bookings"), headerStyle, bookings);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeSummarySheet(Sheet sheet, CellStyle headerStyle, Gym gym, DateRange dateRange, List<Booking> bookings) {
        Map<String, Object> summary = buildSummary(bookings);

        int rowIndex = 0;
        rowIndex = writeKeyValueRow(sheet, rowIndex, "Заал", gym.getName(), headerStyle);
        rowIndex = writeKeyValueRow(sheet, rowIndex, "Хугацаа", dateRange.label(), headerStyle);
        rowIndex = writeKeyValueRow(sheet, rowIndex, "Эхлэх огноо", dateRange.startDate(), headerStyle);
        rowIndex = writeKeyValueRow(sheet, rowIndex, "Дуусах огноо", dateRange.endDate(), headerStyle);
        rowIndex++;

        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            rowIndex = writeKeyValueRow(sheet, rowIndex, entry.getKey(), entry.getValue(), headerStyle);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeDailySheet(Sheet sheet, CellStyle headerStyle, List<Booking> bookings) {
        List<Map<String, Object>> dailyRows = buildDailyBreakdown(bookings);
        String[] headers = {"Date", "Total Bookings", "Confirmed", "Pending", "Cancelled", "Revenue"};
        writeHeader(sheet, headerStyle, headers);

        int rowIndex = 1;
        for (Map<String, Object> dailyRow : dailyRows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(String.valueOf(dailyRow.get("date")));
            row.createCell(1).setCellValue(asLong(dailyRow.get("totalBookings")));
            row.createCell(2).setCellValue(asLong(dailyRow.get("confirmedBookings")));
            row.createCell(3).setCellValue(asLong(dailyRow.get("pendingBookings")));
            row.createCell(4).setCellValue(asLong(dailyRow.get("cancelledBookings")));
            row.createCell(5).setCellValue(asBigDecimal(dailyRow.get("totalRevenue")).doubleValue());
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void writeBookingsSheet(Sheet sheet, CellStyle headerStyle, List<Booking> bookings) {
        String[] headers = {"Booking ID", "Date", "Time", "Status", "Approved", "Member", "Phone", "Amount", "Created At"};
        writeHeader(sheet, headerStyle, headers);

        int rowIndex = 1;
        for (Map<String, Object> bookingRow : buildBookingRows(bookings)) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(asLong(bookingRow.get("bookingId")));
            row.createCell(1).setCellValue(stringValue(bookingRow.get("date")));
            row.createCell(2).setCellValue(stringValue(bookingRow.get("time")));
            row.createCell(3).setCellValue(stringValue(bookingRow.get("status")));
            row.createCell(4).setCellValue(stringValue(bookingRow.get("approved")));
            row.createCell(5).setCellValue(stringValue(bookingRow.get("memberName")));
            row.createCell(6).setCellValue(stringValue(bookingRow.get("memberPhone")));
            row.createCell(7).setCellValue(asBigDecimal(bookingRow.get("amount")).doubleValue());
            row.createCell(8).setCellValue(stringValue(bookingRow.get("createdAt")));
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private int writeKeyValueRow(Sheet sheet, int rowIndex, String key, Object value, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell keyCell = row.createCell(0);
        keyCell.setCellValue(key);
        keyCell.setCellStyle(headerStyle);
        row.createCell(1).setCellValue(stringValue(value));
        return rowIndex + 1;
    }

    private void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private DateRange resolveDateRange(String period, String startDate, String endDate) {
        String normalizedPeriod = period == null ? "WEEK" : period.trim().toUpperCase();
        LocalDate today = LocalDate.now();

        return switch (normalizedPeriod) {
            case "TODAY" -> new DateRange("TODAY", today, today);
            case "WEEK" -> new DateRange(
                    "WEEK",
                    today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
                    today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
            );
            case "MONTH" -> {
                YearMonth yearMonth = YearMonth.from(today);
                yield new DateRange("MONTH", yearMonth.atDay(1), yearMonth.atEndOfMonth());
            }
            case "CUSTOM" -> {
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException("startDate болон endDate заавал ирнэ");
                }
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                validateDateRange(start, end);
                yield new DateRange("CUSTOM", start, end);
            }
            default -> throw new IllegalArgumentException("period утга TODAY, WEEK, MONTH эсвэл CUSTOM байна");
        };
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate нь startDate-ээс өмнө байж болохгүй");
        }
    }

    private long countByStatus(List<Booking> bookings, String status) {
        return bookings.stream().filter(booking -> statusEquals(booking, status)).count();
    }

    private boolean statusEquals(Booking booking, String status) {
        return booking.getStatus() != null && booking.getStatus().equalsIgnoreCase(status);
    }

    private BigDecimal sumRevenue(List<Booking> bookings) {
        return bookings.stream()
                .map(Booking::getTotalPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumRevenueByStatus(List<Booking> bookings, String status) {
        return bookings.stream()
                .filter(booking -> statusEquals(booking, status))
                .map(Booking::getTotalPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveMemberName(Booking booking) {
        if (booking.getUser() == null) {
            return "";
        }

        String firstName = booking.getUser().getFirstName();
        String lastName = booking.getUser().getLastName();
        String fullName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return fullName.isBlank() ? booking.getUser().getUsername() : fullName;
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "gym";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9-_]+", "-");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private record DateRange(String label, LocalDate startDate, LocalDate endDate) {
    }
}
