package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.JobMessages.PollJobs;
import com.jobmonitor.message.JobMessages.CompanyConfig;
import com.jobmonitor.message.DatabaseMessages.GetAllJobData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SchedulerActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerActor.class);
    private final ActorRef<Object> jobPollerActor;
    private final ActorRef<Object> databaseActor;
    private final Duration pollInterval;
    private final Duration companyDiscoveryInterval;
    private List<CompanyConfig> currentCompanies;

    public static Behavior<Object> create(ActorRef<Object> jobPollerActor, 
                                        ActorRef<Object> databaseActor,
                                        Duration pollInterval,
                                        Duration companyDiscoveryInterval) {
        return Behaviors.setup(context -> new SchedulerActor(context, jobPollerActor, databaseActor, pollInterval, companyDiscoveryInterval));
    }

    private SchedulerActor(ActorContext<Object> context, 
                          ActorRef<Object> jobPollerActor,
                          ActorRef<Object> databaseActor,
                          Duration pollInterval, 
                          Duration companyDiscoveryInterval) {
        super(context);
        this.jobPollerActor = jobPollerActor;
        this.databaseActor = databaseActor;
        this.pollInterval = pollInterval;
        this.companyDiscoveryInterval = companyDiscoveryInterval;
        this.currentCompanies = List.of();
        
        // Don't start timers here - wait until we have companies
        // The Main class will send an initial DiscoverCompanies message
        logger.info("SchedulerActor created - waiting for initial company list");
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(SchedulePoll.class, this::onSchedulePoll)
                .onMessage(DiscoverCompanies.class, this::onDiscoverCompanies)
                .onMessage(CompanyListUpdated.class, this::onCompanyListUpdated)
                .onAnyMessage(msg -> {
                    logger.warn("Received unexpected message: {}", msg.getClass().getSimpleName());
                    return Behaviors.same();
                })
                .build();
    }

    private Behavior<Object> onSchedulePoll(SchedulePoll msg) {
        try {
            logger.info("📡 Received SchedulePoll message - starting job polling cycle");
            
            // Poll for all current companies
            if (currentCompanies.isEmpty()) {
                logger.warn("⚠️ No companies to poll, scheduling next poll in {} minutes", pollInterval.toMinutes());
                scheduleNextPoll();
                return Behaviors.same();
            }
            
            logger.info("🏢 Starting to poll {} companies: {}", currentCompanies.size(), 
                currentCompanies.stream().map(CompanyConfig::getName).collect(java.util.stream.Collectors.joining(", ")));
            
            for (CompanyConfig company : currentCompanies) {
                try {
                    logger.info("📞 Sending PollJobs message to JobPollerActor for company: {}", company.getName());
                    jobPollerActor.tell(new PollJobs(company));
                    logger.info("✅ PollJobs message sent successfully for company: {}", company.getName());
                } catch (Exception e) {
                    logger.error("❌ Error sending PollJobs for company {}: {}", company.getName(), e.getMessage());
                }
            }
            
            // Schedule next poll
            logger.info("⏰ Scheduling next poll in {} minutes", pollInterval.toMinutes());
            scheduleNextPoll();
            logger.info("✅ Polling cycle completed successfully");
            return Behaviors.same();
            
        } catch (Exception e) {
            logger.error("❌ Critical error in onSchedulePoll: {}", e.getMessage(), e);
            // Schedule next poll even on error to keep the system running
            logger.info("🔄 Attempting to schedule next poll after error");
            scheduleNextPoll();
            return Behaviors.same();
        }
    }

    private Behavior<Object> onDiscoverCompanies(DiscoverCompanies msg) {
        try {
            // Request updated company list from database
            databaseActor.tell(new GetAllJobData(getContext().getSelf()));
            logger.debug("Requested company discovery from database");
            return Behaviors.same();
        } catch (Exception e) {
            logger.error("Error in company discovery: {}", e.getMessage(), e);
            // Schedule next discovery even on error
            scheduleCompanyDiscovery();
            return Behaviors.same();
        }
    }

    private Behavior<Object> onCompanyListUpdated(CompanyListUpdated msg) {
        this.currentCompanies = msg.companies;
        logger.info("Company list updated. Now monitoring {} companies", currentCompanies.size());
        
        // Start timers only after we have companies
        if (currentCompanies.size() > 0) {
            logger.info("Starting continuous operation - scheduling first poll and company discovery");
            scheduleNextPoll();
            scheduleCompanyDiscovery();
            
            // ADD IMMEDIATE TEST POLL to verify the system works
            logger.info("🧪 Starting immediate test poll to verify system functionality");
            getContext().getSelf().tell(new SchedulePoll());
        }
        
        return Behaviors.same();
    }

    private void scheduleNextPoll() {
        try {
            logger.info("🔔 Scheduling next poll in {} minutes", pollInterval.toMinutes());
            getContext().scheduleOnce(pollInterval, getContext().getSelf(), new SchedulePoll());
            logger.info("✅ Next poll scheduled successfully for {} minutes from now", pollInterval.toMinutes());
        } catch (Exception e) {
            logger.error("❌ Error scheduling next poll: {}", e.getMessage(), e);
            // Try to schedule again after a short delay
            logger.info("🔄 Attempting to reschedule poll after 30 seconds due to error");
            getContext().scheduleOnce(Duration.ofSeconds(30), getContext().getSelf(), new SchedulePoll());
        }
    }

    private void scheduleCompanyDiscovery() {
        try {
            logger.info("🔔 Scheduling company discovery in {} minutes", companyDiscoveryInterval.toMinutes());
            getContext().scheduleOnce(companyDiscoveryInterval, getContext().getSelf(), new DiscoverCompanies());
            logger.info("✅ Company discovery scheduled successfully for {} minutes from now", companyDiscoveryInterval.toMinutes());
        } catch (Exception e) {
            logger.error("❌ Error scheduling company discovery: {}", e.getMessage(), e);
            // Try to schedule again after a short delay
            logger.info("🔄 Attempting to reschedule company discovery after 60 seconds due to error");
            getContext().scheduleOnce(Duration.ofSeconds(60), getContext().getSelf(), new DiscoverCompanies());
        }
    }

    public static class SchedulePoll {
        // Message to trigger polling
    }

    public static class DiscoverCompanies {
        // Message to trigger company discovery
    }

    public static class CompanyListUpdated {
        public final List<CompanyConfig> companies;
        
        public CompanyListUpdated(List<CompanyConfig> companies) {
            this.companies = companies;
        }
    }
}
