package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Label;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import java.util.Set;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.*;

public class ECSInitializingSlavesResolverTest {

    @Test
    public void ECSSlaveWhichIsNotRunningShouldBeInTheResult()
    {

        ECSSlave slave=mock(ECSSlave.class);
        Mockito.when(slave.getNodeName()).thenReturn("InitializingNode");
        ECSTaskTemplate testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"FARGATE")
                .withImage("cloudbees/maven-java")
                .withMemory(2048)
                .withCpu(2048)
                .withAssignPublicIp(true)
                .withSecurityGroups("secGroup")
                .withSubnets("subnets")
                .withPrivileged(true)
                .withSingleRunTask(true)
                .withIdleTerminationMinutes(1);
        ECSSlaveHelper helper=new ECSSlaveHelper(slave,"maven-java",testTemplate);
        helper.setTaskState(INITIALIZING);
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
        ECSTaskTemplate testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"FARGATE")
                .withImage("cloudbees/maven-java")
                .withMemory(2048)
                .withCpu(2048)
                .withAssignPublicIp(true)
                .withSecurityGroups("secGroup")
                .withSubnets("subnets")
                .withPrivileged(true)
                .withSingleRunTask(true)
                .withIdleTerminationMinutes(1);
        ECSSlaveHelper helper=new ECSSlaveHelper(slave,"maven-java",testTemplate);
        helper.setTaskState(RUNNING);
        Mockito.when(slave.getHelper()).thenReturn(helper);
        Label label=mock(Label.class);

        ECSInitializingSlavesResolver resolver=Mockito.spy(new ECSInitializingSlavesResolver());
        doReturn(new Object[] {slave}).when(resolver).getNodes(label);
        Set<String> nodes= resolver.getInitializingECSSlaves(label);
        Assert.assertEquals(0,nodes.size());
    }

}