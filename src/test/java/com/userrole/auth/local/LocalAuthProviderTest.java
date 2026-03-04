package com.userrole.auth.local;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.auth.spi.TokenResponse;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.common.ValidationException;
import com.userrole.role.dto.RoleResponse;
import com.userrole.user.User;
import com.userrole.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalAuthProviderTest {

    @Mock
    private UserService userService;

    @Mock
    private StoredTokenRepository storedTokenRepository;

    private PasswordEncoder passwordEncoder;
    private LocalAuthProvider localAuthProvider;

    // A 32-byte (256-bit) Base64 key
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "testSecretKey123testSecretKey123".getBytes()
    );

    private User sampleUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        localAuthProvider = new LocalAuthProvider(
                userService,
                storedTokenRepository,
                passwordEncoder,
                TEST_SECRET,
                900L,
                604800L
        );

        sampleUser = new User("alice", passwordEncoder.encode("correct_password"),
                "Alice Smith", "Engineering", "alice@example.com", "555-1234");
        setUserId(sampleUser, 1L);
    }

    // ---- authenticate ----

    @Test
    void authenticate_whenCredentialsAreCorrect_returnsTokenResponse() {
        when(userService.findByUsername("alice")).thenReturn(sampleUser);
        when(userService.getRolesForUser(1L)).thenReturn(List.of());
        when(storedTokenRepository.save(any(StoredToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenResponse result = localAuthProvider.authenticate("alice", "correct_password");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    void authenticate_whenPasswordIsWrong_throwsValidationException() {
        when(userService.findByUsername("alice")).thenReturn(sampleUser);

        assertThatThrownBy(() -> localAuthProvider.authenticate("alice", "wrong_password"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void authenticate_whenUserNotFound_throwsResourceNotFoundException() {
        when(userService.findByUsername("unknown")).thenThrow(new ResourceNotFoundException("User not found."));

        assertThatThrownBy(() -> localAuthProvider.authenticate("unknown", "any"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authenticate_whenUserHasRoles_embedsRoleNamesInToken() {
        RoleResponse adminRole = new RoleResponse(1L, "ADMIN", null, Instant.now(), Instant.now(), List.of(), 0L);
        when(userService.findByUsername("alice")).thenReturn(sampleUser);
        when(userService.getRolesForUser(1L)).thenReturn(List.of(adminRole));
        when(storedTokenRepository.save(any(StoredToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenResponse result = localAuthProvider.authenticate("alice", "correct_password");

        // Validate the token contains the role
        TokenClaims claims = localAuthProvider.validate(result.accessToken());
        assertThat(claims.roles()).contains("ADMIN");
    }

    // ---- validate ----

    @Test
    void validate_whenTokenIsValid_returnsCorrectTokenClaims() {
        when(userService.findByUsername("alice")).thenReturn(sampleUser);
        when(userService.getRolesForUser(1L)).thenReturn(List.of());
        when(storedTokenRepository.save(any(StoredToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenResponse tokens = localAuthProvider.authenticate("alice", "correct_password");
        TokenClaims claims = localAuthProvider.validate(tokens.accessToken());

        assertThat(claims.userId()).isEqualTo(1L);
        assertThat(claims.username()).isEqualTo("alice");
    }

    @Test
    void validate_whenTokenIsTampered_throwsValidationException() {
        assertThatThrownBy(() -> localAuthProvider.validate("tampered.jwt.token"))
                .isInstanceOf(ValidationException.class);
    }

    // ---- refresh ----

    @Test
    void refresh_whenRefreshTokenIsValid_returnsNewAccessToken() {
        StoredToken storedToken = new StoredToken(1L, "valid-refresh-token", Instant.now().plusSeconds(604800));
        when(storedTokenRepository.findByRefreshToken("valid-refresh-token")).thenReturn(java.util.Optional.of(storedToken));
        when(userService.findById(1L)).thenReturn(sampleUser);
        when(userService.getRolesForUser(1L)).thenReturn(List.of());

        TokenResponse result = localAuthProvider.refresh("valid-refresh-token");

        assertThat(result.accessToken()).isNotBlank();
    }

    @Test
    void refresh_whenRefreshTokenIsRevoked_throwsValidationException() {
        StoredToken storedToken = new StoredToken(1L, "revoked-token", Instant.now().plusSeconds(604800));
        storedToken.setRevoked(true);
        when(storedTokenRepository.findByRefreshToken("revoked-token")).thenReturn(java.util.Optional.of(storedToken));

        assertThatThrownBy(() -> localAuthProvider.refresh("revoked-token"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void refresh_whenRefreshTokenIsExpired_throwsValidationException() {
        StoredToken storedToken = new StoredToken(1L, "expired-token", Instant.now().minusSeconds(1));
        when(storedTokenRepository.findByRefreshToken("expired-token")).thenReturn(java.util.Optional.of(storedToken));

        assertThatThrownBy(() -> localAuthProvider.refresh("expired-token"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refresh_whenRefreshTokenNotFound_throwsValidationException() {
        when(storedTokenRepository.findByRefreshToken("unknown")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> localAuthProvider.refresh("unknown"))
                .isInstanceOf(ValidationException.class);
    }

    // ---- invalidate ----

    @Test
    void invalidate_marksTokensAsRevoked() {
        when(userService.findByUsername("alice")).thenReturn(sampleUser);
        when(userService.getRolesForUser(1L)).thenReturn(List.of());
        when(storedTokenRepository.save(any(StoredToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenResponse tokens = localAuthProvider.authenticate("alice", "correct_password");
        localAuthProvider.invalidate(tokens.accessToken());

        verify(storedTokenRepository).revokeAllByUserId(1L);
    }

    // ---- helpers ----

    private void setUserId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
