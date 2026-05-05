package com.example.gymbooking.security;

import com.example.gymbooking.config.JwtUtil;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend.oauth2-success-url:http://localhost:3000/login/callback}")
    private String frontendSuccessUrl;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 authentication failed");
            return;
        }
        String email = oauthUser.getAttribute("email");

        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Google account email is required");
            return;
        }

        User user = findOrCreateUser(oauthUser, email.trim());

        String tokenSubject = Optional.ofNullable(user.getUsername())
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(user.getEmail())
                        .filter(value -> !value.isBlank())
                        .orElse(email));

        String jwt = jwtUtil.generateToken(tokenSubject);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendSuccessUrl)
                .queryParam("token", jwt)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private User findOrCreateUser(OAuth2User oauthUser, String email) {
        return userRepository.findByEmailNormalized(email)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setUsername(email);
                    user.setFirstName(oauthUser.getAttribute("given_name"));
                    user.setLastName(oauthUser.getAttribute("family_name"));
                    user.setVerified(true);
                    user.setRole("USER");
                    return userRepository.save(user);
                });
    }
}
