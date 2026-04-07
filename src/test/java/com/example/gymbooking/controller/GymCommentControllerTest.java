package com.example.gymbooking.controller;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.GymComment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.GymCommentRepository;
import com.example.gymbooking.repository.GymRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GymCommentControllerTest {

    @Mock
    private GymCommentRepository gymCommentRepository;

    @Mock
    private GymRepository gymRepository;

    @InjectMocks
    private GymCommentController gymCommentController;

    private User user;
    private Gym gym;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setUsername("test-user");

        gym = new Gym();
        gym.setId(1L);
        gym.setName("Fit Gym");
        gym.setCapacity(50);
    }

    @Test
    void addCommentByGym_shouldSaveCommentToDatabase() {
        when(gymRepository.findById(1L)).thenReturn(Optional.of(gym));
        when(gymCommentRepository.save(any(GymComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = gymCommentController.addCommentByGym(1L, user, Map.of("comment", "Great gym"));

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<GymComment> captor = ArgumentCaptor.forClass(GymComment.class);
        verify(gymCommentRepository, times(1)).save(captor.capture());

        GymComment saved = captor.getValue();
        assertEquals("Great gym", saved.getComment());
        assertEquals(user, saved.getUser());
        assertEquals(gym, saved.getGym());
    }

    @Test
    void getGymComments_shouldReturnCommentsVisibleToAllUsers() {
        GymComment comment1 = new GymComment();
        comment1.setComment("First");
        comment1.setGym(gym);
        comment1.setUser(user);

        GymComment comment2 = new GymComment();
        comment2.setComment("Second");
        comment2.setGym(gym);
        comment2.setUser(user);

        when(gymRepository.existsById(1L)).thenReturn(true);
        when(gymCommentRepository.findByGymIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(comment1, comment2));

        ResponseEntity<?> response = gymCommentController.getGymComments(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(comment1, comment2), response.getBody());
    }

    @Test
    void deleteOwnCommentByCommentId_shouldDeleteWhenOwnedByUser() {
        GymComment comment = new GymComment();
        comment.setId(11L);
        comment.setGym(gym);
        comment.setUser(user);
        comment.setComment("to delete");

        when(gymCommentRepository.findByIdAndUserId(11L, 10L)).thenReturn(Optional.of(comment));

        ResponseEntity<?> response = gymCommentController.deleteOwnComment(11L, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(gymCommentRepository, times(1)).delete(comment);
    }

    @Test
    void deleteOwnCommentByGymAndCommentId_shouldReturnForbiddenForOtherUser() {
        User anotherUser = new User();
        anotherUser.setId(99L);

        GymComment comment = new GymComment();
        comment.setId(11L);
        comment.setGym(gym);
        comment.setUser(user);

        when(gymCommentRepository.findByIdAndGymId(11L, 1L)).thenReturn(Optional.of(comment));

        ResponseEntity<?> response = gymCommentController.deleteOwnComment(1L, 11L, anotherUser);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(gymCommentRepository, never()).delete(any(GymComment.class));
    }
}
