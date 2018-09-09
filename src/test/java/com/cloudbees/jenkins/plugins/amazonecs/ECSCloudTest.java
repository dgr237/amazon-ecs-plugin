package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ListTasksResult;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

public class ECSCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    ECSInitializingSlavesResolver initializingSlavesResolver;
    ECSTaskTemplate testTemplate;
    ECSCloud testCloud;
    ECSService mockService;
    ECSClient mockClient;
    @Before
    public void setup()
    {
        mockClient=mock(ECSClient.class);
        mockService=new ECSService("Credentials","us-east-1");
        mockService.init(mockClient);
        initializingSlavesResolver=mock(ECSInitializingSlavesResolver.class);
        Mockito.when(initializingSlavesResolver.getInitializingECSSlaves(null)).thenReturn(Collections.EMPTY_SET);
        testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"cloudbees/maven-java","FARGATE",null,2048,0,2048,"subnet","secGroup",true,false,null,null,null,null,null,null);
        testCloud=Mockito.spy(new ECSCloud("ECS Cloud", Arrays.asList(testTemplate),"ecsUser","ecsClusterArn","us-east-1","http://jenkinsUrl",30));
        Mockito.when(testCloud.getEcsService()).thenReturn(mockService);
        Mockito.when(mockClient.listTasks(any())).thenReturn(new ListTasksResult());
    }

    @Test
    public void canProvision() {
        Label label=Jenkins.get().getLabel("maven-java");
        Assert.assertTrue(testCloud.canProvision(label));
    }

    @Test
    public void Provision() throws ExecutionException, InterruptedException {
        Label label=Jenkins.get().getLabel("maven-java");
        Collection<NodeProvisioner.PlannedNode> result = testCloud.provision(label, 1);
        assertEquals(1, result.size());
        List<NodeProvisioner.PlannedNode> provisioners=new ArrayList<>(result);
        assertEquals("ECS Slave maven-java",provisioners.get(0).displayName);
        assertEquals(1,provisioners.get(0).numExecutors);
    }
}