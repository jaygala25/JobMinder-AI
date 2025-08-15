package com.jobmonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobmonitor.model.AshbyResponse;
import com.jobmonitor.model.Job;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AshbyApiService {
    private static final Logger logger = LoggerFactory.getLogger(AshbyApiService.class);
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AshbyApiService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))  // Increased for better reliability
                .readTimeout(Duration.ofSeconds(60))    // Increased for large JSON responses
                .writeTimeout(Duration.ofSeconds(15))   // Increased for better reliability
                .callTimeout(Duration.ofSeconds(90))    // Increased total call timeout
                .retryOnConnectionFailure(true)         // Enable retry on connection failures
                .build();
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public List<Job> pollJobs(String companyId) {
        String url = baseUrl + "/" + companyId;
        logger.info("🌐 Polling jobs from: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.debug("📡 Response code: {}", response.code());
            if (!response.isSuccessful()) {
                logger.error("❌ Failed to poll jobs. HTTP {}: {}", response.code(), response.message());
                return Collections.emptyList();
            }

            String responseBody = response.body().string();
            logger.debug("📄 Response body length: {} characters", responseBody.length());
            logger.debug("📄 First 200 chars: {}", responseBody.substring(0, Math.min(200, responseBody.length())));

            AshbyResponse ashbyResponse = objectMapper.readValue(responseBody, AshbyResponse.class);
            
            if (ashbyResponse.getJobs() == null) {
                logger.warn("⚠️ No jobs found in response");
                return Collections.emptyList();
            }

            // Filter out jobs that are not listed (closed positions)
            List<Job> activeJobs = ashbyResponse.getJobs().stream()
                    .filter(Job::isListed)
                    .collect(Collectors.toList());

            // Clean job data by removing unnecessary fields
            activeJobs = cleanJobData(activeJobs);

            logger.info("✅ Found {} active jobs for company {}", activeJobs.size(), companyId);
            return activeJobs;

        } catch (IOException e) {
            logger.error("❌ Error polling jobs from Ashby API: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("❌ Unexpected error while polling jobs: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Poll jobs and return the raw JSON response from Ashby API
     * This method is used to store the complete JSON response in the database
     */
    public String pollJobsRawJson(String companyId) {
        String url = baseUrl + "/" + companyId;
        logger.info("🌐 Polling raw JSON from: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.debug("📡 Response code: {}", response.code());
            
            // Check response headers for content type
            String contentType = response.header("Content-Type");
            if (contentType != null && !contentType.contains("application/json")) {
                logger.error("❌ Unexpected content type: {} for company {}", contentType, companyId);
            }
            
            if (!response.isSuccessful()) {
                logger.error("❌ Failed to poll raw JSON. HTTP {}: {}", response.code(), response.message());
                
                // Log response body for debugging
                try {
                    String errorBody = response.body().string();
                    logger.error("❌ Error response body: {}", errorBody);
                } catch (Exception e) {
                    logger.error("❌ Could not read error response body: {}", e.getMessage());
                }
                return null;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                logger.error("❌ Empty response body for company {}", companyId);
                return null;
            }

            String responseText = responseBody.string();
            
            // Validate response length
            if (responseText == null || responseText.trim().isEmpty()) {
                logger.error("❌ Empty or null response for company {}", companyId);
                return null;
            }
            
            logger.debug("📄 Raw JSON response length: {} characters", responseText.length());
            logger.debug("📄 First 200 chars: {}", responseText.substring(0, Math.min(200, responseText.length())));
            logger.debug("📄 Last 200 chars: {}", responseText.substring(Math.max(0, responseText.length() - 200)));

            // Validate JSON structure before returning
            try {
                objectMapper.readTree(responseText);
                logger.info("✅ Retrieved and validated raw JSON for company {} ({} chars)", companyId, responseText.length());
                return responseText;
            } catch (Exception e) {
                logger.error("❌ Invalid JSON response for company {}: {}", companyId, e.getMessage());
                logger.error("❌ JSON validation failed - response may be truncated or malformed");
                
                // Log more details about the response
                if (responseText.length() > 500) {
                    logger.error("❌ Response preview (first 500 chars): {}", responseText.substring(0, 500));
                    logger.error("❌ Response preview (last 500 chars): {}", responseText.substring(Math.max(0, responseText.length() - 500)));
                } else {
                    logger.error("❌ Full response: {}", responseText);
                }
                
                return null;
            }

        } catch (IOException e) {
            logger.error("❌ Error polling raw JSON from Ashby API: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("❌ Unexpected error while polling raw JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clean job data by removing unnecessary fields before processing
     * @param jobs List of jobs to clean
     * @return Cleaned list of jobs
     */
    private List<Job> cleanJobData(List<Job> jobs) {
        return jobs.stream()
                .map(this::cleanJob)
                .collect(Collectors.toList());
    }

    /**
     * Clean individual job by removing secondaryLocations and descriptionHtml fields
     * @param job Job to clean
     * @return Cleaned job
     */
    private Job cleanJob(Job job) {
        // Remove secondaryLocations field
        job.setSecondaryLocations(null);
        
        // Remove descriptionHtml field
        job.setDescriptionHtml(null);
        
        return job;
    }

    public void close() {
        // OkHttpClient doesn't need explicit closing
    }
}
