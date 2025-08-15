package com.jobmonitor.service;

import com.jobmonitor.model.JobData;
import com.jobmonitor.model.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.jobmonitor.model.CompanyConfig;
import com.jobmonitor.model.AshbyResponse;
import com.fasterxml.jackson.databind.JsonNode;

public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private final HikariDataSource dataSource;
    private final ObjectMapper objectMapper;

    public DatabaseService(String url, String username, String password, int poolSize) {
        // Initialize ObjectMapper for JSON operations
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Load MySQL driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("MySQL driver loaded successfully");
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load MySQL driver", e);
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);      // 10 seconds
        config.setIdleTimeout(300000);          // 5 minutes
        config.setMaxLifetime(600000);          // 10 minutes
        config.setLeakDetectionThreshold(30000); // 30 seconds
        config.setValidationTimeout(5000);      // 5 seconds
        
        this.dataSource = new HikariDataSource(config);
        
        // Initialize database schema
        initializeSchema();
    }
    
    private void initializeSchema() {
        try (Connection conn = dataSource.getConnection()) {
            // Create job_data table if it doesn't exist
            String createTableSql = 
                "CREATE TABLE IF NOT EXISTS job_data (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "company_name VARCHAR(255) NOT NULL, " +
                "ashby_name VARCHAR(255) NOT NULL, " +
                "job_data LONGTEXT NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "UNIQUE KEY unique_company (company_name)" +
                ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
                logger.info("Database schema initialized successfully");
            }
            
            // Create indexes if they don't exist
            String[] indexSqls = {
                "CREATE INDEX idx_company_name ON job_data(company_name)",
                "CREATE INDEX idx_ashby_name ON job_data(ashby_name)",
                "CREATE INDEX idx_created_at ON job_data(created_at)",
                "CREATE INDEX idx_updated_at ON job_data(updated_at)"
            };
            
            for (String indexSql : indexSqls) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(indexSql);
                } catch (SQLException e) {
                    // Index might already exist, ignore error
                    logger.debug("Index creation skipped (might already exist): {}", e.getMessage());
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to initialize database schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    /**
     * Upsert job data - insert new company data or update existing company's job data
     * Returns the list of new jobs found by comparing with existing data
     */
    public List<Job> upsertJobData(String companyName, String ashbyName, List<Job> currentJobs) {
        if (currentJobs == null || currentJobs.isEmpty()) {
            logger.warn("No jobs provided for company: {}", companyName);
            return new ArrayList<>();
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing job data for this company
                Optional<JobData> existingData = getJobDataInternal(conn, companyName);
                
                List<Job> newJobs = new ArrayList<>();
                
                if (existingData.isPresent()) {
                    // Compare with existing data to find new jobs
                    newJobs = findNewJobs(existingData.get().getJobData(), currentJobs);
                    logger.info("Found {} new jobs for company: {}", newJobs.size(), companyName);
                } else {
                    // No existing data, all jobs are new
                    newJobs = currentJobs;
                    logger.info("No existing data found, treating all {} jobs as new for company: {}", currentJobs.size(), companyName);
                }
                
                // Insert or update the company's job data
                insertOrUpdateCompanyData(conn, companyName, ashbyName, currentJobs);
                
                conn.commit();
                logger.info("Successfully upserted job data for company: {} with {} total jobs", companyName, currentJobs.size());
                return newJobs;
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (Exception e) {
            logger.error("Error upserting job data for company {}: {}", companyName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<Job> findNewJobs(String existingJobDataJson, List<Job> currentJobs) {
        // If no existing job data, all current jobs are new
        if (existingJobDataJson == null || existingJobDataJson.trim().isEmpty()) {
            logger.info("No existing job data found, treating all {} jobs as new", currentJobs.size());
            return currentJobs;
        }
        
        try {
            Job[] existingJobs = objectMapper.readValue(existingJobDataJson, Job[].class);
            
            Set<String> existingJobIds = java.util.Arrays.stream(existingJobs)
                    .map(Job::getId)
                    .collect(Collectors.toSet());
            
            List<Job> newJobs = currentJobs.stream()
                    .filter(job -> !existingJobIds.contains(job.getId()))
                    .collect(Collectors.toList());
            
            logger.info("Found {} existing jobs, {} new jobs", existingJobs.length, newJobs.size());
            return newJobs;
                    
        } catch (Exception e) {
            logger.error("Error parsing existing job data: {}", e.getMessage());
            logger.debug("Raw existing job data that failed to parse: {}", existingJobDataJson);
            // If we can't parse existing data, treat all as new
            return currentJobs;
        }
    }

    private void insertOrUpdateCompanyData(Connection conn, String companyName, String ashbyName, List<Job> jobs) throws SQLException, JsonProcessingException {
        String sql = "INSERT INTO job_data (company_name, ashby_name, job_data) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "ashby_name = VALUES(ashby_name), " +
                    "job_data = VALUES(job_data), " +
                    "updated_at = CURRENT_TIMESTAMP";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, companyName);
            stmt.setString(2, ashbyName);
            stmt.setString(3, objectMapper.writeValueAsString(jobs));
            stmt.executeUpdate();
        }
    }

    /**
     * Upsert raw JSON job data - insert new company data or update existing company's raw JSON
     * Returns the list of new jobs found by comparing with existing JSON data
     * IMPORTANT: Database is only updated AFTER comparison is done to avoid losing the old data
     */
    public List<Job> upsertRawJsonJobData(String companyName, String ashbyName, String rawJsonResponse) {
        if (rawJsonResponse == null || rawJsonResponse.trim().isEmpty()) {
            logger.warn("No raw JSON provided for company: {}", companyName);
            return new ArrayList<>();
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing job data for this company BEFORE any updates
                Optional<JobData> existingData = getJobDataInternal(conn, companyName);
                
                List<Job> newJobs = new ArrayList<>();
                
                if (existingData.isPresent()) {
                    // Compare existing JSON with new JSON to find new jobs
                    // This comparison happens BEFORE updating the database
                    newJobs = findNewJobsFromRawJson(existingData.get().getJobData(), rawJsonResponse);
                    logger.info("Found {} new jobs for company: {} by comparing existing JSON with new API response", 
                        newJobs.size(), companyName);
                } else {
                    // No existing data, parse all jobs from new JSON as new
                    newJobs = parseJobsFromRawJson(rawJsonResponse);
                    logger.info("No existing data found, treating all {} jobs as new for company: {}", 
                        newJobs.size(), companyName);
                }
                
                // ONLY AFTER comparison is done, update the database with new JSON
                logger.info("Updating database with new JSON response for company: {} after comparison", companyName);
                insertOrUpdateRawJsonData(conn, companyName, ashbyName, rawJsonResponse);
                
                conn.commit();
                logger.info("Successfully updated database for company: {} - Found {} new jobs, stored new JSON response", 
                    companyName, newJobs.size());
                return newJobs;
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (Exception e) {
            logger.error("Error upserting raw JSON data for company {}: {}", companyName, e.getMessage(), e);
            
            // If this is a JSON parsing error, log additional details
            if (e.getMessage() != null && e.getMessage().contains("corrupted")) {
                logger.error("JSON corruption detected for company: {}. Raw response length: {}", 
                    companyName, rawJsonResponse != null ? rawJsonResponse.length() : "null");
                
                // Don't update the database with corrupted data
                logger.error("Skipping database update for company {} due to corrupted JSON", companyName);
            }
            
            return new ArrayList<>();
        }
    }

    /**
     * Parse jobs from raw JSON response
     */
    private List<Job> parseJobsFromRawJson(String rawJsonResponse) {
        if (rawJsonResponse == null || rawJsonResponse.trim().isEmpty()) {
            logger.warn("Raw JSON response is null or empty");
            return new ArrayList<>();
        }
        
        // Validate JSON structure before parsing
        try {
            // First, validate that the JSON is well-formed
            objectMapper.readTree(rawJsonResponse);
            
            // Check for suspicious patterns that indicate corruption
            if (rawJsonResponse.length() < 1000) {
                logger.warn("Raw JSON response is suspiciously short ({} chars), may be truncated", rawJsonResponse.length());
            }
            
            // Check if the JSON ends properly
            if (!rawJsonResponse.trim().endsWith("}")) {
                logger.error("Raw JSON response does not end with '}', appears to be truncated");
                throw new RuntimeException("JSON response appears to be truncated");
            }
            
            AshbyResponse ashbyResponse = objectMapper.readValue(rawJsonResponse, AshbyResponse.class);
            if (ashbyResponse.getJobs() == null) {
                logger.warn("AshbyResponse contains no jobs array");
                return new ArrayList<>();
            }
            
            // Filter out jobs that are not listed (closed positions) and clean them
            List<Job> jobs = ashbyResponse.getJobs().stream()
                    .filter(Job::isListed)
                    .map(this::cleanJobData)
                    .collect(Collectors.toList());
            
            logger.info("Successfully parsed {} jobs from raw JSON response", jobs.size());
            return jobs;
                    
        } catch (Exception e) {
            logger.error("Error parsing raw JSON response: {}", e.getMessage());
            
            // Log additional details for debugging
            if (rawJsonResponse.length() > 200) {
                logger.error("JSON response preview (first 200 chars): {}", rawJsonResponse.substring(0, 200));
                logger.error("JSON response preview (last 200 chars): {}", 
                    rawJsonResponse.substring(Math.max(0, rawJsonResponse.length() - 200)));
            } else {
                logger.error("Full JSON response: {}", rawJsonResponse);
            }
            
            throw new RuntimeException("Failed to parse corrupted JSON response", e);
        }
    }

    /**
     * Find new jobs by comparing existing JSON with new raw JSON response
     * IMPORTANT: This method is called BEFORE updating the database to preserve the old data for comparison
     */
    private List<Job> findNewJobsFromRawJson(String existingJobDataJson, String newRawJsonResponse) {
        // If no existing job data, parse all from new JSON as new
        if (existingJobDataJson == null || existingJobDataJson.trim().isEmpty()) {
            logger.info("No existing job data found, treating all jobs from new JSON as new");
            return parseJobsFromRawJson(newRawJsonResponse);
        }
        
        try {
            // First, try to parse existing jobs as Job[] (legacy format)
            Job[] existingJobs = null;
            try {
                existingJobs = objectMapper.readValue(existingJobDataJson, Job[].class);
                logger.debug("Successfully parsed existing data as Job[] array");
            } catch (Exception e) {
                // If that fails, try to parse as raw JSON response (new format)
                logger.debug("Failed to parse existing data as Job[] array, trying as raw JSON response");
                try {
                    // Try to extract jobs from the raw JSON response format
                    JsonNode jsonNode = objectMapper.readTree(existingJobDataJson);
                    if (jsonNode.has("jobs") && jsonNode.get("jobs").isArray()) {
                        existingJobs = objectMapper.convertValue(jsonNode.get("jobs"), Job[].class);
                        logger.debug("Successfully parsed existing data as raw JSON response with jobs array");
                    } else {
                        logger.warn("Existing data doesn't contain a 'jobs' array, treating all as new");
                        return parseJobsFromRawJson(newRawJsonResponse);
                    }
                } catch (Exception e2) {
                    logger.warn("Failed to parse existing data as raw JSON response: {}", e2.getMessage());
                    logger.debug("Raw existing data that failed to parse: {}", existingJobDataJson);
                    // If we can't parse existing data, treat all from new JSON as new
                    return parseJobsFromRawJson(newRawJsonResponse);
                }
            }
            
            if (existingJobs == null || existingJobs.length == 0) {
                logger.info("No existing jobs found, treating all from new JSON as new");
                return parseJobsFromRawJson(newRawJsonResponse);
            }
            
            Set<String> existingJobIds = java.util.Arrays.stream(existingJobs)
                    .map(Job::getId)
                    .collect(Collectors.toSet());
            
            // Parse new jobs from NEW JSON response from API
            List<Job> newJobsFromJson = parseJobsFromRawJson(newRawJsonResponse);
            
            // Find jobs that don't exist in the OLD data
            List<Job> newJobs = newJobsFromJson.stream()
                    .filter(job -> !existingJobIds.contains(job.getId()))
                    .collect(Collectors.toList());
            
            logger.info("Comparison completed: Found {} existing jobs in database, {} new jobs from API", 
                existingJobs.length, newJobs.size());
            return newJobs;
                    
        } catch (Exception e) {
            logger.error("Error comparing existing job data with new raw JSON: {}", e.getMessage());
            logger.debug("Raw existing data that caused error: {}", existingJobDataJson);
            // If we can't parse existing data, treat all from new JSON as new
            return parseJobsFromRawJson(newRawJsonResponse);
        }
    }

    /**
     * Clean job data by removing unnecessary fields
     */
    private Job cleanJobData(Job job) {
        // This method would clean the job data similar to what was done in AshbyApiService
        // For now, return the job as-is since we're storing raw JSON
        return job;
    }

    /**
     * Insert or update raw JSON data for a company
     */
    private void insertOrUpdateRawJsonData(Connection conn, String companyName, String ashbyName, String rawJsonResponse) throws SQLException {
        String sql = "INSERT INTO job_data (company_name, ashby_name, job_data) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "ashby_name = VALUES(ashby_name), " +
                    "job_data = VALUES(job_data), " +
                    "updated_at = CURRENT_TIMESTAMP";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, companyName);
            stmt.setString(2, ashbyName);
            stmt.setString(3, rawJsonResponse); // Store the raw JSON directly
            stmt.executeUpdate();
        }
    }

    /**
     * Get job data for a company (legacy method for backward compatibility)
     */
    public Optional<JobData> getJobData(String companyName) {
        return getJobDataInternal(null, companyName);
    }

    private Optional<JobData> getJobDataInternal(Connection conn, String companyName) {
        String sql = "SELECT job_data FROM job_data WHERE company_name = ?";
        
        try (Connection connection = conn != null ? null : dataSource.getConnection();
             PreparedStatement stmt = (conn != null ? conn : connection).prepareStatement(sql)) {
            
            stmt.setString(1, companyName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String jobDataJson = rs.getString("job_data");
                    JobData jobData = new JobData();
                    jobData.setCompanyName(companyName);
                    jobData.setJobData(jobDataJson);
                    return Optional.of(jobData);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error getting job data for company {}: {}", companyName, e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    public List<JobData> getAllJobData() {
        String sql = "SELECT company_name, job_data FROM job_data";
        List<JobData> jobDataList = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String companyName = rs.getString("company_name");
                    String jobDataJson = rs.getString("job_data");
                    
                    JobData jobData = new JobData();
                    jobData.setCompanyName(companyName);
                    jobData.setJobData(jobDataJson);
                    jobDataList.add(jobData);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error getting all job data: {}", e.getMessage(), e);
        }
        
        return jobDataList;
    }

    /**
     * Get all distinct companies from the job_data table
     * @return List of company names and their corresponding ashby names
     */
    public List<CompanyConfig> getAllCompanies() {
        List<CompanyConfig> companies = new ArrayList<>();
        String sql = "SELECT DISTINCT company_name, ashby_name FROM job_data ORDER BY company_name";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String companyName = rs.getString("company_name");
                String ashbyName = rs.getString("ashby_name");
                companies.add(new CompanyConfig(companyName, ashbyName, ashbyName));
            }
            
            logger.info("Retrieved {} companies from database", companies.size());
        } catch (SQLException e) {
            logger.error("Error retrieving companies: {}", e.getMessage(), e);
        }
        
        return companies;
    }

    /**
     * Get companies by ashby name (for backward compatibility)
     * @param ashbyName the ashby name to search for
     * @return Optional CompanyConfig if found
     */
    public Optional<CompanyConfig> getCompanyByAshbyName(String ashbyName) {
        String sql = "SELECT DISTINCT company_name, ashby_name FROM job_data WHERE ashby_name = ? LIMIT 1";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, ashbyName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String companyName = rs.getString("company_name");
                    String foundAshbyName = rs.getString("ashby_name");
                    return Optional.of(new CompanyConfig(companyName, foundAshbyName, foundAshbyName));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving company by ashby name {}: {}", ashbyName, e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
