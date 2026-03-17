package com.equiflow.saga.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = buildUri(request);
        String user = extractUsername(request);

        log.info("→ {} {} [user:{}]", method, uri, user);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            // after filter chain, principal may be richer — prefer it if available
            String resolvedUser = request.getUserPrincipal() != null
                    ? request.getUserPrincipal().getName() : user;
            log.info("← {} {} {} [user:{}] ({}ms)", response.getStatus(), method, uri, resolvedUser, duration);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private String buildUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query != null ? uri + "?" + query : uri;
    }

    /**
     * Extracts the subject claim from a Bearer JWT without verifying the signature.
     * Used only for logging — crypto verification happens in JwtAuthFilter.
     */
    private String extractUsername(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return "-";
        try {
            String[] parts = auth.substring(7).split("[.]");
            if (parts.length < 2) return "-";
            String padded = parts[1].replace('-', '+').replace('_', '/');
            int mod = padded.length() % 4;
            if (mod == 2) padded += "==";
            else if (mod == 3) padded += "=";
            String json = new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
            int idx = json.indexOf("\"sub\":\"");
            if (idx < 0) return "-";
            int start = idx + 7;
            int end = json.indexOf('"', start);
            return end > start ? json.substring(start, end) : "-";
        } catch (Exception e) {
            return "-";
        }
    }
}
