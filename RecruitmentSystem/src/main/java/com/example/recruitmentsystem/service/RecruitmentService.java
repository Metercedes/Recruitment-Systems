package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.model.Applicant;
import com.example.recruitmentsystem.model.Job;
import com.example.recruitmentsystem.model.User;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RecruitmentService {
    private Map<String, User> users = new HashMap<>();
    private List<Job> jobs = new ArrayList<>();
    private List<Applicant> applicants = new ArrayList<>();
    private Map<String, Integer> failedAttempts = new HashMap<>();
    private Map<String, LocalDateTime> lockoutTimes = new HashMap<>();
    private Map<String, LocalDateTime> sessionExpirations = new HashMap<>();
    private List<Rating> ratings = new ArrayList<>();
    private List<Comment> comments = new ArrayList<>();
    private Map<String, List<Notification>> notifications = new HashMap<>();
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION_MINUTES = 2;
    private static final long SESSION_TIMEOUT_MINUTES = 15;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
    );

    // Inner classes for Rating, Comment, Notification
    public static class Rating {
        private String target;
        private String rater;
        private int score;
        private String review;
        private LocalDateTime timestamp;

        public Rating(String target, String rater, int score, String review) {
            this.target = target;
            this.rater = rater;
            this.score = score;
            this.review = review;
            this.timestamp = LocalDateTime.now();
        }

        public String getTarget() { return target; }
        public String getRater() { return rater; }
        public int getScore() { return score; }
        public String getReview() { return review; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class Comment {
        private String jobId;
        private String commenter;
        private String content;
        private LocalDateTime timestamp;

        public Comment(String jobId, String commenter, String content) {
            this.jobId = jobId;
            this.commenter = commenter;
            this.content = content;
            this.timestamp = LocalDateTime.now();
        }

        public String getJobId() { return jobId; }
        public String getCommenter() { return commenter; }
        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class Notification {
        private String user;
        private String jobId;
        private String message;
        private LocalDateTime timestamp;

        public Notification(String user, String jobId, String message) {
            this.user = user;
            this.jobId = jobId;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        public String getUser() { return user; }
        public String getJobId() { return jobId; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public RecruitmentService() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(BCrypt.hashpw("admin123", BCrypt.gensalt()));
        admin.setAdmin(true);
        admin.setWasLocked(false);
        admin.setUserType("employer");
        users.put("admin", admin);
    }

    private String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty";
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return "Password must be at least 8 characters, with 1 uppercase letter, 1 number, and 1 special character (@#$%^&+=!)";
        }
        return null;
    }

    public Map<String, Object> loginUser(String username, String password, String recaptchaToken) {
        System.out.println("[Login] Attempt for user: " + username);

        // Mock reCAPTCHA validation
        if (recaptchaToken == null || !recaptchaToken.equals("mock-recaptcha-token")) {
            System.out.println("[Login] reCAPTCHA validation failed for " + username);
            return Map.of("success", false, "message", "reCAPTCHA verification failed");
        }

        if (lockoutTimes.containsKey(username)) {
            LocalDateTime lockoutTime = lockoutTimes.get(username);
            if (lockoutTime.plusMinutes(LOCKOUT_DURATION_MINUTES).isAfter(LocalDateTime.now())) {
                long secondsRemaining = java.time.Duration.between(LocalDateTime.now(), lockoutTime.plusMinutes(LOCKOUT_DURATION_MINUTES)).getSeconds();
                System.out.println("[Login] Account locked for " + username + ", seconds remaining: " + secondsRemaining);
                return Map.of("success", false, "message", "Account locked. Try again in " + secondsRemaining + " seconds");
            }
            System.out.println("[Login] Lockout expired for " + username);
            lockoutTimes.remove(username);
        }

        User user = users.get(username);
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            int attempts = failedAttempts.getOrDefault(username, 0) + 1;
            failedAttempts.put(username, attempts);
            System.out.println("[Login] Failed attempt " + attempts + "/" + MAX_ATTEMPTS + " for " + username);
            if (attempts >= MAX_ATTEMPTS) {
                lockoutTimes.put(username, LocalDateTime.now());
                if (user != null) {
                    user.setWasLocked(true);
                }
                failedAttempts.remove(username);
                System.out.println("[Login] Lockout triggered for " + username);
                return Map.of("success", false, "message", "Too many failed attempts. Account locked for 2 minutes");
            }
            return Map.of("success", false, "message", "Invalid credentials. Attempt " + attempts + "/" + MAX_ATTEMPTS);
        }

        boolean wasLocked = user.isWasLocked();
        System.out.println("[Login] Success for " + username + ", wasLocked: " + wasLocked);
        failedAttempts.remove(username);
        lockoutTimes.remove(username);
        user.setWasLocked(false);
        sessionExpirations.put(username, LocalDateTime.now().plusMinutes(SESSION_TIMEOUT_MINUTES));
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("isAdmin", user.isAdmin());
        response.put("username", user.getUsername());
        response.put("wasLocked", wasLocked);
        response.put("userType", user.getUserType());
        response.put("sessionTimeout", SESSION_TIMEOUT_MINUTES * 60);
        System.out.println("[Login] Response: " + response);
        return response;
    }

    public Map<String, Object> registerUser(String username, String password, String userType) {
        System.out.println("[Register] Attempt for user: " + username);
        if (users.containsKey(username)) {
            System.out.println("[Register] Username exists: " + username);
            return Map.of("success", false, "message", "Username already exists");
        }
        String passwordError = validatePassword(password);
        if (passwordError != null) {
            System.out.println("[Register] Password validation failed: " + passwordError);
            return Map.of("success", false, "message", passwordError);
        }
        if (!userType.equals("employer") && !userType.equals("jobseeker")) {
            System.out.println("[Register] Invalid user type: " + userType);
            return Map.of("success", false, "message", "Invalid user type");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setAdmin(false);
        user.setWasLocked(false);
        user.setUserType(userType);
        users.put(username, user);
        System.out.println("[Register] User created: " + username);
        return Map.of("success", true, "message", "Registration successful");
    }

    public Map<String, Object> changePassword(String username, String oldPassword, String newPassword) {
        System.out.println("[ChangePassword] Attempt for user: " + username);
        User user = users.get(username);
        if (user == null || !BCrypt.checkpw(oldPassword, user.getPassword())) {
            System.out.println("[ChangePassword] Invalid credentials for " + username);
            return Map.of("success", false, "message", "Invalid current password");
        }
        String passwordError = validatePassword(newPassword);
        if (passwordError != null) {
            System.out.println("[ChangePassword] Password validation failed: " + passwordError);
            return Map.of("success", false, "message", passwordError);
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        System.out.println("[ChangePassword] Password updated for " + username);
        return Map.of("success", true, "message", "Password changed successfully");
    }

    public User getUserByUsername(String username) {
        return users.get(username);
    }

    public void addJob(Job job, String username) {
        User user = users.get(username);
        if (user == null || (!user.isAdmin() && !user.getUserType().equals("employer"))) {
            throw new IllegalStateException("Only admins or employers can add jobs");
        }
        job.setId(UUID.randomUUID().toString());
        job.setEmployerUsername(username);
        jobs.add(job);
        for (User u : users.values()) {
            if (u.getUserType().equals("jobseeker") && u.getSkills() != null) {
                boolean matches = u.getSkills().stream().anyMatch(skill -> job.getRequiredSkills().contains(skill));
                if (matches) {
                    notifications.computeIfAbsent(u.getUsername(), k -> new ArrayList<>())
                            .add(new Notification(u.getUsername(), job.getId(), "New job matches your skills: " + job.getTitle()));
                }
            }
        }
    }

    public void addApplicant(Applicant applicant) {
        applicants.add(applicant);
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public List<Job> filterJobs(String salaryRange, String difficulty, String skill, Double minRating) {
        return jobs.stream()
                .filter(job -> {
                    boolean matches = true;
                    if (salaryRange != null && !salaryRange.isEmpty()) {
                        matches = matches && job.getSalaryRange().equalsIgnoreCase(salaryRange);
                    }
                    if (difficulty != null && !difficulty.isEmpty()) {
                        matches = matches && job.getDifficulty().equalsIgnoreCase(difficulty);
                    }
                    if (skill != null && !skill.isEmpty()) {
                        matches = matches && job.getRequiredSkills().contains(skill.toLowerCase());
                    }
                    if (minRating != null) {
                        double avgRating = getAverageRating(job.getEmployerUsername());
                        matches = matches && avgRating >= minRating;
                    }
                    return matches;
                })
                .collect(Collectors.toList());
    }

    public List<String> getMatches(String username) {
        List<String> results = new ArrayList<>();
        User user = users.get(username);
        if (user == null) return results;

        for (Applicant app : applicants) {
            results.add("Matches for " + app.getName() + ":");
            boolean matched = false;
            for (Job job : jobs) {
                if (app.getSkills().stream().anyMatch(skill -> job.getRequiredSkills().contains(skill))) {
                    results.add("  ✔ " + job.getTitle());
                    matched = true;
                }
            }
            if (!matched) {
                results.add("  ┃ No matches found.");
            }
        }
        return results;
    }

    public Map<String, User> getAllUsers() {
        return users;
    }

    public Map<String, Object> getLockoutStatus(String username) {
        Map<String, Object> status = new HashMap<>();
        status.put("username", username);
        status.put("failedAttempts", failedAttempts.getOrDefault(username, 0));
        status.put("isLocked", lockoutTimes.containsKey(username));
        if (lockoutTimes.containsKey(username)) {
            status.put("lockoutUntil", lockoutTimes.get(username).plusMinutes(LOCKOUT_DURATION_MINUTES));
        }
        User user = users.get(username);
        if (user != null) {
            status.put("wasLocked", user.isWasLocked());
        }
        return status;
    }

    public boolean isSessionValid(String username) {
        if (!sessionExpirations.containsKey(username)) {
            System.out.println("[isSessionValid] No session for " + username);
            return false;
        }
        if (sessionExpirations.get(username).isBefore(LocalDateTime.now())) {
            sessionExpirations.remove(username);
            System.out.println("[isSessionValid] Session expired for " + username);
            return false;
        }
        System.out.println("[isSessionValid] Session valid for " + username);
        return true;
    }

    public long getRemainingSessionTime(String username) {
        if (!sessionExpirations.containsKey(username)) {
            System.out.println("[getRemainingSessionTime] No session for " + username);
            return 0;
        }
        LocalDateTime expiration = sessionExpirations.get(username);
        if (expiration.isBefore(LocalDateTime.now())) {
            sessionExpirations.remove(username);
            System.out.println("[getRemainingSessionTime] Session expired for " + username);
            return 0;
        }
        long remainingTime = java.time.Duration.between(LocalDateTime.now(), expiration).getSeconds();
        System.out.println("[getRemainingSessionTime] Remaining time for " + username + ": " + remainingTime + " seconds");
        return remainingTime;
    }

    public void extendSession(String username) {
        if (users.containsKey(username)) {
            sessionExpirations.put(username, LocalDateTime.now().plusMinutes(SESSION_TIMEOUT_MINUTES));
            System.out.println("[extendSession] Session extended for " + username);
        }
    }

    public void invalidateSession(String username) {
        sessionExpirations.remove(username);
        System.out.println("[invalidateSession] Session invalidated for " + username);
    }

    public Map<String, Object> addRating(String target, String rater, int score, String review) {
        if (!users.containsKey(target) || !users.containsKey(rater)) {
            return Map.of("success", false, "message", "User not found");
        }
        if (score < 1 || score > 5) {
            return Map.of("success", false, "message", "Score must be between 1 and 5");
        }
        ratings.add(new Rating(target, rater, score, review));
        return Map.of("success", true, "message", "Rating submitted");
    }

    public List<Rating> getRatings(String target) {
        return ratings.stream()
                .filter(r -> r.getTarget().equals(target))
                .collect(Collectors.toList());
    }

    public double getAverageRating(String target) {
        List<Rating> targetRatings = getRatings(target);
        if (targetRatings.isEmpty()) return 0.0;
        return targetRatings.stream().mapToInt(Rating::getScore).average().orElse(0.0);
    }

    public Map<String, Object> addComment(String jobId, String commenter, String content) {
        if (!jobs.stream().anyMatch(j -> j.getId().equals(jobId))) {
            return Map.of("success", false, "message", "Job not found");
        }
        if (!users.containsKey(commenter)) {
            return Map.of("success", false, "message", "User not found");
        }
        if (content == null || content.trim().isEmpty()) {
            return Map.of("success", false, "message", "Comment cannot be empty");
        }
        comments.add(new Comment(jobId, commenter, content));
        return Map.of("success", true, "message", "Comment added");
    }

    public List<Comment> getComments(String jobId) {
        return comments.stream()
                .filter(c -> c.getJobId().equals(jobId))
                .collect(Collectors.toList());
    }

    public List<Notification> getNotifications(String username) {
        return notifications.getOrDefault(username, new ArrayList<>());
    }

    public void clearNotifications(String username) {
        notifications.remove(username);
    }

    public void updateUserSkills(String username, List<String> skills) {
        User user = users.get(username);
        if (user != null) {
            user.setSkills(skills.stream().map(String::toLowerCase).collect(Collectors.toList()));
        }
    }
}