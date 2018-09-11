package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Label;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ECSInitializingSlavesResolverTest {

    @Test
    public void ECSSlaveWhichIsNotRunningShouldBeInTheResult()
    {

        ECSSlave slave=mock(ECSSlave.class);
        Mockito.when(slave.getNodeName()).thenReturn("InitializingNode");
        ECSTaskTemplate testTemplate=new ECSTaskTemplate("maven-java","cloudbees/maven-java","FARGATE",null,2048,0,2048,false,null,null,null,null,null).withLabel("maven-java").withSecurityGroups("secGroup").withSubnets("subnets").withPrivileged(true).withSingleRunTask(true).withIdleTerminationMinutes(1);
        ECSSlaveHelper helper=new ECSSlaveHelper(slave,"maven-java",testTemplate);
        helper.setTaskState(ECSSlaveHelper.State.Initializing);
        Mockito.when(slave.getHelper()).thenReturn(helper);
        Label label=mock(Label.class);

        ECSInitializingSlavesResolver resolver=Mockito.spy(new ECSInitializingSlavesResolver());
        doReturn(new Object[] {slave}).when(resolver).getNodes(label);
        Set<String> nodes= resolver.getInitializingECSSlaves(label);
        Assert.assertEquals(1,nodes.size());
        Assert.assertTrue(nodes.contains("InitializingNode"));
    }

    @Test
    public void ECSSlaveWhichIsRunningShouldNotBeInTheResult()
    {

        ECSSlave slave=mock(ECSSlave.class);
        Mockito.when(slave.getNodeName()).thenReturn("InitializingNode");
        ECSTaskTemplate testTemplate=new ECSTaskTemplate("maven-java","cloudbees/maven-java","FARGATE",null,2048,0,2048,false,null,null,null,null,null).withLabel("maven-java").withSecurityGroups("secGroup").withSubnets("subnets").withPrivileged(true).withSingleRunTask(true).withIdleTerminationMinutes(1);
        ECSSlaveHelper helper=new ECSSlaveHelper(slave,"maven-java",testTemplate);
        helper.setTaskState(ECSSlaveHelper.State.Running);
        Mockito.when(slave.getHelper()).thenReturn(helper);
        Label label=mock(Label.class);

        ECSInitializingSlavesResolver resolver=Mockito.spy(new ECSInitializingSlavesResolver());
        doReturn(new Object[] {slave}).when(resolver).getNodes(label);
        Set<String> nodes= resolver.getInitializingECSSlaves(label);
        Assert.assertEquals(0,nodes.size());
    }

}