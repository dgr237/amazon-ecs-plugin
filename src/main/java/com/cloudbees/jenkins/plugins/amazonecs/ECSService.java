package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.TaskDefinition;
import hudson.AbortException;

import java.util.Collection;
import java.util.List;

public interface ECSService {
    List<String> getClusterArns();

    void deleteTask(String taskArn, String clusterArn);

    TaskDefinition registerTemplate(ECSCloud cloud, ECSTaskTemplate template);

    TaskDefinition findTaskDefinition(String familyOrArn);

    List<String> getRunningTasks(ECSCloud cloud);

    String getTaskStatus(ECSCloud cloud, String taskArn);

    String runEcsTask(ECSSlave slave, ECSTaskTemplate template, String clusterArn, Collection<String> command, TaskDefinition taskDefinition) throws AbortException;

    boolean areSufficientClusterResourcesAvailable(int timeoutSeconds, ECSTaskTemplate template, String clusterArn);
}
