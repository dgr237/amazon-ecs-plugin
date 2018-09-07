package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

public class ECSCloudTest {

    ECSInitializingSlavesResolver initializingSlavesResolver;
    ECSTaskTemplate testTemplate;
    ECSCloud testCloud;
    ECSService mockService;
    @Before
    public void setup()
    {
        mockService=mock(ECSService.class);
        initializingSlavesResolver=mock(ECSInitializingSlavesResolver.class);
        Mockito.when(initializingSlavesResolver.getInitializingECSSlaves(null)).thenReturn(Collections.EMPTY_SET);
        testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"cloudbees/maven-java","FARGATE",null,2048,0,2048,"subnet","secGroup",true,false,null,null,null,null,null,null);
        //Mockito.when(mockLabel.matches(any(Node.class))).thenReturn(true);
        testCloud=Mockito.spy(new ECSCloud("ECS Cloud", Arrays.asList(testTemplate),"ecsUser","ecsClusterArn","us-east-1","http://jenkinsUrl",30));
        Mockito.when(testCloud.getTemplate(org.mockito.Matchers.eq(null))).thenReturn(testTemplate);
        Mockito.when(testCloud.getEcsService()).thenReturn(mockService);

    }

    @Test
    public void canProvision() {
        Assert.assertTrue(testCloud.canProvision(null));
    }

    @Test
    public void Provision() throws ExecutionException, InterruptedException {
        Collection<NodeProvisioner.PlannedNode> result = testCloud.provision(null, 1);
        assertTrue(result.size() == 1);
        List<NodeProvisioner.PlannedNode> provisioners=new ArrayList<>(result);
        assertEquals("ECS Slave maven-java",provisioners.get(0).displayName);
        assertEquals(1,provisioners.get(0).numExecutors);
    }
}