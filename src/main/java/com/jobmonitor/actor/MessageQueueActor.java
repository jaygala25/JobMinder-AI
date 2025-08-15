package com.jobmonitor.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobmonitor.message.JobMessages.*;
import com.jobmonitor.model.Job;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MessageQueueActor acts as a simple message queue for job processing.
 * It provides backpressure control and decouples job polling from processing.
 */
public class MessageQueueActor extends AbstractBehavior<Object> {
    private static final Logger logger = LoggerFactory.getLogger(MessageQueueActor.class);
    
    private final ActorRef<Object> jobProcessorActor;
    private final Queue<ProcessNewJobs> jobQueue;
    private final int maxQueueSize;
    private final AtomicInteger activeJobs;
    private final int maxConcurrentJobs;
    
    public static Behavior<Object> create(ActorRef<Object> jobProcessorActor, int maxQueueSize, int maxConcurrentJobs) {
        return Behaviors.setup(context -> new MessageQueueActor(context, jobProcessorActor, maxQueueSize, maxConcurrentJobs));
    }
    
    private MessageQueueActor(ActorContext<Object> context, 
                             ActorRef<Object> jobProcessorActor, 
                             int maxQueueSize, 
                             int maxConcurrentJobs) {
        super(context);
        this.jobProcessorActor = jobProcessorActor;
        this.jobQueue = new LinkedList<>();
        this.maxQueueSize = maxQueueSize;
        this.activeJobs = new AtomicInteger(0);
        this.maxConcurrentJobs = maxConcurrentJobs;
        
        logger.info("MessageQueueActor created with maxQueueSize={}, maxConcurrentJobs={}", maxQueueSize, maxConcurrentJobs);
    }
    
    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnqueueJob.class, this::onEnqueueJob)
                .onMessage(JobProcessed.class, this::onJobProcessed)
                .onMessage(GetQueueStatus.class, this::onGetQueueStatus)
                .build();
    }
    
        private Behavior<Object> onEnqueueJob(EnqueueJob msg) {
        if (jobQueue.size() >= maxQueueSize) {
            logger.warn("Queue is full ({} jobs), rejecting new job batch for company: {}", 
                jobQueue.size(), msg.processNewJobs.getCompany().getName());
            // Could send rejection message back to sender
            return Behaviors.same();
        }
        
        jobQueue.offer(msg.processNewJobs);
        logger.debug("Job batch enqueued for company: {}. Queue size: {}", 
            msg.processNewJobs.getCompany().getName(), jobQueue.size());
        
        // Try to process jobs if we have capacity
        processNextJob();
        
        return Behaviors.same();
    }

    private Behavior<Object> onJobProcessed(JobProcessed msg) {
        int currentActive = activeJobs.decrementAndGet();
        logger.debug("Job batch processed for company: {}. Active jobs: {}", 
            msg.companyName, currentActive);
        
        // Process next job from queue
        processNextJob();
        
        return Behaviors.same();
    }

    private Behavior<Object> onGetQueueStatus(GetQueueStatus msg) {
        QueueStatus status = new QueueStatus(
            jobQueue.size(), 
            activeJobs.get(), 
            maxQueueSize, 
            maxConcurrentJobs
        );
        msg.sender.tell(status);
        return Behaviors.same();
    }
    
    private void processNextJob() {
        if (activeJobs.get() >= maxConcurrentJobs || jobQueue.isEmpty()) {
            return;
        }
        
        ProcessNewJobs processNewJobs = jobQueue.poll();
        if (processNewJobs != null) {
            activeJobs.incrementAndGet();
            logger.debug("Processing job batch for company: {}. Active jobs: {}", 
                processNewJobs.getCompany().getName(), activeJobs.get());
            
            // Send job batch to processor
            jobProcessorActor.tell(processNewJobs);
        }
    }
    
    // Message classes
    public static class EnqueueJob {
        public final ProcessNewJobs processNewJobs;
        
        public EnqueueJob(ProcessNewJobs processNewJobs) {
            this.processNewJobs = processNewJobs;
        }
    }
    
    public static class JobProcessed {
        public final String companyName;
        
        public JobProcessed(String companyName) {
            this.companyName = companyName;
        }
    }
    
    public static class GetQueueStatus {
        public final ActorRef<Object> sender;
        
        public GetQueueStatus(ActorRef<Object> sender) {
            this.sender = sender;
        }
    }
    
    public static class QueueStatus {
        public final int queueSize;
        public final int activeJobs;
        public final int maxQueueSize;
        public final int maxConcurrentJobs;
        
        public QueueStatus(int queueSize, int activeJobs, int maxQueueSize, int maxConcurrentJobs) {
            this.queueSize = queueSize;
            this.activeJobs = activeJobs;
            this.maxQueueSize = maxQueueSize;
            this.maxConcurrentJobs = maxConcurrentJobs;
        }
    }
}
