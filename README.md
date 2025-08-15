# JobMinder AI

[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![Akka](https://img.shields.io/badge/Akka-2.8.5-red.svg)](https://akka.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0+-blue.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**JobMinder AI** is an intelligent, Akka-based job monitoring and alerting system that automatically discovers job opportunities from multiple companies and uses AI to match them against your resume. Get real-time Slack notifications for jobs that are a perfect fit for your skills and experience.

## 🎯 Features

- **🤖 AI-Powered Job Matching**: Uses Mistral AI to analyze job descriptions against your resume with configurable match thresholds
- **🔄 Continuous Monitoring**: Automatically polls job boards every 5 minutes to discover new opportunities
- **📱 Real-Time Notifications**: Instant Slack alerts when matching jobs are found
- **🏢 Multi-Company Support**: Monitor jobs from multiple companies simultaneously
- **💾 Persistent Storage**: MySQL database for storing job data and company configurations
- **⚡ High Performance**: Built with Akka actors for concurrent processing and scalability
- **🔧 Configurable**: Easy-to-modify polling intervals, match thresholds, and notification settings

## 🏗️ Architecture Overview

**JobMinder AI** is architected using the Akka toolkit, leveraging the actor model to build a concurrent, distributed, and resilient application. This design ensures efficient job monitoring, AI-powered job matching, and real-time notifications while maintaining high performance and fault tolerance.

### 🎭 Actor System Design

The system follows the Actor Model pattern, where each component is an independent actor that communicates through message passing. This design provides:

- **Concurrency**: Multiple actors can process jobs simultaneously
- **Isolation**: Actor failures are contained and don't affect the entire system
- **Scalability**: Actors can be distributed across multiple nodes
- **Resilience**: Built-in supervision strategies handle failures gracefully

### 🏛️ Actor Hierarchy & Responsibilities

#### **Root Actor System**
The main application creates a single `ActorSystem` that manages all actors:

```
JobMinderSystem (ActorSystem)
├── DatabaseActor
├── SlackNotifierActor
├── JobProcessorActor
├── LLMAnalyzerActor
├── MessageQueueActor
├── JobPollerActor
└── SchedulerActor
```

#### **Core Actors & Their Roles**

**🔄 SchedulerActor** - *System Orchestrator*
- Manages the overall scheduling and coordination
- Triggers periodic job polling for all companies
- Handles company discovery and dynamic company management
- Maintains continuous operation with configurable intervals
- Implements exponential backoff for failed operations

**🌐 JobPollerActor** - *Job Discovery Engine*
- Fetches job postings from Ashby API for configured companies
- Handles HTTP requests with retry logic and timeout management
- Processes raw JSON responses and extracts job data
- Filters out inactive/closed job postings
- Implements rate limiting to respect API constraints

**🤖 LLMAnalyzerActor** - *AI Analysis Engine*
- Integrates with Mistral AI for intelligent job matching
- Analyzes job descriptions against user resume
- Implements batching strategies to handle large job volumes
- Provides match scoring with configurable thresholds
- Handles AI API rate limits and timeouts gracefully

**⚙️ JobProcessorActor** - *Job Processing Pipeline*
- Receives and processes job analysis results
- Filters jobs based on match scores and criteria
- Manages job deduplication and state tracking
- Coordinates between AI analysis and notification systems
- Implements job processing workflows

**💬 SlackNotifierActor** - *Notification System*
- Sends real-time notifications to Slack channels
- Formats job match information for optimal readability
- Handles Slack API rate limits and retries
- Provides notification delivery status tracking
- Supports rich message formatting with job details

**💾 DatabaseActor** - *Data Persistence Layer*
- Manages all database operations (CRUD)
- Handles connection pooling and transaction management
- Stores job data, company configurations, and processing history
- Implements data consistency and integrity checks
- Provides data retrieval for system operations

**📬 MessageQueueActor** - *Job Processing Queue*
- Manages job processing queue with configurable concurrency
- Implements backpressure handling for high-load scenarios
- Provides queue monitoring and status reporting
- Handles job batching and prioritization
- Ensures fair job processing across companies

### 🔄 Message Flow Architecture

The system implements a sophisticated message flow pattern:

```
1. SchedulerActor → JobPollerActor: PollJobs(companyId)
2. JobPollerActor → DatabaseActor: UpsertRawJsonJobData(company, jobs)
3. JobPollerActor → MessageQueueActor: EnqueueJob(jobBatch)
4. MessageQueueActor → JobProcessorActor: ProcessNewJobs(jobs)
5. JobProcessorActor → LLMAnalyzerActor: AnalyzeJobMatches(jobs)
6. LLMAnalyzerActor → JobProcessorActor: JobMatchResults(results)
7. JobProcessorActor → SlackNotifierActor: NotifyJobMatch(match)
8. SlackNotifierActor → DatabaseActor: UpsertJobData(processedData)
```

### 🛡️ Supervision Strategy

The system employs a hierarchical supervision strategy:

- **One-for-One Strategy**: Each parent actor supervises its child actors individually
- **Restart Strategy**: Failed actors are automatically restarted with exponential backoff
- **Escalation**: Critical failures are escalated to parent actors
- **Circuit Breaker**: API failures trigger circuit breaker patterns to prevent cascading failures

### 📊 Data Flow Architecture

#### **Job Discovery Flow**
```
External APIs (Ashby) → JobPollerActor → DatabaseActor → MessageQueueActor
```

#### **AI Analysis Flow**
```
MessageQueueActor → JobProcessorActor → LLMAnalyzerActor → Mistral AI API
```

#### **Notification Flow**
```
JobProcessorActor → SlackNotifierActor → Slack API → User Notifications
```

### 🔧 Configuration-Driven Architecture

The system is highly configurable through `application.conf`:

- **Polling Intervals**: Configurable job discovery frequency
- **AI Parameters**: Model selection, token limits, temperature settings
- **Queue Management**: Concurrency limits and queue sizes
- **Database Settings**: Connection pooling and timeout configurations
- **Notification Settings**: Slack channel and formatting options

### 🚀 Performance Optimizations

#### **Concurrent Processing**
- Multiple actors process jobs simultaneously
- Configurable concurrency limits prevent resource exhaustion
- Async I/O operations for database and API calls

#### **Batching Strategies**
- Job analysis is batched to optimize AI API usage
- Database operations use batch inserts/updates
- Queue processing handles job batches efficiently

#### **Caching & State Management**
- Actor state maintains processing context
- Database connection pooling reduces overhead
- Intelligent retry mechanisms with exponential backoff

### 🔒 Error Handling & Resilience

#### **Fault Tolerance**
- Actor supervision automatically restarts failed components
- Circuit breaker patterns prevent cascading failures
- Graceful degradation when external services are unavailable

#### **Data Consistency**
- Database transactions ensure data integrity
- Idempotent operations prevent duplicate processing
- State recovery mechanisms for system restarts

#### **Monitoring & Observability**
- Comprehensive logging at all levels
- Performance metrics and health checks
- Error tracking and alerting capabilities

### 🌐 External Integrations

#### **Ashby API Integration**
- RESTful API client with retry logic
- JSON response parsing and validation
- Rate limiting and timeout management

#### **Mistral AI Integration**
- HTTP client for AI model interactions
- Prompt engineering for optimal job matching
- Response parsing and score extraction

#### **Slack API Integration**
- Real-time message posting
- Rich message formatting
- Delivery confirmation and error handling

#### **MySQL Database Integration**
- Connection pooling with HikariCP
- Prepared statements for security
- Transaction management and rollback capabilities

This architecture ensures **JobMinder AI** can handle high volumes of job data, provide intelligent matching through AI, and deliver reliable notifications while maintaining system stability and performance.

## 🚀 Quick Start

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- MySQL 8.0+
- Slack workspace with API access
- Mistral AI API key

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/jobminder-ai.git
cd jobminder-ai
```

### 2. Set Up the Database

```bash
# Make the setup script executable
chmod +x setup_mysql_database.sh

# Run the database setup (update MySQL root password if needed)
./setup_mysql_database.sh
```

### 3. Configure the Application

Update `src/main/resources/application.conf` with your credentials:

```hocon
job-monitor {
  database {
    url = "jdbc:mysql://localhost:3306/job_monitor"
    username = "jobmonitor"
    password = "jobmonitor123"
  }
  
  mistral {
    api-key = "your-mistral-api-key"
    model = "mistral-small-latest"
    match-threshold = 80.0
  }
  
  slack {
    token = "your-slack-bot-token"
    channel = "#job-alerts"
    user-id = "your-slack-user-id"
  }
  
  resume {
    file-path = "resume.txt"
  }
}
```

### 4. Add Your Resume

Replace `resume.txt` with your own resume in text format.

### 5. Add Companies to Monitor

Insert company configurations into the database:

```sql
INSERT INTO job_data (company_name, ashby_name, job_data) VALUES 
('Company Name', 'company-id', NULL);
```

### 6. Run the Application

```bash
# Option 1: Using Maven
mvn exec:java -Dexec.mainClass="com.jobmonitor.Main"

# Option 2: Using the provided script
chmod +x run_job_monitor.sh
./run_job_monitor.sh

# Option 3: Build and run JAR
mvn clean package
java -jar target/jobminder-ai-1.0.0.jar
```

## 📊 How It Works

1. **Job Discovery**: The system polls the Ashby API every 5 minutes for new job postings
2. **AI Analysis**: Each job description is analyzed by Mistral AI against your resume
3. **Match Scoring**: Jobs are scored based on relevance to your skills and experience
4. **Smart Filtering**: Only jobs above the configured match threshold (default: 80%) are processed
5. **Instant Notifications**: Matching jobs trigger immediate Slack notifications with job details and match reasoning
6. **Continuous Operation**: The system runs indefinitely, continuously monitoring for new opportunities

## 🔧 Configuration

### Polling Intervals

```hocon
job-monitor {
  polling {
    interval-minutes = 5        # How often to check for new jobs
  }
  
  company-discovery {
    interval-minutes = 30       # How often to check for new companies
  }
}
```

### AI Analysis Settings

```hocon
job-monitor {
  mistral {
    model = "mistral-small-latest"    # AI model to use
    max-tokens = 16000                # Maximum tokens per request
    temperature = 0.1                 # AI creativity level (0.0 = focused, 1.0 = creative)
    match-threshold = 80.0            # Minimum match score to trigger notification
  }
}
```

### Message Queue Settings

```hocon
job-monitor {
  message-queue {
    max-size = 1000              # Maximum jobs in processing queue
    max-concurrent = 5           # Maximum concurrent job processing
  }
}
```

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/jobminder/
│   │   ├── actor/              # Akka actors for system components
│   │   ├── message/            # Message classes for actor communication
│   │   ├── model/              # Data models and DTOs
│   │   ├── service/            # External service integrations
│   │   └── Main.java          # Application entry point
│   └── resources/
│       ├── application.conf    # Configuration file
│       └── schema.sql         # Database schema
├── test/                      # Unit tests
└── scripts/                   # Setup and utility scripts
```

## 🧪 Testing

Run the test suite:

```bash
mvn test
```

## 📝 Logging

The system uses SLF4J with Logback for comprehensive logging. Logs are written to:
- Console output
- `logs/` directory with daily rotation
- `app.log` for application-specific logs

## 🚨 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Ensure MySQL is running and accessible
   - Verify credentials in `application.conf`
   - Check if the database and user were created successfully

2. **Slack Notifications Not Working**
   - Verify Slack bot token and channel ID
   - Ensure the bot has permission to post in the specified channel
   - Check if the user ID is correct

3. **AI Analysis Failing**
   - Verify Mistral API key is valid
   - Check API rate limits and quotas
   - Ensure resume.txt exists and is readable

4. **No Jobs Being Found**
   - Verify company IDs in the database
   - Check Ashby API accessibility
   - Review polling logs for errors

### Debug Mode

Enable debug logging by updating `application.conf`:

```hocon
akka {
  loglevel = DEBUG
  stdout-loglevel = DEBUG
}
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Akka](https://akka.io/) - Actor-based concurrency framework
- [Mistral AI](https://mistral.ai/) - AI-powered job analysis
- [Slack API](https://api.slack.com/) - Real-time notifications
- [Ashby](https://ashbyhq.com/) - Job board API
- [MySQL](https://www.mysql.com/) - Database storage

## 📞 Support

If you encounter any issues or have questions:

1. Check the [troubleshooting section](#-troubleshooting)
2. Review the logs in the `logs/` directory
3. Open an issue on GitHub with detailed error information

---

**Happy job hunting with JobMinder AI! 🎯✨**
