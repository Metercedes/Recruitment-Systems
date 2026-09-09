package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.model.Applicant;
import com.example.recruitmentsystem.model.Job;
import com.example.recruitmentsystem.model.User;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RecruitmentService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentService.class);
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private CaptchaService captchaService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final UserDirectory userDirectory;
    private List<Job> jobs = new ArrayList<>();
    private List<Applicant> applicants = new ArrayList<>();
    private List<Rating> ratings = new ArrayList<>();
    private List<Comment> comments = new ArrayList<>();
    private Map<String, List<Notification>> notifications = new HashMap<>();

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

    public RecruitmentService(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }





    public User getUserByUsername(String username) {
        return userDirectory.find(username).orElse(null);
    }

    public void addJob(Job job, String username) {
        User user = userDirectory.find(username).orElse(null);
        if (user == null || (!user.isAdmin() && !user.getUserType().equals("employer"))) {
            throw new IllegalStateException("Only admins or employers can add jobs");
        }
        job.setId(UUID.randomUUID().toString());
        job.setEmployerUsername(username);
        jobs.add(job);
        for (User u : userDirectory.all()) {
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
        User user = userDirectory.find(username).orElse(null);
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







    public Map<String, Object> addRating(String target, String rater, int score, String review) {
        if (!userDirectory.exists(target) || !userDirectory.exists(rater)) {
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
        if (!userDirectory.exists(commenter)) {
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
        User user = userDirectory.find(username).orElse(null);
        if (user != null) {
            try {
                // Decrypt existing personal data
                Map<String, Object> personalData = getDecryptedPersonalData(user);
                
                // Update skills
                personalData.put("skills", skills.stream().map(String::toLowerCase).collect(Collectors.toList()));
                
                // Re-encrypt and save
                String jsonData = objectMapper.writeValueAsString(personalData);
                user.setEncryptedPersonalData(encryptionService.encrypt(jsonData));
                
                // Update transient field for immediate use
                user.setSkills(skills.stream().map(String::toLowerCase).collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to update skills for user: {}", username);
            }
        }
    }
    
    public void updateUserPersonalData(String username, String personalId, String phoneNumber, String address) {
        User user = userDirectory.find(username).orElse(null);
        if (user != null) {
            try {
                // Decrypt existing personal data
                Map<String, Object> personalData = getDecryptedPersonalData(user);
                
                // Update personal information
                if (personalId != null) personalData.put("personalId", personalId);
                if (phoneNumber != null) personalData.put("phoneNumber", phoneNumber);
                if (address != null) personalData.put("address", address);
                
                // Re-encrypt and save
                String jsonData = objectMapper.writeValueAsString(personalData);
                user.setEncryptedPersonalData(encryptionService.encrypt(jsonData));
                
                // Update transient fields for immediate use
                if (personalId != null) user.setPersonalId(personalId);
                if (phoneNumber != null) user.setPhoneNumber(phoneNumber);
                if (address != null) user.setAddress(address);
            } catch (Exception e) {
                log.warn("Failed to update personal data for user: {}", username);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getDecryptedPersonalData(User user) {
        try {
            if (user.getEncryptedPersonalData() == null || user.getEncryptedPersonalData().isEmpty()) {
                // Return default structure
                Map<String, Object> defaultData = new HashMap<>();
                defaultData.put("skills", new ArrayList<String>());
                defaultData.put("personalId", "");
                defaultData.put("phoneNumber", "");
                defaultData.put("address", "");
                return defaultData;
            }
            
            String decryptedJson = encryptionService.decrypt(user.getEncryptedPersonalData());
            return objectMapper.readValue(decryptedJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("{}", "Failed to decrypt personal data for user: " + user.getUsername());
            // Return default structure on error
            Map<String, Object> defaultData = new HashMap<>();
            defaultData.put("skills", new ArrayList<String>());
            defaultData.put("personalId", "");
            defaultData.put("phoneNumber", "");
            defaultData.put("address", "");
            return defaultData;
        }
    }
    
    public boolean deleteUser(String username) {
        if (!userDirectory.exists(username)) {
            return false;
        }
        userDirectory.delete(username);
        notifications.remove(username);
        log.info("Account and associated notifications removed for {}", username);
        return true;
    }
    
    public boolean banUser(String username) {
        return userDirectory.find(username).map(user -> {
            user.setBanned(true);
            log.info("Account banned: {}", username);
            return true;
        }).orElse(false);
    }
    
    public void loadDecryptedDataForUser(User user) {
        if (user == null) return;
        
        try {
            Map<String, Object> personalData = getDecryptedPersonalData(user);
            
            // Set transient fields
            user.setSkills((List<String>) personalData.get("skills"));
            user.setPersonalId((String) personalData.get("personalId"));
            user.setPhoneNumber((String) personalData.get("phoneNumber"));
            user.setAddress((String) personalData.get("address"));
        } catch (Exception e) {
            log.warn("{}", "Failed to load decrypted data for user: " + user.getUsername());
        }
    }
}