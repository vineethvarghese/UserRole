package com.userrole.auth.local;

import com.userrole.auth.spi.AuthProvider;
import com.userrole.auth.spi.TokenResponse;
import com.userrole.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.userrole.auth.spi.TokenClaims;

/**
 * REST controller for authentication endpoints.
 * Delegates all logic to AuthProvider — no direct dependency on
 * LocalAuthProvider.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthProvider authProvider;

    public AuthController(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = authProvider.authenticate(request.username(), request.password());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse tokens = authProvider.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal TokenClaims claims,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring("Bearer ".length());
        authProvider.invalidate(token);
        return ResponseEntity.noContent().build();
    }
}
