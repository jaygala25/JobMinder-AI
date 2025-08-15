package com.jobmonitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.message.JobMessages;
import com.jobmonitor.model.Job;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;
import java.util.Map;
import java.util.HashMap;

public class MistralService {
    private static final Logger logger = LoggerFactory.getLogger(MistralService.class);
    
    private static final String MISTRAL_API_URL = "https://api.mistral.ai/v1/chat/completions";
    
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final double matchThreshold;
    private final String resumeText;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public MistralService(String apiKey, String model, int maxTokens, double temperature, 
                         double matchThreshold, String resumeText) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.matchThreshold = matchThreshold;
        this.resumeText = resumeText;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)  // Increased from 30
            .readTimeout(120, TimeUnit.SECONDS)   // Increased from 60
            .writeTimeout(60, TimeUnit.SECONDS)   // Increased from 30
            .build();
    }

    /**
     * Analyze all jobs for a company with automatic batching to prevent timeouts
     */
    public List<JobMessages.JobMatchResult> analyzeJobMatchesForCompany(String companyName, List<Job> jobs) {
        List<JobMessages.JobMatchResult> matches = new ArrayList<>();
        
        if (jobs == null || jobs.isEmpty()) {
            logger.warn("No jobs to analyze for company: {}", companyName);
            return matches;
        }

        logger.info("Analyzing {} jobs for company {} with Mistral AI using automatic batching", jobs.size(), companyName);

        try {
            // Process jobs in smaller batches to prevent timeouts
            List<JobMessages.JobMatchResult> results = analyzeJobsInBatches(companyName, jobs);
            
            // Filter matches based on threshold
            for (JobMessages.JobMatchResult result : results) {
                if (result.isMatch() && result.getMatchScore() >= matchThreshold) {
                    matches.add(result);
                    logger.info("Match found for job: {} (Score: {})", result.getJob().getTitle(), result.getMatchScore());
                } else {
                    logger.debug("No match for job: {} (Score: {})", result.getJob().getTitle(), result.getMatchScore());
                }
            }
        } catch (IOException e) {
            logger.error("IO Error analyzing jobs for company {}: {}", companyName, e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Interrupted while analyzing jobs for company {}: {}", companyName, e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore interrupt status
        } catch (Exception e) {
            logger.error("Unexpected error analyzing jobs for company {}: {}", companyName, e.getMessage(), e);
        }

        logger.info("Found {} matching jobs out of {} analyzed for company {}", matches.size(), jobs.size(), companyName);
        return matches;
    }

    /**
     * Legacy method for backward compatibility - now delegates to company-based method
     */
    public List<JobMessages.JobMatchResult> analyzeJobMatches(List<Job> jobs) {
        // For backward compatibility, treat as a single company
        return analyzeJobMatchesForCompany("Unknown Company", jobs);
    }

    /**
     * Analyze all jobs for a company in a single batch request
     */
    private List<JobMessages.JobMatchResult> analyzeJobsInBatch(String companyName, List<Job> jobs) throws IOException {
        String prompt = buildBatchAnalysisPrompt(companyName, jobs);
        
        // Final validation of the prompt before sending to Mistral
        prompt = validateAndSanitizePrompt(prompt, companyName);
        
        // Test the prompt to ensure it's valid before sending to Mistral
        prompt = testAndFixPrompt(prompt, companyName);
        
        String response = callMistralAPI(prompt);
        return parseBatchMistralResponse(response, jobs);
    }

    /**
     * Analyze jobs in smaller batches to prevent timeouts
     */
    private List<JobMessages.JobMatchResult> analyzeJobsInBatches(String companyName, List<Job> jobs) throws IOException, InterruptedException {
        List<JobMessages.JobMatchResult> allResults = new ArrayList<>();
        
        // Determine optimal batch size based on job count
        int batchSize = calculateOptimalBatchSize(jobs.size());
        logger.info("Processing {} jobs for company {} in batches of {}", jobs.size(), companyName, batchSize);
        
        // Process jobs in batches
        for (int i = 0; i < jobs.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, jobs.size());
            List<Job> batch = jobs.subList(i, endIndex);
            
            logger.info("Processing batch {}/{} for company {} (jobs {}-{})", 
                (i / batchSize) + 1, (int) Math.ceil((double) jobs.size() / batchSize), 
                companyName, i + 1, endIndex);
            
            try {
                List<JobMessages.JobMatchResult> batchResults = analyzeJobsInBatch(companyName, batch);
                allResults.addAll(batchResults);
                logger.info("Successfully processed batch {}/{} for company {} - found {} results", 
                    (i / batchSize) + 1, (int) Math.ceil((double) jobs.size() / batchSize), 
                    companyName, batchResults.size());
                
                // Add a small delay between batches to prevent rate limiting
                if (i + batchSize < jobs.size()) {
                    Thread.sleep(1000); // 1 second delay between batches
                }
                
            } catch (IOException e) {
                // Handle JSON validation and API errors specifically
                logger.error("Critical error processing batch {}/{} for company {}: {}", 
                    (i / batchSize) + 1, (int) Math.ceil((double) jobs.size() / batchSize), 
                    companyName, e.getMessage());
                
                // For critical errors like malformed JSON, skip this batch and continue
                // but log the specific error for debugging
                if (e.getMessage().contains("JSON") || e.getMessage().contains("parse")) {
                    logger.error("JSON parsing error detected, skipping batch for company: {}", companyName);
                }
                continue;
                
            } catch (Exception e) {
                // Handle other unexpected errors
                logger.error("Unexpected error processing batch {}/{} for company {}: {}", 
                    (i / batchSize) + 1, (int) Math.ceil((double) jobs.size() / batchSize), 
                    companyName, e.getMessage(), e);
                
                // Continue with next batch instead of failing completely
                continue;
            }
        }
        
        return allResults;
    }

    /**
     * Calculate optimal batch size based on total job count
     */
    private int calculateOptimalBatchSize(int totalJobs) {
        if (totalJobs <= 20) {
            return totalJobs; // Process all at once for small batches
        } else if (totalJobs <= 50) {
            return 25; // Medium batches
        } else {
            return 20; // Small batches for large job counts to prevent timeouts
        }
    }

    /**
     * Clean job data by removing problematic fields before sending to LLM
     * Enhanced to remove complex fields that may contain special characters
     */
    private List<Job> cleanJobsForLLM(List<Job> jobs) {
        List<Job> cleanedJobs = new ArrayList<>();
        for (Job job : jobs) {
            // Create a clean copy of the job with only essential fields
            Job cleanedJob = new Job();
            cleanedJob.setId(job.getId() != null ? job.getId() : "unknown");
            cleanedJob.setTitle(job.getTitle() != null ? job.getTitle() : "Unknown Title");
            cleanedJob.setDepartment(job.getDepartment() != null ? job.getDepartment() : "");
            cleanedJob.setTeam(job.getTeam() != null ? job.getTeam() : "");
            cleanedJob.setEmploymentType(job.getEmploymentType() != null ? job.getEmploymentType() : "");
            cleanedJob.setLocation(job.getLocation() != null ? job.getLocation() : "");
            cleanedJob.setRemote(job.isRemote());
            cleanedJob.setPublishedAt(job.getPublishedAt());
            
            // Enhanced description cleaning - remove HTML and problematic content
            String cleanDescription = "";
            if (job.getDescriptionPlain() != null) {
                cleanDescription = job.getDescriptionPlain()
                    .replaceAll("<[^>]*>", "") // Remove HTML tags
                    .replaceAll("&[a-zA-Z0-9#]+;", "") // Remove HTML entities
                    .replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "") // Remove control characters
                    .replaceAll("\\s+", " ") // Normalize whitespace
                    .trim();
                
                // Truncate very long descriptions to prevent prompt issues
                if (cleanDescription.length() > 500) {
                    cleanDescription = cleanDescription.substring(0, 497) + "...";
                }
            }
            cleanedJob.setDescriptionPlain(cleanDescription);
            
            // Intentionally excluded fields to prevent JSON parsing errors:
            // - descriptionHtml: Contains HTML tags and special characters
            // - secondaryLocations: Contains array structures
            // - address: Contains nested object structures with special characters
            // - jobUrl: May contain special characters in URLs
            // - applyUrl: May contain special characters in URLs
            
            cleanedJobs.add(cleanedJob);
        }
        return cleanedJobs;
    }

    /**
     * Build a comprehensive prompt for analyzing all jobs from a company
     */
    private String buildBatchAnalysisPrompt(String companyName, List<Job> jobs) throws IOException {
        // Validate that all jobs have valid data before building the prompt
        validateJobsData(jobs, companyName);
        
        // Clean jobs by removing problematic fields like descriptionHtml
        List<Job> cleanedJobs = cleanJobsForLLM(jobs);
        
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an expert job matching AI. Analyze these job postings against the candidate's resume.\n\n");
        promptBuilder.append("CANDIDATE PROFILE:\n");
        promptBuilder.append("- Experience Level: 2-3 years of professional software engineering experience\n");
        promptBuilder.append("- Current Role: Software Engineering Intern (Sep 2024 - Dec 2024)\n");
        promptBuilder.append("- Previous Role: Software Development Engineer 2 (Oct 2020 - Jul 2023)\n");
        promptBuilder.append("- Education: Master's in Computer Science (graduating May 2025)\n");
        promptBuilder.append("- Target Roles: Junior to Mid-level Software Engineer positions\n\n");
        
        promptBuilder.append("COMPANY: ").append(companyName).append("\n");
        promptBuilder.append("RESUME:\n").append(resumeText).append("\n\n");
        promptBuilder.append("JOB OPENINGS:\n");

        // Use sanitized job data to prevent JSON parsing errors
        for (int i = 0; i < cleanedJobs.size(); i++) {
            Job job = cleanedJobs.get(i);
            promptBuilder.append("--- JOB ").append(i + 1).append(" ---\n");
            promptBuilder.append("Job Title: ").append(sanitizeString(job.getTitle())).append("\n");
            promptBuilder.append("Department: ").append(sanitizeString(job.getDepartment())).append("\n");
            promptBuilder.append("Team: ").append(sanitizeString(job.getTeam())).append("\n");
            promptBuilder.append("Employment Type: ").append(sanitizeString(job.getEmploymentType())).append("\n");
            promptBuilder.append("Location: ").append(sanitizeString(job.getLocation())).append("\n");
            promptBuilder.append("Remote: ").append(job.isRemote() ? "Yes" : "No").append("\n");
            promptBuilder.append("Published Date: ").append(job.getPublishedAt() != null ? job.getPublishedAt().toString() : "Unknown").append("\n");
            promptBuilder.append("Job ID: ").append(job.getId()).append("\n");
            promptBuilder.append("Job Description:\n").append(sanitizeString(job.getDescriptionPlain())).append("\n\n");
        }

        promptBuilder.append("Return a JSON object with this exact structure:\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"jobs\": [\n");
        promptBuilder.append("    {\n");
        promptBuilder.append("      \"jobId\": \"actual-job-id-from-api\",\n");
        promptBuilder.append("      \"score\": 85,\n");
        promptBuilder.append("      \"whyGoodMatch\": \"Strong match due to relevant experience and skills\",\n");
        promptBuilder.append("      \"isMatch\": true\n");
        promptBuilder.append("    }\n");
        promptBuilder.append("  ]\n");
        promptBuilder.append("}\n\n");

        promptBuilder.append("RULES:\n");
        promptBuilder.append("1. Use the EXACT job ID from the job data above\n");
        promptBuilder.append("2. Only mark jobs as isMatch: true if score >= ").append(matchThreshold).append("\n");
        promptBuilder.append("3. Score range: 0-100\n");
        promptBuilder.append("4. Provide clear reasoning in whyGoodMatch field\n\n");

        promptBuilder.append("EXPERIENCE LEVEL FILTERING:\n");
        promptBuilder.append("- EXCLUDE jobs requiring 8+ years of experience or Director/VP/C-level positions\n");
        promptBuilder.append("- EXCLUDE jobs requiring 6+ years for Staff/Principal Engineer roles\n");
        promptBuilder.append("- PRIORITIZE roles suitable for 0-5 years of experience (Junior, Mid-level, Senior)\n");
        promptBuilder.append("- FOCUS on Software Engineer, Full Stack Engineer, Backend Engineer, Frontend Engineer positions\n\n");

        promptBuilder.append("Consider: skill alignment, experience level, location, remote work, employment type, and overall fit.\n");
        promptBuilder.append("IMPORTANT: If a job requires significantly more experience than you have, mark it as isMatch: false regardless of skill alignment.\n\n");

        String finalPrompt = promptBuilder.toString();
        
        // Final validation: ensure the prompt doesn't contain any control characters
        if (finalPrompt.matches(".*[\\x00-\\x1F\\x7F-\\x9F].*")) {
            logger.warn("Control characters detected in prompt for company {}, applying final cleanup", companyName);
            finalPrompt = finalPrompt.replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "");
        }
        
        // Additional safety: remove any remaining problematic characters
        finalPrompt = finalPrompt
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]", "") // Remove all control chars
            .replaceAll("[\\uFFFE\\uFFFF]", "") // Remove BOM and invalid Unicode
            .replaceAll("\\s+", " ") // Normalize whitespace
            .trim();
        
        // Log prompt length for debugging
        logger.debug("Built prompt for company {}: {} characters", companyName, finalPrompt.length());
        
        // Final validation: ensure the prompt is valid and not too long
        if (finalPrompt.isEmpty()) {
            logger.error("Prompt is empty after sanitization for company {}", companyName);
            throw new IOException("Failed to build valid prompt for company: " + companyName);
        }
        
        // Check prompt length - Mistral has limits
        if (finalPrompt.length() > 30000) {
            logger.warn("Prompt too long for company {} ({} chars), truncating to 30000", companyName, finalPrompt.length());
            finalPrompt = finalPrompt.substring(0, 29997) + "...";
        }
        
        // Final safety check - ensure no problematic characters remain
        if (finalPrompt.matches(".*[\\x00-\\x1F\\x7F-\\x9F].*")) {
            logger.error("Control characters still present in final prompt for company {}", companyName);
            throw new IOException("Failed to remove all control characters for company: " + companyName);
        }
        
                return finalPrompt;
    }
    
    /**
     * Test and fix the prompt before sending to Mistral API
     */
    private String testAndFixPrompt(String prompt, String companyName) throws IOException {
        if (prompt == null || prompt.isEmpty()) {
            throw new IOException("Prompt is null or empty for company: " + companyName);
        }
        
        // Test 1: Check for control characters
        if (prompt.matches(".*[\\x00-\\x1F\\x7F-\\x9F].*")) {
            logger.error("Control characters detected in testAndFixPrompt for company {}", companyName);
            throw new IOException("Control characters still present in prompt for company: " + companyName);
        }
        
        // Test 2: Check for extremely long prompts
        if (prompt.length() > 50000) {
            logger.error("Prompt extremely long for company {} ({} chars)", companyName, prompt.length());
            throw new IOException("Prompt too long for company: " + companyName);
        }
        
        // Test 3: Check for balanced quotes and brackets
        int singleQuotes = countChar(prompt, '\'');
        int doubleQuotes = countChar(prompt, '"');
        int openBraces = countChar(prompt, '{');
        int closeBraces = countChar(prompt, '}');
        int openBrackets = countChar(prompt, '[');
        int closeBrackets = countChar(prompt, ']');
        
        if (singleQuotes % 2 != 0 || doubleQuotes % 2 != 0) {
            logger.warn("Unbalanced quotes detected in prompt for company {}", companyName);
            // Fix unbalanced quotes by adding missing ones
            if (singleQuotes % 2 != 0) prompt += "'";
            if (doubleQuotes % 2 != 0) prompt += "\"";
        }
        
        if (openBraces != closeBraces || openBrackets != closeBrackets) {
            logger.warn("Unbalanced brackets detected in prompt for company {}", companyName);
            // Fix unbalanced brackets by adding missing ones
            while (closeBraces < openBraces) { prompt += "}"; closeBraces++; }
            while (closeBrackets < openBrackets) { prompt += "]"; closeBrackets++; }
        }
        
        // Test 4: Ensure the prompt doesn't contain problematic patterns
        if (prompt.contains("null") || prompt.contains("undefined")) {
            logger.warn("Prompt contains null/undefined values for company {}, cleaning", companyName);
            prompt = prompt.replaceAll("null", "unknown").replaceAll("undefined", "unknown");
        }
        
        logger.info("Prompt validation passed for company {}: {} characters", companyName, prompt.length());
        return prompt;
    }
    
    /**
     * Parse the batch response from Mistral AI with enhanced error handling
     */
    private List<JobMessages.JobMatchResult> parseBatchMistralResponse(String response, List<Job> jobs) throws IOException {
        List<JobMessages.JobMatchResult> results = new ArrayList<>();
        
        try {
            // Parse the full Mistral API response to extract the content
            JsonNode responseNode = objectMapper.readTree(response);
            
            // Extract the content from the choices array
            JsonNode choicesNode = responseNode.get("choices");
            if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode firstChoice = choicesNode.get(0);
                JsonNode messageNode = firstChoice.get("message");
                
                if (messageNode != null) {
                    String content = messageNode.get("content").asText();
                    logger.debug("Extracted content from Mistral response: {}", content);
                    
                    // Try to parse the content with enhanced error handling
                    try {
                        JsonNode contentNode = objectMapper.readTree(content);
                        results = parseJobsFromContent(contentNode, jobs);
                    } catch (Exception contentParseError) {
                        logger.warn("Failed to parse content as JSON, attempting content extraction and repair: {}", contentParseError.getMessage());
                        
                        // Try to extract and repair the JSON content
                        String extractedJson = extractJsonFromContent(content);
                        if (extractedJson != null) {
                            try {
                                String repairedJson = validateAndFixJsonStructure(extractedJson);
                                JsonNode repairedNode = objectMapper.readTree(repairedJson);
                                results = parseJobsFromContent(repairedNode, jobs);
                                logger.info("Successfully repaired and parsed JSON content");
                            } catch (Exception repairError) {
                                logger.error("JSON repair failed: {}", repairError.getMessage());
                                // Fall back to creating minimal results
                                results = createFallbackResults(jobs);
                            }
                        } else {
                            logger.error("Could not extract JSON from content");
                            results = createFallbackResults(jobs);
                        }
                    }
                } else {
                    logger.error("No message content found in Mistral response");
                    results = createFallbackResults(jobs);
                }
            } else {
                logger.error("Invalid response format: expected 'choices' array, got: {}", response);
                results = createFallbackResults(jobs);
            }
            
        } catch (Exception e) {
            logger.error("Error parsing Mistral response: {}", e.getMessage());
            logger.debug("Raw Mistral response that failed to parse: {}", response);
            // Instead of throwing, return fallback results
            results = createFallbackResults(jobs);
        }
        
                return results;
    }
    
    /**
     * Parse jobs from JSON content node
     */
    private List<JobMessages.JobMatchResult> parseJobsFromContent(JsonNode contentNode, List<Job> jobs) {
        List<JobMessages.JobMatchResult> results = new ArrayList<>();
        
        JsonNode jobsArray = contentNode.get("jobs");
        if (jobsArray != null && jobsArray.isArray()) {
            for (JsonNode jobResult : jobsArray) {
                try {
                    String jobId = jobResult.get("jobId").asText();
                    int score = jobResult.get("score").asInt();
                    String whyGoodMatch = jobResult.get("whyGoodMatch").asText();
                    boolean isMatch = jobResult.get("isMatch").asBoolean();
                    
                    // Find the job by its ID
                    Job job = null;
                    for (Job currentJob : jobs) {
                        if (currentJob.getId().equals(jobId)) {
                            job = currentJob;
                            break;
                        }
                    }

                    if (job != null) {
                        JobMessages.JobMatchResult result = new JobMessages.JobMatchResult(
                            job, "user1", score, whyGoodMatch, isMatch
                        );
                        results.add(result);
                    } else {
                        logger.warn("Job with ID {} not found in provided job list", jobId);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing individual job result: {}", e.getMessage());
                    // Continue with next job
                }
            }
        } else {
            logger.warn("No 'jobs' array found in content");
        }
        
        return results;
    }
    
    /**
     * Extract JSON content from text that may contain additional content
     */
    private String extractJsonFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Look for JSON object markers
        int startBrace = content.indexOf('{');
        int endBrace = content.lastIndexOf('}');
        
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            String jsonContent = content.substring(startBrace, endBrace + 1);
            logger.debug("Extracted JSON content from position {} to {}: {}", startBrace, endBrace, jsonContent);
            return jsonContent;
        }
        
        // If no braces found, look for array markers
        int startBracket = content.indexOf('[');
        int endBracket = content.lastIndexOf(']');
        
        if (startBracket != -1 && endBracket != -1 && endBracket > startBracket) {
            String jsonContent = content.substring(startBracket, endBracket + 1);
            logger.debug("Extracted JSON array from position {} to {}: {}", startBracket, endBracket, jsonContent);
            return jsonContent;
        }
        
        logger.debug("No JSON markers found in content");
        return null;
    }
    
    /**
     * Create fallback results when parsing fails
     */
    private List<JobMessages.JobMatchResult> createFallbackResults(List<Job> jobs) {
        List<JobMessages.JobMatchResult> results = new ArrayList<>();
        
        // Create minimal results for each job to prevent complete failure
        for (Job job : jobs) {
            try {
                JobMessages.JobMatchResult result = new JobMessages.JobMatchResult(
                    job, "user1", 0, "Analysis failed - using fallback", false
                );
                results.add(result);
            } catch (Exception e) {
                logger.warn("Error creating fallback result for job {}: {}", job.getId(), e.getMessage());
            }
        }
        
        logger.info("Created {} fallback results for {} jobs", results.size(), jobs.size());
        return results;
    }
    
    /**
     * Extract JSON array from text that may contain additional content
     */
    private String extractJsonArray(String content) {
        // Find the first [ and last ] to extract the JSON array
        int startBracket = content.indexOf('[');
        int endBracket = content.lastIndexOf(']');
        
        if (startBracket != -1 && endBracket != -1 && endBracket > startBracket) {
            String jsonContent = content.substring(startBracket, endBracket + 1);
            logger.debug("Extracted JSON content: {}", jsonContent);
            return jsonContent;
        }
        
        // If no brackets found, return the original content
        logger.debug("No JSON brackets found, returning original content: {}", content);
        return content;
    }

    /**
     * Enhanced JSON validation and structural fixes
     */
    private String validateAndFixJsonStructure(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return "{}";
        }
        
        try {
            // First, try to parse as valid JSON to see if it's already correct
            objectMapper.readTree(jsonData);
            logger.debug("JSON data is already valid, no fixes needed");
            return jsonData;
            
        } catch (Exception e) {
            logger.warn("JSON validation failed, attempting structural fixes: {}", e.getMessage());
            
            // Apply structural fixes
            String fixedJson = applyStructuralFixes(jsonData);
            
            // Validate the fixed JSON
            try {
                objectMapper.readTree(fixedJson);
                logger.info("JSON structural fixes applied successfully");
                return fixedJson;
                
            } catch (Exception validationError) {
                logger.error("JSON structural fixes failed, falling back to safe fallback: {}", validationError.getMessage());
                return createSafeJsonFallback(jsonData);
            }
        }
    }
    
    /**
     * Apply structural fixes to malformed JSON
     */
    private String applyStructuralFixes(String jsonData) {
        String fixed = jsonData;
        
        // Fix common structural issues
        fixed = fixMissingCommas(fixed);
        fixed = fixUnmatchedBrackets(fixed);
        fixed = fixUnmatchedQuotes(fixed);
        fixed = fixTrailingCommas(fixed);
        fixed = fixMalformedArrays(fixed);
        fixed = fixMalformedObjects(fixed);
        
        // Additional fix for the specific "expecting comma delimiter" error
        fixed = fixCommaDelimiterIssues(fixed);
        
        return fixed;
    }
    
    /**
     * Fix missing commas between JSON fields
     */
    private String fixMissingCommas(String json) {
        // Pattern: "field": value"field" -> "field": value,"field"
        String fixed = json.replaceAll("(\"[^\"]+\"\\s*:\\s*[^,}\\]]+)\\s*(\"[^\"]+\"\\s*:)", "$1,$2");
        
        // Additional fix for missing commas in arrays
        fixed = fixed.replaceAll("(\\d+)\\s*(\\[)", "$1,$2");
        fixed = fixed.replaceAll("(\\])\\s*(\\d+)", "$1,$2");
        
        // Fix missing commas between array elements
        fixed = fixed.replaceAll("(\"[^\"]+\")\\s*(\"[^\"]+\")", "$1,$2");
        
        return fixed;
    }
    
    /**
     * Fix unmatched brackets
     */
    private String fixUnmatchedBrackets(String json) {
        int openBraces = countChar(json, '{');
        int closeBraces = countChar(json, '}');
        int openBrackets = countChar(json, '[');
        int closeBrackets = countChar(json, ']');
        
        StringBuilder fixed = new StringBuilder(json);
        
        // Add missing closing braces
        while (closeBraces < openBraces) {
            fixed.append("}");
            closeBraces++;
        }
        
        // Add missing closing brackets
        while (closeBrackets < openBrackets) {
            fixed.append("]");
            closeBrackets++;
        }
        
        return fixed.toString();
    }
    
    /**
     * Fix unmatched quotes
     */
    private String fixUnmatchedQuotes(String json) {
        int quoteCount = countChar(json, '"');
        if (quoteCount % 2 != 0) {
            // Odd number of quotes, add one more to balance
            return json + "\"";
        }
        return json;
    }
    
    /**
     * Fix trailing commas (remove them)
     */
    private String fixTrailingCommas(String json) {
        // Remove trailing commas before closing braces/brackets
        return json.replaceAll(",\\s*([}\\]])\\s*", "$1");
    }
    
    /**
     * Fix malformed arrays
     */
    private String fixMalformedArrays(String json) {
        // Fix missing commas between array elements
        String fixed = json;
        
        // Fix pattern: "value1" "value2" -> "value1", "value2"
        fixed = fixed.replaceAll("(\"[^\"]+\")\\s+(\"[^\"]+\")", "$1, $2");
        
        // Fix pattern: value1 value2 -> value1, value2 (for numeric arrays)
        fixed = fixed.replaceAll("(\\d+)\\s+(\\d+)", "$1, $2");
        
        // Fix pattern: true false -> true, false (for boolean arrays)
        fixed = fixed.replaceAll("(true|false)\\s+(true|false)", "$1, $2");
        
        return fixed;
    }
    
    /**
     * Fix malformed objects
     */
    private String fixMalformedObjects(String json) {
        // Fix objects with missing commas: {"key1": "value1" "key2": "value2"} -> {"key1": "value1", "key2": "value2"}
        return json.replaceAll("(\"[^\"]+\"\\s*:\\s*[^,}\\]]+)\\s+(\"[^\"]+\"\\s*:)", "$1, $2");
    }
    
    /**
     * Fix specific comma delimiter issues that cause "expecting ',' delimiter" errors
     */
    private String fixCommaDelimiterIssues(String json) {
        String fixed = json;
        
        // Fix missing commas between object properties
        // Pattern: "key": value "key2": value2 -> "key": value, "key2": value2
        fixed = fixed.replaceAll("(\"[^\"]+\"\\s*:\\s*[^,}\\]]+)\\s+(\"[^\"]+\"\\s*:)", "$1, $2");
        
        // Fix missing commas in arrays
        // Pattern: [item1 item2 item3] -> [item1, item2, item3]
        fixed = fixed.replaceAll("\\[([^\\]]+)\\s+([^\\]]+)\\]", "[$1, $2]");
        
        // Fix missing commas between array elements
        // Pattern: "value1" "value2" -> "value1", "value2"
        fixed = fixed.replaceAll("(\"[^\"]+\")\\s+(\"[^\"]+\")", "$1, $2");
        
        // Fix missing commas between numeric values
        // Pattern: 123 456 -> 123, 456
        fixed = fixed.replaceAll("(\\d+)\\s+(\\d+)", "$1, $2");
        
        return fixed;
    }
    
    /**
     * Count occurrences of a character in a string
     */
    private int countChar(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }
    
    /**
     * Create a safe JSON fallback when all fixes fail
     */
    private String createSafeJsonFallback(String originalData) {
        try {
            // Try to extract basic information and create a minimal valid JSON
            return "{\"error\": \"JSON parsing failed\", \"originalLength\": " + originalData.length() + ", \"fallback\": true}";
        } catch (Exception e) {
            return "{\"error\": \"Complete JSON failure\", \"fallback\": true}";
        }
    }

    /**
     * Sanitize job data to prevent JSON parsing errors
     */
    private String sanitizeJobDataForAPI(List<Job> jobs) {
        try {
            // Create a clean representation of jobs for the API
            List<Map<String, Object>> cleanJobs = new ArrayList<>();
            
            for (Job job : jobs) {
                Map<String, Object> cleanJob = new HashMap<>();
                cleanJob.put("id", job.getId());
                cleanJob.put("title", sanitizeString(job.getTitle()));
                cleanJob.put("department", sanitizeString(job.getDepartment()));
                cleanJob.put("team", sanitizeString(job.getTeam()));
                cleanJob.put("location", sanitizeString(job.getLocation()));
                cleanJob.put("employmentType", sanitizeString(job.getEmploymentType()));
                cleanJob.put("descriptionHtml", sanitizeString(job.getDescriptionHtml()));
                cleanJob.put("descriptionPlain", sanitizeString(job.getDescriptionPlain()));
                cleanJob.put("isRemote", job.isRemote());
                cleanJob.put("isListed", job.isListed());
                cleanJob.put("publishedAt", job.getPublishedAt());
                cleanJob.put("jobUrl", sanitizeString(job.getJobUrl()));
                cleanJob.put("applyUrl", sanitizeString(job.getApplyUrl()));
                
                cleanJobs.add(cleanJob);
            }
            
            String jsonString = objectMapper.writeValueAsString(cleanJobs);
            
            // Debug: Log the JSON length and check position 97220
            logger.info("Generated JSON length: {}", jsonString.length());
            if (jsonString.length() > 97220) {
                int start = Math.max(0, 97220 - 100);
                int end = Math.min(jsonString.length(), 97220 + 100);
                logger.error("JSON at position 97220 (context): {}", jsonString.substring(start, end));
                logger.error("Character at position 97220: '{}' (ASCII: {})", 
                    jsonString.charAt(97220), (int) jsonString.charAt(97220));
                
                // Also check for common JSON issues around this position
                String context = jsonString.substring(Math.max(0, 97220 - 200), Math.min(jsonString.length(), 97220 + 200));
                logger.error("Extended context around position 97220: {}", context);
                
                // Check for missing commas or structural issues
                String beforePos = jsonString.substring(0, 97220);
                String afterPos = jsonString.substring(97220);
                logger.error("JSON structure before position 97220 ends with: {}", beforePos.substring(Math.max(0, beforePos.length() - 50)));
                logger.error("JSON structure after position 97220 starts with: {}", afterPos.substring(0, Math.min(50, afterPos.length())));
            }
            
            // Apply enhanced JSON validation and structural fixes
            String validatedJson = validateAndFixJsonStructure(jsonString);
            
            // Additional validation: ensure the JSON is actually valid
            try {
                objectMapper.readTree(validatedJson);
                logger.debug("Job data sanitized and validated successfully for {} jobs", jobs.size());
            } catch (Exception e) {
                logger.error("JSON validation failed after structural fixes: {}", e.getMessage());
                // Try to create a minimal valid JSON as fallback
                validatedJson = createMinimalValidJson(jobs);
            }
            
            return validatedJson;
            
        } catch (Exception e) {
            logger.error("Error sanitizing job data: {}", e.getMessage());
            // Fallback to original data if sanitization fails
            try {
                String fallbackJson = objectMapper.writeValueAsString(jobs);
                return validateAndFixJsonStructure(fallbackJson);
            } catch (Exception fallbackError) {
                logger.error("Fallback serialization also failed: {}", fallbackError.getMessage());
                // Last resort: return a simple string representation
                return "{\"error\": \"Failed to serialize job data\", \"jobCount\": " + jobs.size() + "}";
            }
        }
    }
    
    /**
     * Sanitize string to remove invalid JSON characters
     * Enhanced to handle complex text content from job descriptions
     * Comprehensive control character removal and JSON-safe formatting
     */
    private String sanitizeString(String input) {
        if (input == null) return "";
        
        // First, remove ALL control characters (0x00-0x1F and 0x7F-0x9F) using regex
        String sanitized = input.replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "");
        
        // Replace problematic whitespace characters with regular spaces
        sanitized = sanitized
            .replace("\n", " ")     // Replace newlines with spaces
            .replace("\r", " ")     // Replace carriage returns with spaces
            .replace("\t", " ")     // Replace tabs with spaces
            .replace("\u00A0", " ") // Replace non-breaking spaces
            .replace("\u2000", " ") // Replace en quad
            .replace("\u2001", " ") // Replace em quad
            .replace("\u2002", " ") // Replace en space
            .replace("\u2003", " ") // Replace em space
            .replace("\u2004", " ") // Replace three-per-em space
            .replace("\u2005", " ") // Replace four-per-em space
            .replace("\u2006", " ") // Replace six-per-em space
            .replace("\u2007", " ") // Replace figure space
            .replace("\u2008", " ") // Replace punctuation space
            .replace("\u2009", " ") // Replace thin space
            .replace("\u200A", " ") // Replace hair space
            .replace("\u200B", " ") // Replace zero width space
            .replace("\u200C", " ") // Replace zero width non-joiner
            .replace("\u200D", " ") // Replace zero width joiner
            .replace("\u200E", " ") // Replace left-to-right mark
            .replace("\u200F", " ") // Replace right-to-left mark
            .replace("\u2028", " ") // Replace line separator
            .replace("\u2029", " ") // Replace paragraph separator
            .replace("\u202F", " ") // Replace narrow no-break space
            .replace("\u205F", " ") // Replace medium mathematical space
            .replace("\u2060", " ") // Replace word joiner
            .replace("\uFEFF", " "); // Replace zero width no-break space
        
        // Escape special JSON characters
        sanitized = sanitized
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\""); // Escape quotes
        
        // Remove multiple consecutive spaces
        sanitized = sanitized.replaceAll("\\s+", " ");
        
        // Truncate very long strings to prevent JSON parsing issues
        if (sanitized.length() > 1500) {
            sanitized = sanitized.substring(0, 1497) + "...";
            logger.debug("Truncated long string from {} to {} characters", input.length(), sanitized.length());
        }
        
        // Debug: Log if we're sanitizing a very long string that might cause issues
        if (sanitized.length() > 1000) {
            logger.debug("Sanitized long string ({} chars): {}", sanitized.length(), sanitized.substring(0, 100) + "...");
        }
        
        return sanitized.trim();
    }
    
    /**
     * Final validation and sanitization of the complete prompt before sending to Mistral
     */
    private String validateAndSanitizePrompt(String prompt, String companyName) throws IOException {
        if (prompt == null || prompt.isEmpty()) {
            throw new IOException("Prompt is null or empty for company: " + companyName);
        }
        
        // Remove ALL control characters and problematic Unicode
        String sanitized = prompt
            .replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "") // Remove all control chars
            .replaceAll("[\\uFFFE\\uFFFF]", "") // Remove BOM and invalid Unicode
            .replaceAll("[\\u200B-\\u200F]", "") // Remove zero-width characters
            .replaceAll("[\\u2028\\u2029]", "") // Remove line/paragraph separators
            .replaceAll("\\s+", " ") // Normalize whitespace
            .trim();
        
        // Validate the sanitized prompt
        if (sanitized.isEmpty()) {
            throw new IOException("Prompt became empty after sanitization for company: " + companyName);
        }
        
        // Check for any remaining control characters
        if (sanitized.matches(".*[\\x00-\\x1F\\x7F-\\x9F].*")) {
            logger.error("Control characters still present after sanitization for company: {}", companyName);
            throw new IOException("Failed to remove all control characters for company: " + companyName);
        }
        
        logger.debug("Prompt validated and sanitized for company {}: {} characters", companyName, sanitized.length());
        return sanitized;
    }
    
    /**
     * Create a minimal valid JSON as fallback when sanitization fails
     */
    private String createMinimalValidJson(List<Job> jobs) {
        try {
            List<Map<String, Object>> minimalJobs = new ArrayList<>();
            
            for (Job job : jobs) {
                Map<String, Object> minimalJob = new HashMap<>();
                minimalJob.put("id", job.getId() != null ? job.getId() : "unknown");
                minimalJob.put("title", job.getTitle() != null ? job.getTitle().replace("\"", "").substring(0, Math.min(100, job.getTitle().length())) : "Unknown Title");
                minimalJob.put("department", job.getDepartment() != null ? job.getDepartment().replace("\"", "") : "");
                minimalJob.put("location", job.getLocation() != null ? job.getLocation().replace("\"", "") : "");
                minimalJob.put("employmentType", job.getEmploymentType() != null ? job.getEmploymentType().replace("\"", "") : "");
                minimalJob.put("isRemote", job.isRemote());
                
                minimalJobs.add(minimalJob);
            }
            
            return objectMapper.writeValueAsString(minimalJobs);
            
        } catch (Exception e) {
            logger.error("Failed to create minimal valid JSON: {}", e.getMessage());
            // Last resort: return empty array
            return "[]";
        }
    }

    private com.jobmonitor.message.JobMessages.JobMatchResult analyzeSingleJob(Job job) throws IOException {
        String prompt = buildAnalysisPrompt(job);
        String response = callMistralAPI(prompt);
        return parseMistralResponse(response, job);
    }

    private String buildAnalysisPrompt(Job job) {
        return String.format(
            "Analyze this job posting against the resume and return a JSON response.\n\n" +
            "Job Title: %s\n" +
            "Department: %s\n" +
            "Team: %s\n" +
            "Employment Type: %s\n" +
            "Location: %s\n" +
            "Remote: %s\n" +
            "Published Date: %s\n" +
            "Job ID: %s\n\n" +
            "Job Description:\n%s\n\n" +
            "Resume:\n%s\n\n" +
            "Return a JSON object with this exact structure:\n" +
            "{\n" +
            "    \"score\": 85,\n" +
            "    \"whyGoodMatch\": \"Strong match due to relevant experience and skills\",\n" +
            "    \"isMatch\": true\n" +
            "}\n\n" +
            "RULES:\n" +
            "1. Score range: 0-100\n" +
            "2. Only mark as isMatch: true if score >= %s\n" +
            "3. Provide clear reasoning in whyGoodMatch field\n\n" +
            "Consider: skill alignment, experience level, location, remote work, employment type, and overall fit.",
            job.getTitle(),
            job.getDepartment(),
            job.getTeam(),
            job.getEmploymentType(),
            job.getLocation(),
            job.isRemote() ? "Yes" : "No",
            job.getPublishedAt() != null ? job.getPublishedAt().toString() : "Unknown",
            job.getId(),
            job.getDescriptionPlain(),
            resumeText,
            matchThreshold
        );
    }

    private String callMistralAPI(String prompt) throws IOException {
        // Create the request body for Mistral API with JSON response format
        String requestBody = String.format(
            "{\n" +
            "  \"model\": \"%s\",\n" +
            "  \"messages\": [\n" +
            "    {\n" +
            "      \"role\": \"user\",\n" +
            "      \"content\": \"%s\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"max_tokens\": %d,\n" +
            "  \"temperature\": %.1f,\n" +
            "  \"stream\": false,\n" +
            "  \"response_format\": {\"type\": \"json_object\"}\n" +
            "}",
            model,
            prompt.replace("\"", "\\\"").replace("\n", "\\n"),
            maxTokens,
            temperature
        );
        
        logger.debug("Mistral API request - Model: {}, Max Tokens: {}, Temperature: {}, Response Format: JSON", 
            model, maxTokens, temperature);
        logger.debug("Mistral API request body: {}", requestBody);

        Request request = new Request.Builder()
            .url(MISTRAL_API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(requestBody, MediaType.get("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody errorBody = response.body();
                String errorMessage = "Unexpected response code: " + response.code() + " - " + response.message();
                if (errorBody != null) {
                    errorMessage += " - " + errorBody.string();
                }
                throw new IOException(errorMessage);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response body from Mistral API");
            }

            String responseJson = responseBody.string();
            logger.debug("Mistral API response: {}", responseJson);

            // Since we're using response_format: {"type": "json_object"}, 
            // the response should be a clean JSON object that we can return directly
            // No need to parse the nested structure anymore
            return responseJson;
        }
    }

    private com.jobmonitor.message.JobMessages.JobMatchResult parseMistralResponse(String response, Job job) {
        try {
            // Since we're using response_format: {"type": "json_object"}, 
            // the response should be clean JSON without markdown formatting
            JsonNode jsonNode = objectMapper.readTree(response);
            
            double score = jsonNode.get("score").asDouble();
            String whyGoodMatch = jsonNode.get("whyGoodMatch").asText();
            boolean isMatch = jsonNode.get("isMatch").asBoolean() && score >= matchThreshold;
            
            return new com.jobmonitor.message.JobMessages.JobMatchResult(job, "user1", score, whyGoodMatch, isMatch);
            
        } catch (Exception e) {
            logger.error("Error parsing Mistral response: {}", e.getMessage(), e);
            return new com.jobmonitor.message.JobMessages.JobMatchResult(job, "user1", 0.0, "Error parsing response", false);
        }
    }

    /**
     * Check if a job description appears to be corrupted or truncated
     * This method is specifically for job descriptions, not JSON objects
     */
    private boolean isJobDescriptionCorrupted(String description) {
        if (description == null || description.trim().isEmpty()) {
            return true;
        }
        
        // Job descriptions are typically HTML/text content, not JSON
        // So we check for different corruption indicators
        
        String trimmed = description.trim();
        
        // Check for suspicious length (too short for a meaningful job description)
        if (trimmed.length() < 50) {
            return true;
        }
        
        // Check for obvious truncation patterns
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
            return true;
        }
        
        // Check for incomplete HTML tags (basic check)
        int openTags = (int) trimmed.chars().filter(ch -> ch == '<').count();
        int closeTags = (int) trimmed.chars().filter(ch -> ch == '>').count();
        if (openTags > 0 && Math.abs(openTags - closeTags) > 2) {
            return true;
        }
        
        // Check for excessive special characters that might indicate corruption
        long specialChars = trimmed.chars().filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch) && ch != '<' && ch != '>' && ch != '/' && ch != '=' && ch != '"' && ch != '\'' && ch != '&' && ch != ';' && ch != '-' && ch != '_' && ch != '.' && ch != ',' && ch != ':' && ch != '(' && ch != ')').count();
        if (specialChars > trimmed.length() * 0.3) { // More than 30% special chars
            return true;
        }
        
        return false;
    }

    /**
     * Validate that all jobs have valid data before processing
     */
    private void validateJobsData(List<Job> jobs, String companyName) throws IOException {
        if (jobs == null || jobs.isEmpty()) {
            throw new IOException("No jobs provided for company: " + companyName);
        }
        
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (job == null) {
                throw new IOException("Job at index " + i + " is null for company: " + companyName);
            }
            
            // Validate essential fields
            if (job.getId() == null || job.getId().trim().isEmpty()) {
                throw new IOException("Job at index " + i + " has null/empty ID for company: " + companyName);
            }
            
            if (job.getTitle() == null || job.getTitle().trim().isEmpty()) {
                logger.warn("Job {} has null/empty title for company: {}", job.getId(), companyName);
            }
            
            if (job.getDescriptionPlain() == null || job.getDescriptionPlain().trim().isEmpty()) {
                logger.warn("Job {} has null/empty description for company: {}", job.getId(), companyName);
            }
            
            // Check for corrupted job descriptions
            if (job.getDescriptionPlain() != null && isJobDescriptionCorrupted(job.getDescriptionPlain())) {
                logger.error("Job {} has corrupted description data for company: {}", job.getId(), companyName);
                throw new IOException("Job " + job.getId() + " has corrupted description data for company: " + companyName);
            }
        }
        
        logger.debug("Job data validation passed for {} jobs from company: {}", jobs.size(), companyName);
    }

    /**
     * Validate and fix raw JSON responses from external APIs
     */
    public String validateAndFixRawJsonResponse(String rawJsonResponse, String companyName) {
        if (rawJsonResponse == null || rawJsonResponse.trim().isEmpty()) {
            logger.warn("Empty JSON response received for company: {}", companyName);
            return "{}";
        }
        
        logger.debug("Validating raw JSON response for company: {} (length: {})", companyName, rawJsonResponse.length());
        
        try {
            // First, try to parse as valid JSON
            objectMapper.readTree(rawJsonResponse);
            logger.debug("Raw JSON response is valid for company: {}", companyName);
            return rawJsonResponse;
            
        } catch (Exception e) {
            logger.error("Raw JSON validation failed for company: {}, JSON appears corrupted: {}", companyName, e.getMessage());
            
            // Check if this is a truncated file issue
            if (rawJsonResponse.length() < 1000) {
                logger.error("Raw JSON response for company {} is suspiciously short ({} chars), may be truncated", 
                    companyName, rawJsonResponse.length());
            }
            
            // Try to apply structural fixes to the raw response
            String fixedJson = applyStructuralFixes(rawJsonResponse);
            
            // Validate the fixed JSON
            try {
                objectMapper.readTree(fixedJson);
                logger.info("Raw JSON structural fixes applied successfully for company: {}", companyName);
                return fixedJson;
                
            } catch (Exception validationError) {
                logger.error("Raw JSON structural fixes failed for company: {}, using fallback: {}", companyName, validationError.getMessage());
                
                // For corrupted JSON, return a minimal valid structure to prevent downstream errors
                return createSafeJsonFallback(rawJsonResponse);
            }
        }
    }
}
