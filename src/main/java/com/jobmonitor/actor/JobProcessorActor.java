package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.JobMessages.*;
import com.jobmonitor.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JobProcessorActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(JobProcessorActor.class);
    private final ActorRef<Object> databaseActor;
    private ActorRef<Object> llmAnalyzerActor; // Make this mutable
    private final ActorRef<Object> slackNotifierActor;
    private final String userId;
    private int pendingNotifications = 0; // Counter for pending Slack notifications

    public static Behavior<Object> create(ActorRef<Object> databaseActor,
                                        ActorRef<Object> llmAnalyzerActor,
                                        ActorRef<Object> slackNotifierActor,
                                        String userId) {
        return Behaviors.setup(context -> new JobProcessorActor(context, databaseActor, llmAnalyzerActor, slackNotifierActor, userId));
    }

    private JobProcessorActor(ActorContext<Object> context,
                            ActorRef<Object> databaseActor,
                            ActorRef<Object> llmAnalyzerActor,
                            ActorRef<Object> slackNotifierActor,
                            String userId) {
        super(context);
        this.databaseActor = databaseActor;
        this.llmAnalyzerActor = llmAnalyzerActor;
        this.slackNotifierActor = slackNotifierActor;
        this.userId = userId;
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessNewJobs.class, this::onProcessNewJobs)
                .onMessage(JobMatchResults.class, this::onJobMatchResults)
                .onMessage(NotificationSent.class, this::onNotificationSent)
                .onMessage(UpdateLLMAnalyzerActor.class, this::onUpdateLLMAnalyzerActor)
                .build();
    }

    private Behavior<Object> onUpdateLLMAnalyzerActor(UpdateLLMAnalyzerActor msg) {
        this.llmAnalyzerActor = msg.getLLMAnalyzerActor();
        getContext().getLog().info("LLMAnalyzerActor reference updated");
        
        return this;
    }

    private Behavior<Object> onProcessNewJobs(ProcessNewJobs msg) {
        CompanyConfig company = msg.getCompany();
        List<Job> newJobs = msg.getNewJobs();
        
        getContext().getLog().info("Processing {} new jobs for company: {}", newJobs.size(), company.getName());
        
        if (!newJobs.isEmpty()) {
            if (llmAnalyzerActor != null) {
                // Send jobs to Mistral for analysis with company information
                llmAnalyzerActor.tell(new AnalyzeJobMatches(company, newJobs));
                return this;
            } else {
                getContext().getLog().error("LLMAnalyzerActor not available, cannot process jobs");
                return this; // Keep actor alive even on error
            }
        } else {
            getContext().getLog().info("No new jobs to process");
            return this; // Keep actor alive for future work
        }
    }

    private Behavior<Object> onJobMatchResults(JobMatchResults msg) {
        List<com.jobmonitor.message.JobMessages.JobMatchResult> results = msg.getResults();
        
        getContext().getLog().info("Received {} job match results from LLM", results.size());
        
        int matchingJobs = 0;
        for (com.jobmonitor.message.JobMessages.JobMatchResult result : results) {
            if (result.isMatch()) {
                matchingJobs++;
                // Send Slack notification for matching jobs
                slackNotifierActor.tell(new NotifyJobMatch(
                    result.getJob(), 
                    result.getUserId(), 
                    result.getMatchScore(), 
                    result.getMatchReason()
                ));
                pendingNotifications++; // Increment counter for each pending notification
            }
        }
        
        getContext().getLog().info("🎯 JOB MATCHING SUMMARY: Found {} matching jobs out of {} total results", matchingJobs, results.size());
        
        if (matchingJobs > 0) {
            getContext().getLog().info("📧 Sending {} Slack notifications for matching jobs", matchingJobs);
        } else {
            getContext().getLog().info("❌ No matching jobs found - no Slack notifications will be sent");
        }
        
        return this;
    }

    private Behavior<Object> onNotificationSent(NotificationSent msg) {
        getContext().getLog().info("Slack notification sent for job: {}", msg.getJob().getTitle());
        
        // Decrement the counter of pending notifications
        pendingNotifications--;
        
        // Log when all notifications are complete
        if (pendingNotifications <= 0) {
            getContext().getLog().info("All Slack notifications completed.");
        }
        
        return this;
    }
}
