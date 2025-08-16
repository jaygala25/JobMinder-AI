package com.jobmonitor;

import com.jobmonitor.service.ResumeService;

public class SimpleDemo {
    public static void main(String[] args) {
        System.out.println("🚀 Starting Job Monitoring System Demo");
        
        try {
            // Test resume loading
            ResumeService resumeService = new ResumeService("resume.txt");
            String resumeText = resumeService.getResumeText();
            System.out.println("✅ Resume loaded successfully");
            System.out.println("📄 Resume preview: " + resumeText.substring(0, Math.min(100, resumeText.length())) + "...");
            
            // Show system architecture
            System.out.println("\n🏗️  System Architecture:");
            System.out.println("   • Akka Actor System for concurrent job processing");
            System.out.println("   • H2 Database for job data storage");
            System.out.println("   • Mistral AI for job analysis");
            System.out.println("   • Slack integration for notifications");
            System.out.println("   • Ashby API for job polling");
            
            // Show configured companies
            System.out.println("\n🏢 Configured Companies:");
            System.out.println("   • Kikoff (ashby-id: kikoff)");
            System.out.println("   • Company2 (ashby-id: company2)");
            System.out.println("   • Company3 (ashby-id: company3)");
            System.out.println("   • Company4 (ashby-id: company4)");
            System.out.println("   • Company5 (ashby-id: company5)");
            
            // Show polling schedule
            System.out.println("\n⏰ Polling Schedule:");
            System.out.println("   • Initial delay: 30 seconds");
            System.out.println("   • Poll interval: 15 minutes");
            System.out.println("   • Match threshold: 20%");
            
            System.out.println("\n🎯 Next Steps:");
            System.out.println("   1. Configure real API keys in application.conf");
            System.out.println("   2. Add real Ashby company IDs");
            System.out.println("   3. Set up Slack bot token");
            System.out.println("   4. Run the full application with: java -jar target/job-monitoring-system-1.0.0.jar");
            
            System.out.println("\n✅ Demo completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
