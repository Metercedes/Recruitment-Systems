package com.example.recruitmentsystem.controller;

import com.example.recruitmentsystem.dto.AuthDtos.ChangePasswordRequest;
import com.example.recruitmentsystem.dto.AuthDtos.LoginRequest;
import com.example.recruitmentsystem.dto.AuthDtos.RegisterRequest;
import com.example.recruitmentsystem.dto.AuthDtos.SessionResponse;
import com.example.recruitmentsystem.model.User;
import com.example.recruitmentsystem.service.UserDirectory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private final AuthenticationManager authenticationManager;
    private final UserDirectory directory;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserDirectory directory) {
        this.authenticationManager = authenticationManager;
        this.directory = directory;
    }

    /**
     * Issues the CSRF cookie before the first state-changing request. The front end calls this on
     * page load so a login POST already has a token to echo back.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   jakarta.servlet.http.HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, httpRequest, httpResponse);

            directory.recordSuccess(request.username());
            User user = directory.find(request.username()).orElseThrow();
            return ResponseEntity.ok(new SessionResponse(
                    user.getUsername(), user.isAdmin(), user.getUserType(),
                    directory.consumeWasLockedFlag(user.getUsername())));

        } catch (LockedException e) {
            // Deliberately the same response as a wrong password. Confirming that an account is
            // locked confirms that it exists, and the original code reported both the lockout and
            // the attempt count, which told an attacker exactly where they were.
            return rejected(request.username());
        } catch (AuthenticationException e) {
            directory.recordFailure(request.username());
            return rejected(request.username());
        }
    }

    private ResponseEntity<Map<String, String>> rejected(String username) {
        securityLog.warn("Authentication rejected for {}", username);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password"));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String error = directory.register(request.username(), request.password(), request.userType());
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Registration successful"));
    }

    /**
     * Changes the caller's own password. The account comes from the authenticated principal, so
     * the request body cannot name a different user.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        String error = directory.changePassword(
                principal.getName(), request.currentPassword(), request.newPassword());
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(Principal principal) {
        return directory.find(principal.getName())
                .map(user -> ResponseEntity.ok(new SessionResponse(
                        user.getUsername(), user.isAdmin(), user.getUserType(), false)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
