package com.jobmonitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer;
import java.time.OffsetDateTime;
import java.util.List;

public class Job {
    private String id;
    private String title;
    private String department;
    private String team;
    
    @JsonProperty("employmentType")
    private String employmentType;
    
    private String location;
    
    @JsonProperty("secondaryLocations")
    @JsonDeserialize(using = FlexibleListDeserializer.class)
    private List<String> secondaryLocations;
    
    @JsonProperty("publishedAt")
    private OffsetDateTime publishedAt;
    
    @JsonProperty("isListed")
    private boolean isListed;
    
    @JsonProperty("isRemote")
    private boolean isRemote;
    
    private Address address;
    
    @JsonProperty("jobUrl")
    private String jobUrl;
    
    @JsonProperty("applyUrl")
    private String applyUrl;
    
    @JsonProperty("descriptionHtml")
    private String descriptionHtml;
    
    @JsonProperty("descriptionPlain")
    private String descriptionPlain;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<String> getSecondaryLocations() { return secondaryLocations; }
    public void setSecondaryLocations(List<String> secondaryLocations) { this.secondaryLocations = secondaryLocations; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public boolean isListed() { return isListed; }
    public void setListed(boolean listed) { isListed = listed; }

    public boolean isRemote() { return isRemote; }
    public void setRemote(boolean remote) { isRemote = remote; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public String getJobUrl() { return jobUrl; }
    public void setJobUrl(String jobUrl) { this.jobUrl = jobUrl; }

    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }

    public String getDescriptionHtml() { return descriptionHtml; }
    public void setDescriptionHtml(String descriptionHtml) { this.descriptionHtml = descriptionHtml; }

    public String getDescriptionPlain() { return descriptionPlain; }
    public void setDescriptionPlain(String descriptionPlain) { this.descriptionPlain = descriptionPlain; }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", department='" + department + '\'' +
                ", team='" + team + '\'' +
                ", employmentType='" + employmentType + '\'' +
                ", location='" + location + '\'' +
                ", publishedAt=" + publishedAt +
                ", isListed=" + isListed +
                ", isRemote=" + isRemote +
                '}';
    }

    public static class Address {
        @JsonProperty("postalAddress")
        private PostalAddress postalAddress;

        public PostalAddress getPostalAddress() { return postalAddress; }
        public void setPostalAddress(PostalAddress postalAddress) { this.postalAddress = postalAddress; }

        public static class PostalAddress {
            @JsonProperty("addressRegion")
            private String addressRegion;
            
            @JsonProperty("addressCountry")
            private String addressCountry;
            
            @JsonProperty("addressLocality")
            private String addressLocality;

            public String getAddressRegion() { return addressRegion; }
            public void setAddressRegion(String addressRegion) { this.addressRegion = addressRegion; }

            public String getAddressCountry() { return addressCountry; }
            public void setAddressCountry(String addressCountry) { this.addressCountry = addressCountry; }

            public String getAddressLocality() { return addressLocality; }
            public void setAddressLocality(String addressLocality) { this.addressLocality = addressLocality; }
        }
    }
}
