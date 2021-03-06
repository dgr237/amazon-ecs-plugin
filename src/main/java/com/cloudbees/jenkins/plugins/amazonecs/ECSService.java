/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import hudson.AbortException;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates interactions with Amazon ECS.
 */
public class ECSService {
    private static final Logger LOGGER = Logger.getLogger(ECSService.class.getName());

    private ECSClient client;
    private final String credentialsId;
    private final String regionName;

    ECSService(String credentialsId, String regionName) {
        this.credentialsId = credentialsId;
        this.regionName = regionName;
    }

    ECSService(AWSCredentialsProvider credentialsProvider, String regionName) {
        this("", regionName);
        this.client = new ECSClientImpl(credentialsProvider, regionName);
    }

    void init(ECSClient client)
    {
        this.client=client;
    }

    private synchronized ECSClient getAmazonECSClient() {
        if (client == null) {
            client = new ECSClientImpl(credentialsId, regionName);
        }
        return client;
    }

    public List<String> getClusterArns(){
        final List<String> allClusterArns = new ArrayList<>();
        String lastToken = null;
        do {
            ListClustersResult result = getAmazonECSClient().listClusters(new ListClustersRequest().withNextToken(lastToken));
            allClusterArns.addAll(result.getClusterArns());
            lastToken = result.getNextToken();
        } while (lastToken != null);
        Collections.sort(allClusterArns);
        return allClusterArns;
    }

    void deleteTask(String taskArn, String clusterArn) {
        LOGGER.log(Level.INFO, "Delete ECS Slave task: {0}", taskArn);
        try {
            getAmazonECSClient().stopTask(new StopTaskRequest().withTask(taskArn).withCluster(clusterArn));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Couldn't stop task arn " + taskArn + " caught exception: " + e.getMessage(), e);
        }
    }

    /**
     * Looks whether the latest task definition matches the desired one. If yes, returns the full TaskDefinition of the existing one.
     * If no, register a new task definition with desired parameters and returns the new TaskDefinition.
     */
    TaskDefinition registerTemplate(final ECSCloud cloud, final ECSTaskTemplate template) {
        String familyName = fullQualifiedTemplateName(cloud, template);
        TaskDefinition currentTaskDefinition = findTaskDefinition(familyName);
        RegisterTaskDefinitionRequest request = template.createRegisterTaskDefinitionRequestIfNotMatched(familyName, currentTaskDefinition);

        if (request != null) {
            final RegisterTaskDefinitionResult result = getAmazonECSClient().registerTaskDefinition(request);
            LOGGER.log(Level.FINE, "Created Task Definition {0}: {1}", new Object[]{result.getTaskDefinition(), request});
            LOGGER.log(Level.INFO, "Created Task Definition: {0}", new Object[]{result.getTaskDefinition()});
            return result.getTaskDefinition();
        } else {
            return currentTaskDefinition;
        }
    }


    /**
     * Finds the task definition for the specified family or ARN, or null if none is found.
     * The parameter may be a task definition family, family with revision, or full task definition ARN.
     */
    TaskDefinition findTaskDefinition(String familyOrArn) {
        try {
            DescribeTaskDefinitionResult result = getAmazonECSClient().describeTaskDefinition(
                    new DescribeTaskDefinitionRequest()
                            .withTaskDefinition(familyOrArn));

            return result.getTaskDefinition();
        } catch (ClientException e) {
            LOGGER.log(Level.FINE, "No existing task definition found for family or ARN: " + familyOrArn, e);
            LOGGER.log(Level.INFO, "No existing task definition found for family or ARN: " + familyOrArn);

            return null;
        }
    }

    boolean checkIfAdditionalSlaveCanBeProvisioned(String cluster, ECSTaskTemplate template, int maxSlaves) {
        if (maxSlaves != 0) {
            List<String> allRunningTasks = getRunningTasks(cluster);
            LOGGER.log(Level.INFO, "ECS Slaves INITIALIZING/ RUNNING: {0}", allRunningTasks.size());
            if (allRunningTasks.size() >= maxSlaves) {
                LOGGER.log(Level.INFO, "ECS Slaves INITIALIZING/ RUNNING: {0}, exceeds max Slaves: {1}", new Object[]{allRunningTasks.size(), maxSlaves});
                return false;
            }
        }
        return template.isFargate() || areSufficientClusterResourcesAvailable(template, cluster);
    }

    List<String> getRunningTasks(String cluster) {
        ListTasksRequest request=new ListTasksRequest().withCluster(cluster).withDesiredStatus(DesiredStatus.RUNNING);
        final List<String> allTaskArns = new ArrayList<>();
        String lastToken = null;
        do {
            ListTasksResult result= getAmazonECSClient().listTasks(request.withNextToken(lastToken));
            allTaskArns.addAll(result.getTaskArns());
            lastToken = result.getNextToken();
        } while (lastToken != null);
        Collections.sort(allTaskArns);
        return allTaskArns;
    }

    String getTaskStatus(ECSCloud cloud, String taskArn) {
        DescribeTasksRequest request = new DescribeTasksRequest();
        request.setCluster(cloud.getCluster());
        request.setTasks(Arrays.asList(taskArn));

        DescribeTasksResult result = getAmazonECSClient().describeTasks(request);
        if (result.getTasks().isEmpty()) {
            return "UNKNOWN";
        }
        else {
            return result.getTasks().get(0).getLastStatus();
        }
    }

    private String fullQualifiedTemplateName(final ECSCloud cloud, final ECSTaskTemplate template) {
        return cloud.getDisplayName().replaceAll("\\s+","") + '-' + template.getTemplateName();
    }

    String runEcsTask(final ECSSlave slave, final ECSTaskTemplate template, String clusterArn, Collection<String> command, TaskDefinition taskDefinition) throws AbortException {
        KeyValuePair envNodeName = new KeyValuePair();
        envNodeName.setName("SLAVE_NODE_NAME");
        envNodeName.setValue(slave.getECSComputer().getName());

        KeyValuePair envNodeSecret = new KeyValuePair();
        envNodeSecret.setName("SLAVE_NODE_SECRET");
        envNodeSecret.setValue(slave.getECSComputer().getJnlpMac());

        // by convention, we assume the jenkins slave container is the first container in the task definition. ECS requires
        // all task definitions to contain at least one container, and all containers to have a name, so we do not need
        // to null- or bounds-check for the presence of a container definition.
        String slaveContainerName = taskDefinition.getContainerDefinitions().get(0).getName();

        LOGGER.log(Level.FINE, "Found container definition with {0} container(s). Assuming first container is the Jenkins slave: {1}", new Object[]{taskDefinition.getContainerDefinitions().size(), slaveContainerName});

        RunTaskRequest req = new RunTaskRequest()
                .withTaskDefinition(taskDefinition.getTaskDefinitionArn())
                .withLaunchType(LaunchType.fromValue(template.getLaunchType()))
                .withOverrides(new TaskOverride()
                        .withContainerOverrides(new ContainerOverride()
                                .withName(slaveContainerName)
                                .withCommand(command)
                                .withEnvironment(envNodeName)
                                .withEnvironment(envNodeSecret)))
                .withCluster(clusterArn);

        if (template.isFargate()) {
            AwsVpcConfiguration awsVpcConfiguration = new AwsVpcConfiguration();
            awsVpcConfiguration.setAssignPublicIp(template.getAssignPublicIp() ? "ENABLED" : "DISABLED");
            awsVpcConfiguration.setSecurityGroups(Arrays.asList(template.getSecurityGroups().split(",")));
            awsVpcConfiguration.setSubnets(Arrays.asList(template.getSubnets().split(",")));

            NetworkConfiguration networkConfiguration = new NetworkConfiguration();
            networkConfiguration.withAwsvpcConfiguration(awsVpcConfiguration);

            req.withNetworkConfiguration(networkConfiguration);
        }
        final RunTaskResult runTaskResult = getAmazonECSClient().runTask(req);


        if (!runTaskResult.getFailures().isEmpty()) {
            LOGGER.log(Level.WARNING, "Slave {0} - Failure to run task with definition {1} on ECS cluster {2}", new Object[]{slave.getNodeName(), taskDefinition.getTaskDefinitionArn(), clusterArn});
            for (Failure failure : runTaskResult.getFailures()) {
                LOGGER.log(Level.WARNING, "Slave {0} - Failure reason={1}, arn={2}", new Object[]{slave.getNodeName(), failure.getReason(), failure.getArn()});
            }
            throw new AbortException("Failed to run slave container " + slave.getNodeName());
        }
        return runTaskResult.getTasks().get(0).getTaskArn();
    }

    boolean areSufficientClusterResourcesAvailable(ECSTaskTemplate template, String clusterArn) {
        int i = 0;
        int j = template.getSlaveLaunchTimeoutSeconds();
        boolean hasEnoughResources = false;
        Object waitHandle = new Object();
        synchronized (waitHandle) {
            while (i++ < j && !hasEnoughResources) {
                List<String> containerArns = getContainerArns(clusterArn);
                DescribeContainerInstancesResult containerInstancesDesc = getAmazonECSClient().describeContainerInstances(new DescribeContainerInstancesRequest().withContainerInstances(containerArns).withCluster(clusterArn));
                LOGGER.log(Level.INFO, "Found {0} instances", containerInstancesDesc.getContainerInstances().size());
                hasEnoughResources = areEnoughResourcesAvailable(template, containerInstancesDesc);

                if (!hasEnoughResources) {
                    try {
                        waitHandle.wait(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        final String msg = MessageFormat.format("Interrupted while waiting for sufficient resources: {0} cpu units, {1}mb free memory", template.getCpu(), template.getMemoryConstraint());
                        LOGGER.log(Level.WARNING, msg);
                        return false;
                    }
                }
            }
        }

        if (!hasEnoughResources) {
            final String msg = MessageFormat.format("Timeout while waiting for sufficient resources: {0} cpu units, {1}mb free memory", template.getCpu(), template.getMemoryConstraint());
            LOGGER.log(Level.WARNING, msg);
            return false;
        }
        return true;
    }

    private boolean areEnoughResourcesAvailable(ECSTaskTemplate template,  DescribeContainerInstancesResult containerInstancesDesc) {
        boolean hasEnoughResources=false;
        for (ContainerInstance instance : containerInstancesDesc.getContainerInstances()) {
            LOGGER.log(Level.INFO, "Resources found in instance {1}: {0}", new Object[]{instance.getRemainingResources(), instance.getContainerInstanceArn()});
            int memoryResource = 0;
            int cpuResource = 0;
            for (Resource resource : instance.getRemainingResources()) {
                if ("MEMORY".equals(resource.getName())) {
                    memoryResource = resource.getIntegerValue();
                } else if ("CPU".equals(resource.getName())) {
                    cpuResource = resource.getIntegerValue();
                }
            }


            LOGGER.log(Level.INFO, "Instance {0} has {1}mb of free memory. {2}mb are required", new Object[]{instance.getContainerInstanceArn(), memoryResource, template.getMemoryConstraint()});
            LOGGER.log(Level.INFO, "Instance {0} has {1} units of free cpu. {2} units are required", new Object[]{instance.getContainerInstanceArn(), cpuResource, template.getCpu()});
            if (memoryResource >= template.getMemoryConstraint()
                    && cpuResource >= template.getCpu()) {
                hasEnoughResources = true;
                break;
            }
        }
        return hasEnoughResources;
    }

    private List<String> getContainerArns(String cluster) {
        ListContainerInstancesRequest request=new ListContainerInstancesRequest().withCluster(cluster);
        final List<String> allContainerArns = new ArrayList<>();
        String lastToken = null;
        do {
            ListContainerInstancesResult result= getAmazonECSClient().listContainerInstances(request.withNextToken(lastToken));
            allContainerArns.addAll(result.getContainerInstanceArns());
            lastToken = result.getNextToken();
        } while (lastToken != null);
        Collections.sort(allContainerArns);
        return allContainerArns;
    }

    public static class ECSClientImpl implements ECSClient
    {
        private final String credentialsId;
        private final String regionName;
        private AWSCredentialsProvider provider;
        private AmazonECS client;

        ECSClientImpl(String credentialsId, String regionName) {
            this.credentialsId = credentialsId;
            this.regionName = regionName;
            this.provider=null;
        }

        ECSClientImpl(AWSCredentialsProvider provider, String regionName)
        {
            this("",regionName);
            this.provider=provider;
        }

        private synchronized AmazonECS getAmazonECSClient() {
            if(client==null) {
                ClientConfiguration clientConfiguration=JenkinsWrapper.getClientConfiguration();

                AWSCredentialsProvider credentials = getCredentials(credentialsId);
                final AmazonECSClientBuilder builder = AmazonECSClient.builder().withClientConfiguration(clientConfiguration).withRegion(regionName);
                if (credentials != null) {
                    builder.withCredentials(credentials);
                    // no credentials provided, rely on com.amazonaws.auth.DefaultAWSCredentialsProviderChain
                    // to use IAM Role define at the EC2 instance level ...
                    if (LOGGER.isLoggable(Level.FINE)) {
                        String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
                        String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4) + StringUtils.repeat("*", awsAccessKeyId.length() - (2 * 4)) + StringUtils.right(awsAccessKeyId, 4);
                        LOGGER.log(Level.FINE, "Connect to Amazon ECS with IAM Access Key {0}", obfuscatedAccessKeyId);
                    }
                }


                LOGGER.log(Level.FINE, "Selected Region: {0}", regionName);
                client= builder.build();
            }
            return client;
        }

        @CheckForNull
        private AWSCredentialsProvider getCredentials(@Nullable String credentialsId) {
            if(provider==null) {
                return AWSCredentialsHelper.getCredentials(credentialsId, JenkinsWrapper.getInstance());
            }
            return provider;
        }

        public ListClustersResult listClusters(ListClustersRequest request) {
            return getAmazonECSClient().listClusters(request);
        }

        public void stopTask(StopTaskRequest request) {
            getAmazonECSClient().stopTask(request);
        }

        public RegisterTaskDefinitionResult registerTaskDefinition(RegisterTaskDefinitionRequest request) {
            return getAmazonECSClient().registerTaskDefinition(request);
        }

        public DescribeTaskDefinitionResult describeTaskDefinition(DescribeTaskDefinitionRequest request) {
            return getAmazonECSClient().describeTaskDefinition(request);
        }

        public ListContainerInstancesResult listContainerInstances(ListContainerInstancesRequest request) {
            return getAmazonECSClient().listContainerInstances(request);
        }

        public DescribeContainerInstancesResult describeContainerInstances(DescribeContainerInstancesRequest request) {
            return getAmazonECSClient().describeContainerInstances(request);
        }

        public ListTasksResult listTasks(ListTasksRequest request) {
            return getAmazonECSClient().listTasks(request);
        }

        public DescribeTasksResult describeTasks(DescribeTasksRequest request) {
            return getAmazonECSClient().describeTasks(request);
        }

        public RunTaskResult runTask(RunTaskRequest request) {
            return getAmazonECSClient().runTask(request);
        }
    }
}
