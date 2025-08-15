package com.jobmonitor.message;

import com.jobmonitor.model.Job;
import java.util.List;

public class JobMessages {

    // Messages for JobPollerActor
    public static class PollJobs {
        private final CompanyConfig company;
        
        public PollJobs(CompanyConfig company) {
            this.company = company;
        }
        
        public CompanyConfig getCompany() { return company; }
    }

    public static class PollJobsResponse {
        private final CompanyConfig company;
        private final List<Job> jobs;
        private final boolean success;
        private final String errorMessage;
        
        public PollJobsResponse(CompanyConfig company, List<Job> jobs, boolean success, String errorMessage) {
            this.company = company;
            this.jobs = jobs;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public CompanyConfig getCompany() { return company; }
        public List<Job> getJobs() { return jobs; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    // Messages for JobProcessorActor
    public static class ProcessNewJobs {
        private final CompanyConfig company;
        private final List<Job> newJobs;
        
        public ProcessNewJobs(CompanyConfig company, List<Job> newJobs) {
            this.company = company;
            this.newJobs = newJobs;
        }
        
        public CompanyConfig getCompany() { return company; }
        public List<Job> getNewJobs() { return newJobs; }
    }

    public static class UpdateLLMAnalyzerActor {
        private final akka.actor.typed.ActorRef<Object> llmAnalyzerActor;
        
        public UpdateLLMAnalyzerActor(akka.actor.typed.ActorRef<Object> llmAnalyzerActor) {
            this.llmAnalyzerActor = llmAnalyzerActor;
        }
        
        public akka.actor.typed.ActorRef<Object> getLLMAnalyzerActor() { return llmAnalyzerActor; }
    }

    public static class ProcessNewJobsResponse {
        private final CompanyConfig company;
        private final int processedCount;
        private final int matchedCount;
        
        public ProcessNewJobsResponse(CompanyConfig company, int processedCount, int matchedCount) {
            this.company = company;
            this.processedCount = processedCount;
            this.matchedCount = matchedCount;
        }
        
        public CompanyConfig getCompany() { return company; }
        public int getProcessedCount() { return processedCount; }
        public int getMatchedCount() { return matchedCount; }
    }

    // Messages for LLMAnalyzerActor
    public static class AnalyzeJobMatches {
        private final CompanyConfig company;
        private final List<Job> jobs;
        
        public AnalyzeJobMatches(CompanyConfig company, List<Job> jobs) {
            this.company = company;
            this.jobs = jobs;
        }
        
        public CompanyConfig getCompany() { return company; }
        public List<Job> getJobs() { return jobs; }
    }

    public static class JobMatchResults {
        private final List<JobMatchResult> results;
        
        public JobMatchResults(List<JobMatchResult> results) {
            this.results = results;
        }
        
        public List<JobMatchResult> getResults() { return results; }
    }

    public static class JobMatchResult {
        private final Job job;
        private final String userId;
        private final double matchScore;
        private final String matchReason;
        private final boolean isMatch;
        
        public JobMatchResult(Job job, String userId, double matchScore, String matchReason, boolean isMatch) {
            this.job = job;
            this.userId = userId;
            this.matchScore = matchScore;
            this.matchReason = matchReason;
            this.isMatch = isMatch;
        }
        
        public Job getJob() { return job; }
        public String getUserId() { return userId; }
        public double getMatchScore() { return matchScore; }
        public String getMatchReason() { return matchReason; }
        public boolean isMatch() { return isMatch; }
    }

    // Messages for SlackNotifierActor
    public static class NotifyJobMatch {
        private final Job job;
        private final String userId;
        private final double matchScore;
        private final String matchReason;
        
        public NotifyJobMatch(Job job, String userId, double matchScore, String matchReason) {
            this.job = job;
            this.userId = userId;
            this.matchScore = matchScore;
            this.matchReason = matchReason;
        }
        
        public Job getJob() { return job; }
        public String getUserId() { return userId; }
        public double getMatchScore() { return matchScore; }
        public String getMatchReason() { return matchReason; }
    }

    public static class NotificationSent {
        private final Job job;
        private final String userId;
        private final String slackMessageId;
        private final boolean success;
        private final String errorMessage;
        
        public NotificationSent(Job job, String userId, String slackMessageId, boolean success, String errorMessage) {
            this.job = job;
            this.userId = userId;
            this.slackMessageId = slackMessageId;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public Job getJob() { return job; }
        public String getUserId() { return userId; }
        public String getSlackMessageId() { return slackMessageId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class CompanyConfig {
        final String name;
        final String ashbyId;
        
        public CompanyConfig(String name, String ashbyId) {
            this.name = name;
            this.ashbyId = ashbyId;
        }
        
        public String getName() { return name; }
        public String getAshbyId() { return ashbyId; }
    }
}
