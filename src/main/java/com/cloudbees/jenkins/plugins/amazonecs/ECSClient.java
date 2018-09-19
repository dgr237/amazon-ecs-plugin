package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;

interface ECSClient {

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
