package com.example.recruitmentsystem.controller;

import com.example.recruitmentsystem.model.Applicant;
import com.example.recruitmentsystem.model.Job;
import com.example.recruitmentsystem.model.User;
import com.example.recruitmentsystem.service.RecruitmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecruitmentController {

    @Autowired
    private RecruitmentService service;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        String recaptchaToken = credentials.get("recaptchaToken");
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            System.out.println("[Controller] Login failed: Empty fields");
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please fill all fields"));
        }
        Map<String, Object> result = service.loginUser(username.trim(), password, recaptchaToken);
        System.out.println("[Controller] Login response for " + username + ": " + result);
        return ResponseEntity.status(result.containsKey("success") && (boolean) result.get("success") ? 200 : 400).body(result);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        String userType = credentials.get("userType");
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty() || userType == null) {
            System.out.println("[Controller] Register failed: Empty fields");
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please fill all fields"));
        }
        Map<String, Object> response = service.registerUser(username.trim(), password, userType);
        System.out.println("[Controller] Register response for " + username + ": " + response);
        return ResponseEntity.status((boolean) response.get("success") ? 200 : 400).body(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        if (username == null || oldPassword == null || newPassword == null || newPassword.isEmpty()) {
            System.out.println("[Controller] Change password failed: Empty fields for " + username);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please fill all fields"));
        }
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Change password failed: Session expired for " + username);
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Session expired"));
        }
        Map<String, Object> response = service.changePassword(username, oldPassword, newPassword);
        System.out.println("[Controller] Change password response for " + username + ": " + response);
        return ResponseEntity.status((boolean) response.get("success") ? 200 : 400).body(response);
    }

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> addJob(@RequestBody Job job, @RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Add job failed: Session expired for " + username);
            return ResponseEntity.status(401).body(Map.of("message", "Session expired"));
        }
        try {
            if (job.getTitle() == null || job.getTitle().trim().isEmpty() ||
                    job.getDescription() == null || job.getDescription().trim().isEmpty() ||
                    job.getRequirements() == null || job.getRequirements().isEmpty() ||
                    job.getSalaryRange() == null || job.getSalaryRange().trim().isEmpty() ||
                    job.getDifficulty() == null || job.getDifficulty().trim().isEmpty() ||
                    job.getRequiredSkills() == null || job.getRequiredSkills().isEmpty() ||
                    job.getBenefits() == null || job.getBenefits().isEmpty()) {
                System.out.println("[Controller] Add job failed: Empty fields for " + username);
                return ResponseEntity.badRequest().body(Map.of("message", "Please fill all job fields"));
            }
            job.setRequirements(job.getRequirements().stream().map(String::trim).map(String::toLowerCase).toList());
            job.setRequiredSkills(job.getRequiredSkills().stream().map(String::trim).map(String::toLowerCase).toList());
            service.addJob(job, username);
            service.extendSession(username);
            System.out.println("[Controller] Job added by " + username);
            return ResponseEntity.ok(Map.of("message", "Job added"));
        } catch (IllegalStateException e) {
            System.out.println("[Controller] Add job failed: " + e.getMessage());
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/applicants")
    public ResponseEntity<Map<String, String>> addApplicant(@RequestBody Applicant applicant) {
        if (applicant.getName() == null || applicant.getName().trim().isEmpty() ||
                applicant.getSkills() == null || applicant.getSkills().isEmpty()) {
            System.out.println("[Controller] Add applicant failed: Empty fields");
            return ResponseEntity.badRequest().body(Map.of("message", "Please fill all applicant fields"));
        }
        applicant.setSkills(applicant.getSkills().stream().map(String::trim).map(String::toLowerCase).toList());
        service.addApplicant(applicant);
        System.out.println("[Controller] Applicant added: " + applicant.getName());
        return ResponseEntity.ok(Map.of("message", "Applicant registered"));
    }

    @GetMapping("/jobs")
    public List<Job> getJobs(@RequestParam(required = false) String salaryRange,
                             @RequestParam(required = false) String difficulty,
                             @RequestParam(required = false) String skill,
                             @RequestParam(required = false) Double minRating,
                             @RequestHeader(value = "X-Username", required = false) String username) {
        if (username != null && !service.isSessionValid(username)) {
            System.out.println("[Controller] Get jobs failed: Session expired for " + username);
            return List.of();
        }
        if (username != null) {
            service.extendSession(username);
        }
        List<Job> jobs = service.filterJobs(salaryRange, difficulty, skill, minRating);
        jobs.forEach(job -> {
            job.setEmployerRating(service.getAverageRating(job.getEmployerUsername()));
        });
        return jobs;
    }

    @GetMapping("/matches")
    public List<String> getMatches(@RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Get matches failed: Session expired for " + username);
            return List.of();
        }
        service.extendSession(username);
        System.out.println("[Controller] Fetching matches for " + username);
        return service.getMatches(username);
    }

    @GetMapping("/debug/users")
    public Map<String, User> getUsers(@RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Debug users failed: Session expired for " + username);
            return Map.of();
        }
        User user = service.getUserByUsername(username);
        if (user == null || !user.isAdmin()) {
            System.out.println("[Controller] Debug users failed: Not admin for " + username);
            return Map.of();
        }
        System.out.println("[Controller] Fetching users for admin " + username);
        return service.getAllUsers();
    }

    @GetMapping("/debug/lockout-status")
    public Map<String, Object> getLockoutStatus(@RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Debug lockout failed: " + username);
            return Map.of("error", "Session expired");
        }
        System.out.println("[Controller] Fetching lockout status for " + username);
        return service.getLockoutStatus(username);
    }

    @GetMapping("/session-time")
    public ResponseEntity<Map<String, Object>> getSessionTime(@RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Get session time failed: Session expired for " + username);
            return ResponseEntity.status(401).body(Map.of("remainingTime", 0));
        }
        long remainingTime = service.getRemainingSessionTime(username);
        System.out.println("[Controller] Session time for " + username + ": " + remainingTime + " seconds");
        return ResponseEntity.ok(Map.of("remainingTime", remainingTime));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("X-Username") String username) {
        service.invalidateSession(username);
        System.out.println("[Controller] Logout for " + username);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/ratings")
    public ResponseEntity<Map<String, Object>> addRating(@RequestBody Map<String, Object> request, @RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Add rating failed: Session expired for " + username);
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Session expired"));
        }
        String target = (String) request.get("target");
        int score = ((Number) request.get("score")).intValue();
        String review = (String) request.get("review");
        service.extendSession(username);
        return ResponseEntity.status(200).body(service.addRating(target, username, score, review));
    }

    @GetMapping("/ratings")
    public List<RecruitmentService.Rating> getRatings(@RequestParam String target, @RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Get ratings failed: Session expired for " + username);
            return List.of();
        }
        service.extendSession(username);
        return service.getRatings(target);
    }

    @PostMapping("/comments")
    public ResponseEntity<Map<String, Object>> addComment(@RequestBody Map<String, String> request, @RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Add comment failed: Session expired for " + username);
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Session expired"));
        }
        String jobId = request.get("jobId");
        String content = request.get("content");
        service.extendSession(username);
        return ResponseEntity.status(200).body(service.addComment(jobId, username, content));
    }

    @GetMapping("/comments")
    public List<RecruitmentService.Comment> getComments(@RequestParam String jobId, @RequestHeader(value = "X-Username", required = false) String username) {
        if (username != null && !service.isSessionValid(username)) {
            System.out.println("[Controller] Get comments failed: Session expired for " + username);
            return List.of();
        }
        if (username != null) {
            service.extendSession(username);
        }
        return service.getComments(jobId);
    }

    @GetMapping("/notifications")
    public List<RecruitmentService.Notification> getNotifications(@RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Get notifications failed: Session expired for " + username);
            return List.of();
        }
        service.extendSession(username);
        return service.getNotifications(username);
    }

    @PostMapping("/user/skills")
    public ResponseEntity<Map<String, Object>> updateSkills(@RequestBody Map<String, List<String>> request, @RequestHeader("X-Username") String username) {
        if (!service.isSessionValid(username)) {
            System.out.println("[Controller] Update skills failed: Session expired for " + username);
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Session expired"));
        }
        List<String> skills = request.get("skills");
        service.updateUserSkills(username, skills);
        service.extendSession(username);
        return ResponseEntity.ok(Map.of("success", true, "message", "Skills updated"));
    }
}