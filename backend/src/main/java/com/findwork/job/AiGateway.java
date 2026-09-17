package com.findwork.job;

public interface AiGateway {
    JobSemanticAnalysis analyze(JobPosting job);

    String modelName();

    boolean configured();
}
