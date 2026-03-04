package com.userrole.auth.local;

import com.userrole.auth.spi.AuthProvider;
import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.ServletErrorResponseWriter;
import com.userrole.common.ValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter. Extracts the Bearer token from the Authorization header,
 * validates it via AuthProvider, and populates the SecurityContextHolder.
 *
 * Depends on AuthProvider interface only — no knowledge of LocalAuthProvider.
 */
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";

    private final AuthProvider authProvider;

    public JwtFilter(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No Bearer token present — let the security chain handle the anonymous request
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            TokenClaims claims = authProvider.validate(token);

            // Map role names to GrantedAuthority with ROLE_ prefix (Spring Security convention)
            List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (ValidationException | io.jsonwebtoken.JwtException e) {
            SecurityContextHolder.clearContext();
            ServletErrorResponseWriter.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "Invalid or expired token.");
        }
    }
}
