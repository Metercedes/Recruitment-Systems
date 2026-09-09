package com.example.recruitmentsystem.controller;

import com.example.recruitmentsystem.model.Applicant;
import com.example.recruitmentsystem.model.Job;
import com.example.recruitmentsystem.model.User;
import com.example.recruitmentsystem.service.RecruitmentService;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint that acts on behalf of a user takes the account from {@link Principal}.
 *
 * <p>The original controller took it from an {@code X-Username} request header on each method,
 * which meant the caller chose who they were. Authorisation decisions are made against the
 * authenticated principal only, and role checks are declared with {@code @PreAuthorize} so the
 * framework enforces them before the method body runs.
 */
@RestController
@RequestMapping("/api")
public class RecruitmentController {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentController.class);

    private final RecruitmentService service;

    public RecruitmentController(RecruitmentService service) {
        this.service = service;
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> addJob(@RequestBody Job job, Principal principal) {
        String username = principal.getName();
        if (isIncomplete(job)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please fill all job fields"));
        }
        job.setRequirements(normalise(job.getRequirements()));
        job.setRequiredSkills(normalise(job.getRequiredSkills()));
        try {
            service.addJob(job, username);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
        log.info("Job posted by {}", username);
        return ResponseEntity.ok(Map.of("message", "Job added"));
    }

    private boolean isIncomplete(Job job) {
        return isBlank(job.getTitle()) || isBlank(job.getDescription())
                || isEmpty(job.getRequirements()) || isBlank(job.getSalaryRange())
                || isBlank(job.getDifficulty()) || isEmpty(job.getRequiredSkills())
                || isEmpty(job.getBenefits());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private static List<String> normalise(List<String> values) {
        return values.stream().map(String::trim).map(String::toLowerCase).toList();
    }

    @PostMapping("/applicants")
    @PreAuthorize("hasAnyRole('JOBSEEKER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> addApplicant(@RequestBody Applicant applicant) {
        if (isBlank(applicant.getName()) || isEmpty(applicant.getSkills())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Please fill all applicant fields"));
        }
        applicant.setSkills(normalise(applicant.getSkills()));
        service.addApplicant(applicant);
        return ResponseEntity.ok(Map.of("message", "Applicant registered"));
    }

    /** Job listings are public: browsing vacancies does not require an account. */
    @GetMapping("/jobs")
    public List<Job> getJobs(@RequestParam(required = false) String salaryRange,
                             @RequestParam(required = false) String difficulty,
                             @RequestParam(required = false) String skill,
                             @RequestParam(required = false) Double minRating) {
        List<Job> jobs = service.filterJobs(salaryRange, difficulty, skill, minRating);
        jobs.forEach(job -> job.setEmployerRating(service.getAverageRating(job.getEmployerUsername())));
        return jobs;
    }

    @GetMapping("/matches")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'ADMIN')")
    public List<String> getMatches(Principal principal) {
        return service.getMatches(principal.getName());
    }

    @PostMapping("/ratings")
    @PreAuthorize("hasRole('JOBSEEKER')")
    public ResponseEntity<Map<String, Object>> addRating(@RequestBody Map<String, Object> request,
                                                         Principal principal) {
        String target = (String) request.get("target");
        Object score = request.get("score");
        if (isBlank(target) || !(score instanceof Number number)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "A target and a numeric score are required"));
        }
        // The rater is the authenticated account, so a rating cannot be attributed to someone else.
        return ResponseEntity.ok(service.addRating(target, principal.getName(),
                number.intValue(), (String) request.get("review")));
    }

    @GetMapping("/ratings")
    public List<RecruitmentService.Rating> getRatings(@RequestParam String target) {
        return service.getRatings(target);
    }

    @PostMapping("/comments")
    public ResponseEntity<Map<String, Object>> addComment(@RequestBody Map<String, String> request,
                                                          Principal principal) {
        String jobId = request.get("jobId");
        String content = request.get("content");
        if (isBlank(jobId) || isBlank(content)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "A job and a comment are required"));
        }
        return ResponseEntity.ok(service.addComment(jobId, principal.getName(), content));
    }

    @GetMapping("/comments")
    public List<RecruitmentService.Comment> getComments(@RequestParam String jobId) {
        return service.getComments(jobId);
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('JOBSEEKER')")
    public List<RecruitmentService.Notification> getNotifications(Principal principal) {
        return service.getNotifications(principal.getName());
    }

    @PostMapping("/user/skills")
    @PreAuthorize("hasRole('JOBSEEKER')")
    public ResponseEntity<Map<String, Object>> updateSkills(
            @RequestBody Map<String, List<String>> request, Principal principal) {
        List<String> skills = request.get("skills");
        if (skills == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "skills is required"));
        }
        service.updateUserSkills(principal.getName(), normalise(skills));
        return ResponseEntity.ok(Map.of("success", true, "message", "Skills updated"));
    }

    @PostMapping("/user/personal-data")
    public ResponseEntity<Map<String, Object>> updatePersonalData(
            @RequestBody Map<String, String> request, Principal principal) {
        service.updateUserPersonalData(principal.getName(), request.get("personalId"),
                request.get("phoneNumber"), request.get("address"));
        return ResponseEntity.ok(Map.of("success", true, "message", "Personal data updated"));
    }

    /** Returns the caller's own profile. There is no endpoint that returns anyone else's. */
    @GetMapping("/user/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(Principal principal) {
        User user = service.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        service.loadDecryptedDataForUser(user);

        Map<String, Object> profile = new HashMap<>();
        profile.put("username", user.getUsername());
        profile.put("userType", user.getUserType());
        profile.put("isAdmin", user.isAdmin());
        if ("jobseeker".equals(user.getUserType())) {
            profile.put("skills", user.getSkills());
        }
        profile.put("personalId", user.getPersonalId());
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("address", user.getAddress());
        return ResponseEntity.ok(profile);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/users/{targetUsername}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String targetUsername,
                                                          Principal principal) {
        if (targetUsername.equals(principal.getName())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "An administrator cannot delete their own account"));
        }
        if (!service.deleteUser(targetUsername)) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }
        log.info("Account {} deleted by administrator {}", targetUsername, principal.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users/{targetUsername}/ban")
    public ResponseEntity<Map<String, Object>> banUser(@PathVariable String targetUsername,
                                                       Principal principal) {
        if (!service.banUser(targetUsername)) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }
        log.info("Account {} banned by administrator {}", targetUsername, principal.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "User banned successfully"));
    }
}
