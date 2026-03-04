package com.userrole.auth.local;

import com.userrole.auth.spi.AuthProvider;
import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.ValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private AuthProvider authProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private PrintWriter writer;

    @Mock
    private FilterChain filterChain;

    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        jwtFilter = new JwtFilter(authProvider);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_whenNoAuthorizationHeader_passesFilterWithNoAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authProvider, never()).validate(any());
    }

    @Test
    void doFilterInternal_whenHeaderNotBearer_passesFilterWithNoAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authProvider, never()).validate(any());
    }

    @Test
    void doFilterInternal_whenValidBearerToken_populatesSecurityContext() throws Exception {
        String token = "valid.jwt.token";
        TokenClaims claims = new TokenClaims(1L, "alice", List.of("EDITOR"), Instant.now(), Instant.now().plusSeconds(900));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(authProvider.validate(token)).thenReturn(claims);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(claims);
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_EDITOR"));
    }

    @Test
    void doFilterInternal_whenValidBearerTokenWithAdminRole_includesRoleAdminAuthority() throws Exception {
        String token = "admin.jwt.token";
        TokenClaims claims = new TokenClaims(2L, "admin", List.of("ADMIN"), Instant.now(), Instant.now().plusSeconds(900));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(authProvider.validate(token)).thenReturn(claims);

        jwtFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void doFilterInternal_whenInvalidToken_sends401AndDoesNotPopulateContext() throws Exception {
        String token = "invalid.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(authProvider.validate(token)).thenThrow(new ValidationException("Token is invalid."));
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write(argThat((String body) -> body.contains("UNAUTHORIZED")));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_whenExpiredToken_sends401() throws Exception {
        String token = "expired.jwt.token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(authProvider.validate(token)).thenThrow(new ValidationException("Token expired."));
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write(argThat((String body) -> body.contains("UNAUTHORIZED")));
    }
}
