package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.JobMessages.*;
import com.jobmonitor.message.DatabaseMessages.*;
import com.jobmonitor.model.Job;
import com.jobmonitor.service.AshbyApiService;
import com.jobmonitor.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JobPollerActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(JobPollerActor.class);
    private final AshbyApiService ashbyApiService;
    private final ActorRef<Object> databaseActor;
    private final ActorRef<Object> jobProcessorActor;

    public static Behavior<Object> create(AshbyApiService ashbyApiService, 
                                        ActorRef<Object> databaseActor,
                                        ActorRef<Object> jobProcessorActor) {
        return Behaviors.setup(context -> new JobPollerActor(context, ashbyApiService, databaseActor, jobProcessorActor));
    }

    private JobPollerActor(ActorContext<Object> context, 
                          AshbyApiService ashbyApiService,
                          ActorRef<Object> databaseActor,
                          ActorRef<Object> jobProcessorActor) {
        super(context);
        this.ashbyApiService = ashbyApiService;
        this.databaseActor = databaseActor;
        this.jobProcessorActor = jobProcessorActor;
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(PollJobs.class, this::onPollJobs)
                .onMessage(JobDataFound.class, this::onJobDataFound)
                .onMessage(JobDataUpserted.class, this::onJobDataUpserted)
                .build();
    }

    private Behavior<Object> onPollJobs(PollJobs msg) {
        CompanyConfig company = msg.getCompany();
        logger.info("📥 JobPollerActor received PollJobs message for company: {}", company.getName());
        getContext().getLog().info("Polling jobs for company: {}", company.getName());
        
        try {
            // Get existing job data for comparison
            logger.info("🔍 Requesting existing job data from DatabaseActor for company: {}", company.getName());
            databaseActor.tell(new GetJobData(company.getName(), getContext().getSelf(), company));
            logger.info("✅ GetJobData message sent to DatabaseActor for company: {}", company.getName());
            
            // Return this behavior to wait for JobDataFound response
            return this;
        } catch (Exception e) {
            logger.error("❌ Error in onPollJobs for company {}: {}", company.getName(), e.getMessage(), e);
            return this;
        }
    }

    private Behavior<Object> onJobDataFound(JobDataFound msg) {
        CompanyConfig company = msg.getCompanyConfig();
        String companyName = msg.getCompanyName();
        getContext().getLog().info("Received JobDataFound for company: {}", companyName);
        
        // Poll current raw JSON from Ashby API
        getContext().getLog().info("Polling Ashby API for raw JSON from company: {}", companyName);
        String rawJsonResponse = ashbyApiService.pollJobsRawJson(company.getAshbyId());
        
        if (rawJsonResponse == null || rawJsonResponse.trim().isEmpty()) {
            getContext().getLog().error("❌ No raw JSON response received from API for company: {}", companyName);
            logger.error("❌ API call failed for company: {} - no response data", companyName);
            
            // Don't update database with null/empty data
            // The system will retry on the next polling cycle
            return this;
        }

        // Additional validation of the API response
        if (rawJsonResponse.length() < 1000) {
            getContext().getLog().warn("⚠️ Suspiciously short API response for company: {} ({} chars)", 
                companyName, rawJsonResponse.length());
        }
        
        // Check if response appears to be truncated
        if (!rawJsonResponse.trim().endsWith("}")) {
            getContext().getLog().error("❌ API response appears truncated for company: {} - does not end with '}'", companyName);
            logger.error("❌ Truncated API response detected for company: {}", companyName);
            return this;
        }

        getContext().getLog().info("✅ Retrieved raw JSON response for company: {} ({} characters)", 
            companyName, rawJsonResponse.length());
        
        try {
            // Update database with raw JSON data and get new jobs
            getContext().getLog().info("Updating database with raw JSON data for company: {}", companyName);
            databaseActor.tell(new com.jobmonitor.message.DatabaseMessages.UpsertRawJsonJobData(
                companyName, 
                company.getAshbyId(), 
                rawJsonResponse, 
                getContext().getSelf(),
                company
            ));
            
        } catch (Exception e) {
            getContext().getLog().error("❌ Error processing raw JSON for company {}: {}", companyName, e.getMessage(), e);
            logger.error("❌ Database update failed for company: {} due to JSON processing error", companyName, e);
        }
        
        return this;
    }

    private Behavior<Object> onJobDataUpserted(JobDataUpserted msg) {
        String companyName = msg.getCompanyName();
        CompanyConfig company = msg.getCompanyConfig();
        logger.info("Received JobDataUpserted for company: {} - Success: {}, New jobs: {}", 
            companyName, msg.isSuccess(), msg.getNewJobs().size());
        
        if (msg.isSuccess() && !msg.getNewJobs().isEmpty()) {
            logger.info("Processing {} new jobs for company: {}", msg.getNewJobs().size(), companyName);
            jobProcessorActor.tell(new ProcessNewJobs(company, msg.getNewJobs()));
            return this;
        } else if (msg.isSuccess()) {
            logger.info("No new jobs to process for company: {}", companyName);
            // Keep actor alive for future polling
            return this;
        } else {
            logger.error("Failed to upsert job data for company {}: {}", companyName, msg.getErrorMessage());
            // Keep actor alive even on error
            return this;
        }
    }
}
