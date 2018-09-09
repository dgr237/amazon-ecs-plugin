package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;
import hudson.AbortException;

import java.util.Collection;
import java.util.List;

public interface ECSClient {

    ListClustersResult listClusters(ListClustersRequest request);
    void stopTask(StopTaskRequest request);
    RegisterTaskDefinitionResult registerTaskDefinition(RegisterTaskDefinitionRequest request);
    DescribeTaskDefinitionResult describeTaskDefinition(DescribeTaskDefinitionRequest request);
    ListContainerInstancesResult listContainerInstances(ListContainerInstancesRequest request);
    DescribeContainerInstancesResult describeContainerInstances(DescribeContainerInstancesRequest request);
    ListTasksResult listTasks(ListTasksRequest request);
    DescribeTasksResult describeTasks(DescribeTasksRequest request);
    RunTaskResult runTask(RunTaskRequest request);
}
