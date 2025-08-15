package com.jobmonitor.service;

import com.jobmonitor.model.Job;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SlackService {
    private static final Logger logger = LoggerFactory.getLogger(SlackService.class);
    private final Slack slack;
    private final String token;
    private final String channel;
    private final String userId;

    public SlackService(String token, String channel, String userId) {
        this.slack = Slack.getInstance();
        this.token = token;
        this.channel = channel;
        this.userId = userId;
    }

    public String sendJobMatchNotification(Job job, double matchScore, String matchReason) {
        try {
            String message = buildJobMatchMessage(job, matchScore, matchReason);
            return sendMessage(message);
        } catch (Exception e) {
            logger.error("Error sending job match notification: {}", e.getMessage(), e);
            return null;
        }
    }

    public String sendMessage(String message) {
        try {
            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                    .token(token)
                    .channel(channel)
                    .text(message)
                    .build();

            ChatPostMessageResponse response = slack.methods(token).chatPostMessage(request);
            
            if (response.isOk()) {
                logger.info("Slack message sent successfully");
                return response.getTs();
            } else {
                logger.error("Failed to send Slack message: {}", response.getError());
                return null;
            }

        } catch (IOException | SlackApiException e) {
            logger.error("Error sending Slack message: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildJobMatchMessage(Job job, double matchScore, String matchReason) {
        StringBuilder message = new StringBuilder();
        message.append("🎯 *New Job Match Found!*\n\n");
        message.append("*Job Title:* ").append(job.getTitle()).append("\n");
        message.append("*Department:* ").append(job.getDepartment()).append("\n");
        message.append("*Team:* ").append(job.getTeam()).append("\n");
        message.append("*Location:* ").append(job.getLocation()).append("\n");
        message.append("*Employment Type:* ").append(job.getEmploymentType()).append("\n");
        message.append("*Remote:* ").append(job.isRemote() ? "Yes" : "No").append("\n");
        message.append("*Published:* ").append(job.getPublishedAt() != null ? job.getPublishedAt().toString().substring(0, 10) : "Unknown").append("\n");
        message.append("*Match Score:* ").append(String.format("%.1f", matchScore)).append("%\n\n");
        message.append("*Why This is a Good Match:*\n").append(matchReason).append("\n\n");
        
        if (job.getJobUrl() != null) {
            message.append("*Job URL:* ").append(job.getJobUrl()).append("\n");
        }
        if (job.getApplyUrl() != null) {
            message.append("*Apply URL:* ").append(job.getApplyUrl()).append("\n");
        }
        
        return message.toString();
    }
}
