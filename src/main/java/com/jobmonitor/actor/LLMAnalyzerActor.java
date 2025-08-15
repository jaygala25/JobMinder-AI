package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.JobMessages;
import com.jobmonitor.model.Job;
import com.jobmonitor.service.MistralService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LLMAnalyzerActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(LLMAnalyzerActor.class);
    private final MistralService mistralService;
    private final ActorRef<Object> replyTo;

    public static Behavior<Object> create(MistralService mistralService, ActorRef<Object> replyTo) {
        return Behaviors.setup(context -> new LLMAnalyzerActor(context, mistralService, replyTo));
    }

    private LLMAnalyzerActor(ActorContext<Object> context, MistralService mistralService, ActorRef<Object> replyTo) {
        super(context);
        this.mistralService = mistralService;
        this.replyTo = replyTo;
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(JobMessages.AnalyzeJobMatches.class, this::onAnalyzeJobMatches)
                .build();
    }

    private Behavior<Object> onAnalyzeJobMatches(JobMessages.AnalyzeJobMatches msg) {
        List<Job> jobs = msg.getJobs();
        JobMessages.CompanyConfig company = msg.getCompany();
        
        getContext().getLog().info("Analyzing {} jobs for company {} with Mistral AI in a single batch request", 
            jobs.size(), company.getName());
        
        try {
            // Use the new company-based batch analysis method
            List<JobMessages.JobMatchResult> results = mistralService.analyzeJobMatchesForCompany(company.getName(), jobs);
            
            JobMessages.JobMatchResults response = new JobMessages.JobMatchResults(results);
            replyTo.tell(response);
            
            // Count actual matches (isMatch = true)
            long actualMatches = results.stream().filter(JobMessages.JobMatchResult::isMatch).count();
            
            getContext().getLog().info("🎯 Job match analysis completed for company {}: found {} actual matches out of {} total results", 
                company.getName(), actualMatches, results.size());
            
            if (actualMatches > 0) {
                getContext().getLog().info("📧 Company {} will receive {} Slack notifications", company.getName(), actualMatches);
            } else {
                getContext().getLog().info("❌ Company {} has no matching jobs - no notifications will be sent", company.getName());
            }
            
        } catch (Exception e) {
            logger.error("❌ Error analyzing job matches for company {}: {}", company.getName(), e.getMessage(), e);
            
            JobMessages.JobMatchResults response = new JobMessages.JobMatchResults(List.of());
            replyTo.tell(response);
        }
        
        return this;
    }
}
