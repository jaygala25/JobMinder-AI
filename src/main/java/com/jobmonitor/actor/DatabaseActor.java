package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.DatabaseMessages.*;

import com.jobmonitor.model.JobData;
import com.jobmonitor.model.Job;
import com.jobmonitor.service.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

public class DatabaseActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseActor.class);
    private final DatabaseService databaseService;

    public static Behavior<Object> create(DatabaseService databaseService) {
        return Behaviors.setup(context -> new DatabaseActor(context, databaseService));
    }

    private DatabaseActor(ActorContext<Object> context, DatabaseService databaseService) {
        super(context);
        this.databaseService = databaseService;
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(UpsertJobData.class, this::onUpsertJobData)
                .onMessage(UpsertRawJsonJobData.class, this::onUpsertRawJsonJobData)
                .onMessage(GetJobData.class, this::onGetJobData)
                .onMessage(GetAllJobData.class, this::onGetAllJobData)
                .build();
    }

    private Behavior<Object> onUpsertJobData(UpsertJobData msg) {
        try {
            List<Job> newJobs = databaseService.upsertJobData(msg.getCompanyName(), msg.getAshbyName(), msg.getJobs());
            JobDataUpserted response = new JobDataUpserted(msg.getCompanyName(), newJobs, true, null, msg.getCompanyConfig());
            msg.getSender().tell(response);
            getContext().getLog().info("Job data upserted for company: {} - Success: {}, New jobs: {}", 
                msg.getCompanyName(), true, newJobs.size());
            
            // Keep this actor alive for other companies - it will be terminated by the main system
            return this;
            
        } catch (Exception e) {
            logger.error("Error upserting job data for company {}: {}", msg.getCompanyName(), e.getMessage(), e);
            JobDataUpserted response = new JobDataUpserted(msg.getCompanyName(), new ArrayList<>(), false, e.getMessage(), msg.getCompanyConfig());
            msg.getSender().tell(response);
            
            // Keep actor alive even on error - let main system handle termination
            return this;
        }
    }

    private Behavior<Object> onUpsertRawJsonJobData(UpsertRawJsonJobData msg) {
        try {
            List<Job> newJobs = databaseService.upsertRawJsonJobData(msg.getCompanyName(), msg.getAshbyName(), msg.getRawJsonResponse());
            JobDataUpserted response = new JobDataUpserted(msg.getCompanyName(), newJobs, true, null, msg.getCompanyConfig());
            msg.getSender().tell(response);
            getContext().getLog().info("Raw JSON job data upserted for company: {} - Success: {}, New jobs: {}", 
                msg.getCompanyName(), true, newJobs.size());
            
            return this;
            
        } catch (Exception e) {
            logger.error("Error upserting raw JSON job data for company {}: {}", msg.getCompanyName(), e.getMessage(), e);
            JobDataUpserted response = new JobDataUpserted(msg.getCompanyName(), new ArrayList<>(), false, e.getMessage(), msg.getCompanyConfig());
            msg.getSender().tell(response);
            
            return this;
        }
    }

    private Behavior<Object> onGetJobData(GetJobData msg) {
        logger.info("📥 DatabaseActor received GetJobData message for company: {}", msg.getCompanyName());
        try {
            logger.info("🔍 Querying database for job data for company: {}", msg.getCompanyName());
            Optional<JobData> jobData = databaseService.getJobData(msg.getCompanyName());
            logger.info("✅ Database query completed for company: {} - Found: {}", msg.getCompanyName(), jobData.isPresent());
            
            JobDataFound response = new JobDataFound(msg.getCompanyName(), jobData, msg.getSender(), msg.getCompanyConfig());
            logger.info("📤 Sending JobDataFound response to sender for company: {}", msg.getCompanyName());
            msg.getSender().tell(response);
            logger.info("✅ JobDataFound response sent successfully for company: {}", msg.getCompanyName());
            
            getContext().getLog().info("Job data found for company: {} - Present: {}", msg.getCompanyName(), jobData.isPresent());
        } catch (Exception e) {
            logger.error("❌ Error getting job data for company {}: {}", msg.getCompanyName(), e.getMessage(), e);
            JobDataFound response = new JobDataFound(msg.getCompanyName(), Optional.empty(), msg.getSender(), msg.getCompanyConfig());
            msg.getSender().tell(response);
        }
        return this;
    }

    private Behavior<Object> onGetAllJobData(GetAllJobData msg) {
        try {
            // Get all companies for continuous operation
            List<com.jobmonitor.model.CompanyConfig> companies = databaseService.getAllCompanies();
            
            // Convert to JobMessages.CompanyConfig format
            List<com.jobmonitor.message.JobMessages.CompanyConfig> companyConfigs = companies.stream()
                .map(company -> new com.jobmonitor.message.JobMessages.CompanyConfig(
                    company.getCompanyName(), 
                    company.getAshbyId()
                ))
                .collect(java.util.stream.Collectors.toList());
            
            // Send company list update to scheduler
            msg.getSender().tell(new com.jobmonitor.actor.SchedulerActor.CompanyListUpdated(companyConfigs));
            
            getContext().getLog().info("Retrieved {} companies for continuous monitoring", companies.size());
        } catch (Exception e) {
            logger.error("Error getting all companies: {}", e.getMessage(), e);
            // Send empty company list on error
            msg.getSender().tell(new com.jobmonitor.actor.SchedulerActor.CompanyListUpdated(List.of()));
        }
        return this;
    }
    

}
