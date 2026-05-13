package com.example.gymbooking.security;

import com.example.gymbooking.config.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.annotation.Order;

import java.io.IOException;

@Component
@Order(2)
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = null;
        String username = null;

        LOGGER.info("=== JWT FILTER DEBUG START ===");
        LOGGER.info("REQUEST URI: {}", request.getRequestURI());
        LOGGER.info("AUTH HEADER: {}", authorizationHeader);
        
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
            LOGGER.info("EXTRACTED TOKEN: {}", token.substring(0, Math.min(token.length(), 20)) + "...");
            
            try {
                if (jwtUtil.validateToken(token)) {
                    username = jwtUtil.getUsernameFromToken(token);
                    LOGGER.info("VALIDATED USERNAME: {}", username);
                } else {
                    LOGGER.warn("TOKEN VALIDATION FAILED");
                }
            } catch (Exception ex) {
                LOGGER.error("JWT validation failed: {}", ex.getMessage(), ex);
            }
        } else {
            LOGGER.warn("NO BEARER TOKEN FOUND");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                LOGGER.info("SETTING AUTHENTICATION FOR USER: {}", username);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                LOGGER.info("LOADED USER DETAILS: {}", userDetails.getUsername());
                
                if (token != null && jwtUtil.validateToken(token)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    LOGGER.info("AUTHENTICATION SET SUCCESSFULLY FOR: {}", username);
                } else {
                    LOGGER.warn("TOKEN VALIDATION FAILED DURING AUTH SET");
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to set authentication for user {}: {}", username, ex.getMessage(), ex);
            }
        } else {
            LOGGER.info("NO AUTHENTICATION SET - USERNAME: {}, EXISTING AUTH: {}", 
                username, SecurityContextHolder.getContext().getAuthentication() != null);
        }

        LOGGER.info("=== JWT FILTER DEBUG END ===");
        filterChain.doFilter(request, response);
    }
}
