package com.example.gymbooking.controller;

import com.example.gymbooking.dto.CreatePaymentRequest;
import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Payment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.PaymentRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.service.NotificationService;
import com.example.gymbooking.service.QPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private QPayService qPayService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SlotRepository slotRepository;

    private PaymentController paymentController;

    private Booking booking;

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(
                paymentRepository,
                bookingRepository,
                qPayService,
                notificationService,
                slotRepository,
                true
        );

        User user = new User();
        user.setId(77L);

        booking = new Booking();
        booking.setId(10L);
        booking.setUser(user);
    }

    @Test
    void createPayment_shouldAcceptNestedBookingObjectAndFillUserId() {
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentRequest request = new CreatePaymentRequest();
        CreatePaymentRequest.BookingReference bookingReference = new CreatePaymentRequest.BookingReference();
        bookingReference.setId(10L);
        request.setBooking(bookingReference);
        request.setAmount(new BigDecimal("12000.00"));
        request.setPaymentMethod("CARD");

        ResponseEntity<?> response = paymentController.createPayment(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Payment);

        Payment saved = (Payment) response.getBody();
        assertEquals(new BigDecimal("12000.00"), saved.getAmount());
        assertEquals("CARD", saved.getPaymentMethod());
        assertEquals(77L, saved.getUserId());
        assertEquals("PENDING", saved.getStatus());
    }

    @Test
    void createPayment_shouldReturnBadRequestWhenUserIdCannotBeResolved() {
        Booking bookingWithoutUser = new Booking();
        bookingWithoutUser.setId(10L);

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(bookingWithoutUser));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setBookingId(10L);
        request.setAmount(new BigDecimal("15000"));

        ResponseEntity<?> response = paymentController.createPayment(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(java.util.Map.of("error", "userId is required"), response.getBody());
    }
}
