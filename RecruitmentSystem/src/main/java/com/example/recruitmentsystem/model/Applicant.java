package com.example.recruitmentsystem.model;

import java.util.List;

public class Applicant {
    private String name;
    private List<String> skills;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
}