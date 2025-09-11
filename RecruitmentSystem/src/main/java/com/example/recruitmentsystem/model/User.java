package com.example.recruitmentsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.util.List;

@Entity
public class User {
    @Id
    private String username;
    private String password;
    private boolean isAdmin;
    private boolean wasLocked;
    private String userType; // "employer" or "jobseeker"
    
    // Encrypted sensitive data
    private String encryptedPersonalData; // JSON string containing sensitive info
    
    @Transient
    private List<String> skills; // For job seekers - not persisted directly
    
    @Transient
    private String personalId; // Decrypted personal ID
    
    @Transient
    private String phoneNumber; // Decrypted phone number
    
    @Transient
    private String address; // Decrypted address

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
    
    public String getEncryptedPersonalData() { return encryptedPersonalData; }
    public void setEncryptedPersonalData(String encryptedPersonalData) { this.encryptedPersonalData = encryptedPersonalData; }
    
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    
    public String getPersonalId() { return personalId; }
    public void setPersonalId(String personalId) { this.personalId = personalId; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}