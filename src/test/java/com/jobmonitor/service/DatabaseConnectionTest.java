package com.jobmonitor.service;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Test MySQL database connection
 */
public class DatabaseConnectionTest {
    
    public static void main(String[] args) {
        System.out.println("🔍 Testing MySQL database connection...");
        
        try {
            // Load configuration
            Config config = ConfigFactory.load();
            
            String url = config.getString("job-monitor.database.url");
            String username = config.getString("job-monitor.database.username");
            String password = config.getString("job-monitor.database.password");
            
            System.out.println("📋 Database Configuration:");
            System.out.println("   URL: " + url);
            System.out.println("   Username: " + username);
            System.out.println("   Password: " + "*".repeat(password.length()));
            
            System.out.println("\n🔌 Attempting to connect...");
            
            // Test connection
            Connection connection = DriverManager.getConnection(url, username, password);
            
            if (connection != null && !connection.isClosed()) {
                System.out.println("✅ SUCCESS: Connected to MySQL database!");
                System.out.println("   Database Product: " + connection.getMetaData().getDatabaseProductName());
                System.out.println("   Database Version: " + connection.getMetaData().getDatabaseProductVersion());
                
                // Test if job_monitor database exists
                try {
                    connection.createStatement().executeQuery("SELECT 1");
                    System.out.println("✅ Database query test passed!");
                } catch (SQLException e) {
                    System.out.println("⚠️  Database exists but query failed: " + e.getMessage());
                }
                
                connection.close();
                System.out.println("🔌 Connection closed successfully");
            } else {
                System.err.println("❌ Failed to establish connection");
            }
            
        } catch (SQLException e) {
            System.err.println("❌ SQL Error: " + e.getMessage());
            System.err.println("   Error Code: " + e.getErrorCode());
            System.err.println("   SQL State: " + e.getSQLState());
            
            // Common error suggestions
            if (e.getMessage().contains("Access denied")) {
                System.err.println("\n💡 Suggestion: Check your username/password");
            } else if (e.getMessage().contains("Unknown database")) {
                System.err.println("\n💡 Suggestion: Create the 'job_monitor' database first");
                System.err.println("   Run: CREATE DATABASE job_monitor;");
            } else if (e.getMessage().contains("Connection refused")) {
                System.err.println("\n💡 Suggestion: Make sure MySQL server is running");
                System.err.println("   Check with: brew services list | grep mysql");
            }
            
        } catch (Exception e) {
            System.err.println("❌ General Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
