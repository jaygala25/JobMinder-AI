package com.jobmonitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResumeService {
    private static final Logger logger = LoggerFactory.getLogger(ResumeService.class);
    private final String resumeFilePath;
    private String resumeText;

    public ResumeService(String resumeFilePath) {
        this.resumeFilePath = resumeFilePath;
        loadResume();
    }

    private void loadResume() {
        try {
            Path path = Paths.get(resumeFilePath);
            if (Files.exists(path)) {
                this.resumeText = Files.readString(path);
                logger.info("Resume loaded successfully from: {}", resumeFilePath);
            } else {
                logger.warn("Resume file not found at: {}, using default resume", resumeFilePath);
                this.resumeText = getDefaultResume();
            }
        } catch (IOException e) {
            logger.error("Error loading resume from file: {}", e.getMessage(), e);
            this.resumeText = getDefaultResume();
        }
    }

    public String getResumeText() {
        return resumeText;
    }

    private String getDefaultResume() {
        return "JAY GALA\n" +
               "Software Engineer & AI/ML Specialist\n\n" +
               "EXPERIENCE\n\n" +
               "Senior Software Engineer | Tech Company | 2020-2023\n" +
               "- Led development of microservices architecture serving 1M+ users\n" +
               "- Implemented CI/CD pipelines reducing deployment time by 60%\n" +
               "- Mentored 5 junior developers and conducted code reviews\n" +
               "- Technologies: Java, Spring Boot, Docker, Kubernetes, AWS\n\n" +
               "Software Engineer | Startup | 2018-2020\n" +
               "- Built RESTful APIs and frontend applications\n" +
               "- Collaborated with cross-functional teams on agile projects\n" +
               "- Technologies: Java, JavaScript, React, PostgreSQL\n\n" +
               "AI/ML Engineer | AI Company | 2023-Present\n" +
               "- Developed machine learning models for natural language processing\n" +
               "- Implemented LLM integration and prompt engineering\n" +
               "- Built scalable AI infrastructure using Python, TensorFlow, PyTorch\n" +
               "- Technologies: Python, TensorFlow, PyTorch, LangChain, OpenAI API\n\n" +
               "EDUCATION\n" +
               "Bachelor of Science in Computer Science | University | 2018\n\n" +
               "SKILLS\n" +
               "Programming: Java, Python, JavaScript, SQL, Scala\n" +
               "Frameworks: Spring Boot, React, Node.js, TensorFlow, PyTorch\n" +
               "Cloud: AWS, Docker, Kubernetes, Google Cloud Platform\n" +
               "AI/ML: Machine Learning, Deep Learning, NLP, LLM Integration\n" +
               "Tools: Git, Jenkins, Jira, Maven, Gradle";
    }
}
