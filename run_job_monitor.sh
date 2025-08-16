#!/bin/bash

# Job Monitoring System Runner Script
# This script runs the job monitoring application and handles logging

# Set the working directory
cd /Users/jaygala25/Desktop/AI\ Agents\ Infra/Project

# Set log file
LOG_FILE="/tmp/job-monitor-$(date +%Y%m%d-%H%M%S).log"

# Log start time
echo "$(date): Starting Job Monitoring System..." | tee -a "$LOG_FILE"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "$(date): ERROR: Maven (mvn) is not installed or not in PATH" | tee -a "$LOG_FILE"
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "$(date): ERROR: Java is not installed or not in PATH" | tee -a "$LOG_FILE"
    exit 1
fi

# Check if the project directory exists
if [ ! -f "pom.xml" ]; then
    echo "$(date): ERROR: pom.xml not found. Please ensure you're in the correct project directory." | tee -a "$LOG_FILE"
    exit 1
fi

# Run the application
echo "$(date): Executing job monitoring application..." | tee -a "$LOG_FILE"

# Run with Maven and capture output
if mvn exec:java -Dexec.mainClass="com.jobmonitor.Main" 2>&1 | tee -a "$LOG_FILE"; then
    echo "$(date): Job monitoring completed successfully" | tee -a "$LOG_FILE"
    exit 0
else
    echo "$(date): ERROR: Job monitoring failed with exit code $?" | tee -a "$LOG_FILE"
    exit 1
fi
