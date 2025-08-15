package com.jobmonitor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AshbyResponse {
    private List<Job> jobs;

    public List<Job> getJobs() { return jobs; }
    public void setJobs(List<Job> jobs) { this.jobs = jobs; }

    @Override
    public String toString() {
        return "AshbyResponse{" +
                "jobs=" + jobs +
                '}';
    }
}
