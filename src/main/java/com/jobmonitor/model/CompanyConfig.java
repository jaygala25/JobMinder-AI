package com.jobmonitor.model;

public class CompanyConfig {
    private Long id;
    private String companyName;
    private String ashbyId;
    private String ashbyName;
    private boolean isActive;
    
    public CompanyConfig() {}
    
    public CompanyConfig(String companyName, String ashbyId, String ashbyName) {
        this.companyName = companyName;
        this.ashbyId = ashbyId;
        this.ashbyName = ashbyName;
        this.isActive = true;
    }
    
    public CompanyConfig(Long id, String companyName, String ashbyId, String ashbyName, boolean isActive) {
        this.id = id;
        this.companyName = companyName;
        this.ashbyId = ashbyId;
        this.ashbyName = ashbyName;
        this.isActive = isActive;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    
    public String getAshbyId() { return ashbyId; }
    public void setAshbyId(String ashbyId) { this.ashbyId = ashbyId; }
    
    public String getAshbyName() { return ashbyName; }
    public void setAshbyName(String ashbyName) { this.ashbyName = ashbyName; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    @Override
    public String toString() {
        return "CompanyConfig{" +
                "id=" + id +
                ", companyName='" + companyName + '\'' +
                ", ashbyId='" + ashbyId + '\'' +
                ", ashbyName='" + ashbyName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
