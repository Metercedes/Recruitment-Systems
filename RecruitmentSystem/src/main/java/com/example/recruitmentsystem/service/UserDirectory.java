package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Accounts, credentials and lockout state.
 *
 * <p>Separated out of {@code RecruitmentService} so that Spring Security has one place to read
 * accounts from, and so the credential handling is small enough to review on its own.
 *
 * <p>Storage is in memory, which is what the original project did. It is a demonstration
 * application; a persistent user store is noted as a limitation rather than pretended away.
 */
@Service
public class UserDirectory {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private static final Pattern PASSWORD_POLICY = Pattern.compile(
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!])(?=\\S+$).{12,}$");
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserDirectory(PasswordEncoder passwordEncoder,
                         EncryptionService encryptionService,
                         @Value("${app.admin.username:}") String adminUsername,
                         @Value("${app.admin.password:}") String adminPassword) {
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
        seedAdministrator(adminUsername, adminPassword);
    }

    /**
     * Creates the administrator only when credentials are supplied.
     *
     * <p>The original code seeded {@code admin} with the password {@code admin123} compiled into
     * the source, so every deployment shared a publicly known administrator. There is no default
     * here: without configuration the application simply has no administrator.
     */
    private void seedAdministrator(String username, String password) {
        if (username.isBlank() || password.isBlank()) {
            securityLog.info("No administrator configured; set app.admin.username and app.admin.password to create one");
            return;
        }
        String policyError = checkPasswordPolicy(password);
        if (policyError != null) {
            throw new IllegalStateException("Configured administrator password is too weak: " + policyError);
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setAdmin(true);
        admin.setUserType("employer");
        users.put(username, admin);
        securityLog.info("Administrator account created for configured username");
    }

    public Optional<User> find(String username) {
        return Optional.ofNullable(users.get(username));
    }

    public Collection<User> all() {
        return users.values();
    }

    public boolean exists(String username) {
        return users.containsKey(username);
    }

    public String checkPasswordPolicy(String password) {
        if (password == null || password.isBlank()) {
            return "Password is required";
        }
        if (!PASSWORD_POLICY.matcher(password).matches()) {
            return "Password must be at least 12 characters and contain an upper-case letter, "
                    + "a lower-case letter, a digit and one of @#$%^&+=!";
        }
        return null;
    }

    public String register(String username, String password, String userType) {
        if (username == null || !username.matches("^[A-Za-z0-9._-]{3,32}$")) {
            return "Username must be 3-32 characters using letters, digits, dot, underscore or hyphen";
        }
        if (!"employer".equals(userType) && !"jobseeker".equals(userType)) {
            return "Account type must be employer or jobseeker";
        }
        String policyError = checkPasswordPolicy(password);
        if (policyError != null) {
            return policyError;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setAdmin(false);
        user.setUserType(userType);
        user.setEncryptedPersonalData(encryptionService.encrypt(emptyPersonalData()));

        // Registration must not overwrite an existing account, and must not report whether one
        // exists either: both would let an unauthenticated caller enumerate usernames.
        if (users.putIfAbsent(username, user) != null) {
            securityLog.info("Registration rejected for an existing username");
            return "Registration could not be completed";
        }
        securityLog.info("Account registered: {}", username);
        return null;
    }

    private String emptyPersonalData() {
        Map<String, Object> data = new HashMap<>();
        data.put("skills", java.util.List.of());
        data.put("personalId", "");
        data.put("phoneNumber", "");
        data.put("address", "");
        return objectMapper.writeValueAsString(data);
    }

    public String changePassword(String username, String currentPassword, String newPassword) {
        User user = users.get(username);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            securityLog.warn("Password change rejected for {}: current password did not match", username);
            return "Current password is incorrect";
        }
        String policyError = checkPasswordPolicy(newPassword);
        if (policyError != null) {
            return policyError;
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return "New password must differ from the current one";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        securityLog.info("Password changed for {}", username);
        return null;
    }

    public boolean isLockedOut(String username) {
        Instant until = lockedUntil.get(username);
        if (until == null) {
            return false;
        }
        if (until.isAfter(Instant.now())) {
            return true;
        }
        lockedUntil.remove(username);
        failedAttempts.remove(username);
        return false;
    }

    public void recordFailure(String username) {
        int attempts = failedAttempts.merge(username, 1, Integer::sum);
        if (attempts >= MAX_ATTEMPTS) {
            lockedUntil.put(username, Instant.now().plus(LOCKOUT_DURATION));
            failedAttempts.remove(username);
            find(username).ifPresent(user -> user.setWasLocked(true));
            securityLog.warn("Account locked after {} failed attempts: {}", attempts, username);
        } else {
            securityLog.info("Failed authentication for {} ({} of {})", username, attempts, MAX_ATTEMPTS);
        }
    }

    public void recordSuccess(String username) {
        failedAttempts.remove(username);
        lockedUntil.remove(username);
        securityLog.info("Successful authentication for {}", username);
    }

    public boolean consumeWasLockedFlag(String username) {
        return find(username).map(user -> {
            boolean wasLocked = user.isWasLocked();
            user.setWasLocked(false);
            return wasLocked;
        }).orElse(false);
    }

    public void delete(String username) {
        users.remove(username);
        failedAttempts.remove(username);
        lockedUntil.remove(username);
        securityLog.info("Account deleted: {}", username);
    }
}
