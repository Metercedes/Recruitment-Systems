package com.example.recruitmentsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.List;

@Entity
public class User {
    @Id
    private String username;
    private String password;
    private boolean isAdmin;
    private boolean wasLocked;
    private String userType; // "employer" or "jobseeker"
    private List<String> skills; // For job seekers

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { this.isAdmin = admin; }
    public boolean isWasLocked() { return wasLocked; }
    public void setWasLocked(boolean wasLocked) { this.wasLocked = wasLocked; }
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
}