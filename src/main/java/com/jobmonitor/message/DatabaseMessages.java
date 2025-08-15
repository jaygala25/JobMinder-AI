package com.jobmonitor.message;

import akka.actor.typed.ActorRef;
import com.jobmonitor.model.Job;
import com.jobmonitor.message.JobMessages.CompanyConfig;
import java.util.List;
import java.util.Optional;

public class DatabaseMessages {

    // Job data operations
    public static class UpsertJobData {
        private final String companyName;
        private final String ashbyName;
        private final List<Job> jobs;
        private final ActorRef<Object> sender;
        private final CompanyConfig companyConfig; // Added company config
        
        public UpsertJobData(String companyName, String ashbyName, List<Job> jobs, ActorRef<Object> sender, CompanyConfig companyConfig) {
            this.companyName = companyName;
            this.ashbyName = ashbyName;
            this.jobs = jobs;
            this.sender = sender;
            this.companyConfig = companyConfig;
        }
        
        public String getCompanyName() { return companyName; }
        public String getAshbyName() { return ashbyName; }
        public List<Job> getJobs() { return jobs; }
        public ActorRef<Object> getSender() { return sender; }
        public CompanyConfig getCompanyConfig() { return companyConfig; }
    }

    public static class JobDataUpserted {
        private final String companyName;
        private final List<Job> newJobs;
        private final boolean success;
        private final String errorMessage;
        private final CompanyConfig companyConfig; // Added company config
        
        public JobDataUpserted(String companyName, List<Job> newJobs, boolean success, String errorMessage, CompanyConfig companyConfig) {
            this.companyName = companyName;
            this.newJobs = newJobs;
            this.success = success;
            this.errorMessage = errorMessage;
            this.companyConfig = companyConfig;
        }
        
        public String getCompanyName() { return companyName; }
        public List<Job> getNewJobs() { return newJobs; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public CompanyConfig getCompanyConfig() { return companyConfig; }
    }

    public static class GetJobData {
        private final String companyName;
        private final ActorRef<Object> sender;
        private final CompanyConfig companyConfig; // Added company config
        
        public GetJobData(String companyName, ActorRef<Object> sender, CompanyConfig companyConfig) {
            this.companyName = companyName;
            this.sender = sender;
            this.companyConfig = companyConfig;
        }
        
        public String getCompanyName() { return companyName; }
        public ActorRef<Object> getSender() { return sender; }
        public CompanyConfig getCompanyConfig() { return companyConfig; }
    }

    public static class JobDataFound {
        private final String companyName;
        private final java.util.Optional<com.jobmonitor.model.JobData> jobData;
        private final ActorRef<Object> sender;
        private final CompanyConfig companyConfig; // Added company config
        
        public JobDataFound(String companyName, java.util.Optional<com.jobmonitor.model.JobData> jobData, ActorRef<Object> sender, CompanyConfig companyConfig) {
            this.companyName = companyName;
            this.jobData = jobData;
            this.sender = sender;
            this.companyConfig = companyConfig;
        }
        
        public String getCompanyName() { return companyName; }
        public java.util.Optional<com.jobmonitor.model.JobData> getJobData() { return jobData; }
        public ActorRef<Object> getSender() { return sender; }
        public CompanyConfig getCompanyConfig() { return companyConfig; }
    }

    public static class GetAllJobData {
        private final ActorRef<Object> sender;
        
        public GetAllJobData(ActorRef<Object> sender) {
            this.sender = sender;
        }
        
        public ActorRef<Object> getSender() { return sender; }
    }

    public static class AllJobDataFound {
        private final java.util.List<com.jobmonitor.model.JobData> jobDataList;
        private final ActorRef<Object> sender;
        
        public AllJobDataFound(java.util.List<com.jobmonitor.model.JobData> jobDataList, ActorRef<Object> sender) {
            this.jobDataList = jobDataList;
            this.sender = sender;
        }
        
        public java.util.List<com.jobmonitor.model.JobData> getJobDataList() { return jobDataList; }
        public ActorRef<Object> getSender() { return sender; }
    }
    
    // Raw JSON job data operations
    public static class UpsertRawJsonJobData {
        private final String companyName;
        private final String ashbyName;
        private final String rawJsonResponse;
        private final ActorRef<Object> sender;
        private final CompanyConfig companyConfig; // Added company config
        
        public UpsertRawJsonJobData(String companyName, String ashbyName, String rawJsonResponse, ActorRef<Object> sender, CompanyConfig companyConfig) {
            this.companyName = companyName;
            this.ashbyName = ashbyName;
            this.rawJsonResponse = rawJsonResponse;
            this.sender = sender;
            this.companyConfig = companyConfig;
        }
        
        public String getCompanyName() { return companyName; }
        public String getAshbyName() { return ashbyName; }
        public String getRawJsonResponse() { return rawJsonResponse; }
        public ActorRef<Object> getSender() { return sender; }
        public CompanyConfig getCompanyConfig() { return companyConfig; }
    }

}
