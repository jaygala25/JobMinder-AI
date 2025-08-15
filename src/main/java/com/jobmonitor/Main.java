package com.jobmonitor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import com.jobmonitor.actor.*;
import com.jobmonitor.message.JobMessages.UpdateLLMAnalyzerActor;
import com.jobmonitor.service.*;
import com.jobmonitor.model.CompanyConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Load configuration
        Config config = ConfigFactory.load();
        
        // Initialize database service
        DatabaseService databaseService = new DatabaseService(
            config.getString("job-monitor.database.url"),
            config.getString("job-monitor.database.username"),
            config.getString("job-monitor.database.password"),
            config.getInt("job-monitor.database.pool-size")
        );
        
        // Fetch companies dynamically from database
        List<com.jobmonitor.model.CompanyConfig> dbCompanies = databaseService.getAllCompanies();
        if (dbCompanies.isEmpty()) {
            logger.error("No companies found in database. Please ensure job_data table has data.");
            System.exit(1);
        }
        
        // Convert to JobMessages.CompanyConfig
        List<com.jobmonitor.message.JobMessages.CompanyConfig> companies = dbCompanies.stream()
            .map(dbCompany -> new com.jobmonitor.message.JobMessages.CompanyConfig(
                dbCompany.getCompanyName(), 
                dbCompany.getAshbyId()
            ))
            .collect(Collectors.toList());
        
        logger.info("Found {} companies in database: {}", 
            companies.size(), 
            companies.stream().map(com.jobmonitor.message.JobMessages.CompanyConfig::getName).collect(Collectors.joining(", ")));

        // Add shutdown hook to ensure database connections are closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, closing database connections...");
            databaseService.close();
            logger.info("Database connections closed");
        }));

        // Create services
        AshbyApiService ashbyApiService = new AshbyApiService(
            config.getString("job-monitor.ashby.base-url")
        );
        
        // Initialize Mistral service
        String mistralApiKey = config.getString("job-monitor.mistral.api-key");
        String model = config.getString("job-monitor.mistral.model");
        int maxTokens = config.getInt("job-monitor.mistral.max-tokens");
        double temperature = config.getDouble("job-monitor.mistral.temperature");
        double matchThreshold = config.getDouble("job-monitor.mistral.match-threshold");
        
        // Load resume text
        String resumeFilePath = config.getString("job-monitor.resume.file-path");
        ResumeService resumeService = new ResumeService(resumeFilePath);
        
        MistralService mistralService = new MistralService(mistralApiKey, model, maxTokens, temperature, matchThreshold, resumeService.getResumeText());
        
        SlackService slackService = new SlackService(
            config.getString("job-monitor.slack.token"),
            config.getString("job-monitor.slack.channel"),
            config.getString("job-monitor.slack.user-id")
        );

        // Create the main application behavior
        Behavior<Object> rootBehavior = Behaviors.setup(context -> {
            
            // Create actors
            ActorRef<Object> databaseActor = context.spawn(DatabaseActor.create(databaseService), "databaseActor");
            ActorRef<Object> slackNotifierActor = context.spawn(SlackNotifierActor.create(slackService, context.getSelf().narrow()), "slackNotifierActor");
            ActorRef<Object> jobProcessorActor = context.spawn(JobProcessorActor.create(databaseActor, null, slackNotifierActor, "user1"), "jobProcessorActor");
            ActorRef<Object> llmAnalyzerActor = context.spawn(LLMAnalyzerActor.create(mistralService, jobProcessorActor), "llmAnalyzerActor");
            
            // Create message queue actor for job processing
            int maxQueueSize = config.getInt("job-monitor.message-queue.max-size");
            int maxConcurrentJobs = config.getInt("job-monitor.message-queue.max-concurrent");
            ActorRef<Object> messageQueueActor = context.spawn(
                MessageQueueActor.create(jobProcessorActor, maxQueueSize, maxConcurrentJobs),
                "messageQueueActor"
            );
            
            // Update JobProcessorActor with LLMAnalyzerActor reference
            jobProcessorActor.tell(new UpdateLLMAnalyzerActor(llmAnalyzerActor));
            

            
            // Create JobPollerActor (single instance for all companies)
            ActorRef<Object> jobPollerActor = context.spawn(
                JobPollerActor.create(ashbyApiService, databaseActor, jobProcessorActor),
                "jobPollerActor"
            );
            
            // Create single continuous SchedulerActor that manages all companies
            Duration pollInterval = Duration.ofMinutes(config.getInt("job-monitor.polling.interval-minutes"));
            Duration companyDiscoveryInterval = Duration.ofMinutes(config.getInt("job-monitor.company-discovery.interval-minutes"));
            
            ActorRef<Object> schedulerActor = context.spawn(
                SchedulerActor.create(jobPollerActor, databaseActor, pollInterval, companyDiscoveryInterval),
                "continuousSchedulerActor"
            );
            
            // Send initial company discovery to populate the scheduler
            schedulerActor.tell(new com.jobmonitor.actor.SchedulerActor.DiscoverCompanies());
            
            context.getLog().info("Continuous scheduler started with {} minute polling interval", pollInterval.toMinutes());
            context.getLog().info("Company discovery will run every {} minutes", companyDiscoveryInterval.toMinutes());
            
                    // Keep the system running continuously - no termination needed
        context.getLog().info("🎯 All actors created successfully - system will run continuously");
        context.getLog().info("📊 Job monitoring will continue running until manually stopped");
        
        return Behaviors.empty();
        });

        // Create and start the actor system
        ActorSystem<Object> system = ActorSystem.create(rootBehavior, "job-monitor-system");
        
        logger.info("Job Monitoring System started");
        
        // Keep the application running forever for continuous job monitoring
        logger.info("🎯 Job Monitoring System started - running continuously");
        logger.info("📊 System will monitor jobs continuously and send Slack notifications for new matches");
        logger.info("⏹️  Press Ctrl+C to stop the application");
        
        // Keep the main thread alive - the Actor System will run forever
        try {
            // Use a simple sleep loop instead of Thread.join() which can cause issues
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000); // Sleep for 1 second
            }
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted, shutting down...");
        }
        
        logger.info("Shutting down Job Monitoring System...");
        system.terminate();
        logger.info("Job Monitoring System shutdown complete");
    }
}
