package com.suabhi.cabbooking.identity.api;

import com.suabhi.cabbooking.identity.domain.AppUserPrincipal;
import com.suabhi.cabbooking.identity.service.CustomerAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final CustomerAccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    public AuthController(CustomerAccountService accountService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CookieCsrfTokenRepository csrfTokenRepository) {
        this.accountService = accountService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        CustomerAccountService.CustomerProfile profile = accountService.register(
                request.email(), request.fullName(), request.phoneE164(), request.password());

        AuthUserResponse response = new AuthUserResponse(profile.id(), profile.email(), profile.fullName(),
                false, List.of("CUSTOMER"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(UriComponentsBuilder.fromPath("/api/v1/customer/profile").build().toUri())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            String email = request.email().strip().toLowerCase(Locale.ROOT);
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new AuthError(Instant.now(), 401, "Invalid email or password."));
        }

        servletRequest.getSession(true);
        servletRequest.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(AuthUserResponse.from(principal));
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        CustomerAccountService.CustomerProfile profile = accountService.findProfile(principal.getId());
        return new AuthUserResponse(profile.id(), profile.email(), profile.fullName(),
                profile.emailVerified(), principal.getRoleCodes().stream().sorted().toList());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 2, max = 160) String fullName,
            @Pattern(regexp = "^$|\\+[1-9][0-9]{7,14}",
                    message = "Enter a phone number in international format, for example +919876543210.")
            String phoneE164,
            @NotBlank @Size(min = 12, max = 72) String password) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {}

    public record AuthUserResponse(java.util.UUID id, String email, String fullName,
                                   boolean emailVerified, List<String> roles) {
        static AuthUserResponse from(AppUserPrincipal principal) {
            return new AuthUserResponse(principal.getId(), principal.getEmail(),
                    principal.getFullName(), principal.isEmailVerified(),
                    principal.getRoleCodes().stream().sorted().toList());
        }
    }

    public record AuthError(Instant timestamp, int status, String message) {}
}
