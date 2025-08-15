package com.jobmonitor.model;

import java.time.LocalDateTime;

public class JobData {
    private Long id;
    private String companyName;
    private String jobData; // JSON string
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public JobData() {}

    public JobData(String companyName, String jobData) {
        this.companyName = companyName;
        this.jobData = jobData;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getJobData() { return jobData; }
    public void setJobData(String jobData) { this.jobData = jobData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "JobData{" +
                "id=" + id +
                ", companyName='" + companyName + '\'' +
                ", jobData='" + jobData + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
