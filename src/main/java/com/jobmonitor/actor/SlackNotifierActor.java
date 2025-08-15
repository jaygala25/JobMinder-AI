package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.JobMessages.*;
import com.jobmonitor.model.Job;
import com.jobmonitor.service.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackNotifierActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(SlackNotifierActor.class);
    private final SlackService slackService;
    private final ActorRef<Object> replyTo;

    public static Behavior<Object> create(SlackService slackService, ActorRef<Object> replyTo) {
        return Behaviors.setup(context -> new SlackNotifierActor(context, slackService, replyTo));
    }

    private SlackNotifierActor(ActorContext<Object> context, SlackService slackService, ActorRef<Object> replyTo) {
        super(context);
        this.slackService = slackService;
        this.replyTo = replyTo;
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(NotifyJobMatch.class, this::onNotifyJobMatch)
                .build();
    }

    private Behavior<Object> onNotifyJobMatch(NotifyJobMatch msg) {
        Job job = msg.getJob();
        String userId = msg.getUserId();
        double matchScore = msg.getMatchScore();
        String matchReason = msg.getMatchReason();
        
        getContext().getLog().info("📧 Sending Slack notification for job match: {} with score: {}%", job.getTitle(), matchScore);
        
        try {
            String slackMessageId = slackService.sendJobMatchNotification(job, matchScore, matchReason);
            
            boolean success = slackMessageId != null;
            String errorMessage = success ? null : "Failed to send Slack notification";
            
            NotificationSent response = new NotificationSent(job, userId, slackMessageId, success, errorMessage);
            replyTo.tell(response);
            
            if (success) {
                getContext().getLog().info("✅ Slack notification sent successfully for job: {} (Message ID: {})", job.getTitle(), slackMessageId);
            } else {
                getContext().getLog().error("❌ Failed to send Slack notification for job: {}", job.getTitle());
            }
            
        } catch (Exception e) {
            logger.error("❌ Error sending Slack notification: {}", e.getMessage(), e);
            
            NotificationSent response = new NotificationSent(job, userId, null, false, e.getMessage());
            replyTo.tell(response);
        }
        
        return this;
    }
}
