package com.example.chat.security;

import com.example.chat.service.RedisBaseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisBaseService redisBaseService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            setErrorResponse(response, "Token invalid", 401);
            return;
        }

        String token = authHeader.substring(7);

        if (redisBaseService.isTokenBlacklisted(token)) {
            setErrorResponse(response, "Token has been revoked", 401);
            return;
        }

        if (!jwtTokenProvider.isValidAccessToken(token)) {
            setErrorResponse(response, "Token expired", 401);
            return;
        }

        Long accountId = jwtTokenProvider.getAccountIdFromAccessToken(token);
        String role = jwtTokenProvider.getRoleFromAccessToken(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        accountId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        List<String> excludedPaths = List.of(
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/google",
                "/api/auth/send-otp",
                "/api/auth/verify-account",
                "/api/auth/forgot-password",
                "/api/auth/verify-otp",
                "/api/auth/reset-password",
                "/api/auth/refresh-accessToken",
                "/api/auth/logout",
                "/ws",
                "/payment/callback",

                // Swagger & OpenAPI endpoints
                "/swagger-ui",
                "/v3/api-docs",
                "/swagger-resources",
                "/webjars"
        );
        return excludedPaths.stream().anyMatch(path::startsWith);
    }

    private void setErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String json = String.format("""
        {
            "timestamp": "%s",
            "status": %d,
            "error": "Unauthorized",
            "message": "%s"
        }
        """, java.time.LocalDateTime.now(), status, message);

        response.getWriter().write(json);
    }

}
