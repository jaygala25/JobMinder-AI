package com.jobmonitor;

import com.jobmonitor.service.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoRunner {
    private static final Logger logger = LoggerFactory.getLogger(DemoRunner.class);

    public static void main(String[] args) {
        logger.info("🚀 Starting Job Monitoring System Demo");
        
        try {
            // Test resume loading
            ResumeService resumeService = new ResumeService("resume.txt");
            String resumeText = resumeService.getResumeText();
            logger.info("✅ Resume loaded successfully");
            logger.info("📄 Resume preview: {}", resumeText.substring(0, Math.min(100, resumeText.length())) + "...");
            
            // Show system architecture
            logger.info("🏗️  System Architecture:");
            logger.info("   • Akka Actor System for concurrent job processing");
            logger.info("   • H2 Database for job data storage");
            logger.info("   • Mistral AI for job analysis");
            logger.info("   • Slack integration for notifications");
            logger.info("   • Ashby API for job polling");
            
            // Show configured companies
            logger.info("🏢 Configured Companies:");
            logger.info("   • Kikoff (ashby-id: kikoff)");
            logger.info("   • Company2 (ashby-id: company2)");
            logger.info("   • Company3 (ashby-id: company3)");
            logger.info("   • Company4 (ashby-id: company4)");
            logger.info("   • Company5 (ashby-id: company5)");
            
            // Show polling schedule
            logger.info("⏰ Polling Schedule:");
            logger.info("   • Initial delay: 30 seconds");
            logger.info("   • Poll interval: 15 minutes");
            logger.info("   • Match threshold: 20%");
            
            logger.info("🎯 Next Steps:");
            logger.info("   1. Configure real API keys in application.conf");
            logger.info("   2. Add real Ashby company IDs");
            logger.info("   3. Set up Slack bot token");
            logger.info("   4. Run the full application with: java -jar target/job-monitoring-system-1.0.0.jar");
            
            logger.info("✅ Demo completed successfully!");
            
        } catch (Exception e) {
            logger.error("❌ Demo failed: {}", e.getMessage(), e);
        }
    }
}
