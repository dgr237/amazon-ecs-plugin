package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ListTasksResult;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {Label.class,JenkinsWrapper.class})
public class ECSCloudTest {
    //private ECSInitializingSlavesResolver initializingSlavesResolver;
    //private ECSTaskTemplate testTemplate;
    private ECSCloud testCloud;
    //private ECSService mockService;
    //private ECSClient mockClient;
    private Label label;
    //private Jenkins jenkins;

    @Before
    public void setup()
    {
        label=mock(Label.class);
        Jenkins jenkins=mock(Jenkins.class);
        ECSClient mockClient=mock(ECSClient.class);
        ECSService mockService=new ECSService("Credentials","us-east-1");
        mockService.init(mockClient);
        Mockito.when(jenkins.getLabel(any())).thenReturn(label);
        PowerMockito.mockStatic(JenkinsWrapper.class);
        Mockito.when(JenkinsWrapper.getInstance()).thenReturn(jenkins);
        Mockito.when(JenkinsWrapper.getECSService(any(String.class),(any(String.class)))).thenReturn(mockService);

        ECSInitializingSlavesResolver initializingSlavesResolver=Mockito.spy(new ECSInitializingSlavesResolver());
        doReturn(new Object[] {}).when(initializingSlavesResolver).getNodes(label);
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
        testCloud= Mockito.spy(new ECSCloud("ECS Cloud","ecsClusterArn","us-east-1").withCredentialsId("ecsUserId").withJenkinsUrl("http://jenkinsUrl:8080").withMaxSlaves(5).withSlaveTimeoutInSeconds(60).withTemplates(testTemplate).withTunnel("myJenkins:50000"));
        Mockito.when(mockClient.listTasks(any())).thenReturn(new ListTasksResult());
    }

    @After
    public void shutdown()
    {
        //TODO How to shutdown
    }

    @Test
    public void canProvisionLabelSupportedByECSShouldReturnTrue() {
        PowerMockito.mockStatic(Label.class);
        PowerMockito.when(Label.parse(any())).thenReturn(new TreeSet<>());
        Mockito.when(label.matches(any(Collection.class))).thenReturn(true);
        Label label=JenkinsWrapper.getInstance().getLabel("maven-java");
        Assert.assertTrue(testCloud.canProvision(label));
    }

    @Test
    public void canProvisionLabelNotSupportedByECSShouldReturnFalse() {
        PowerMockito.mockStatic(Label.class);
        PowerMockito.when(Label.parse(any())).thenReturn(new TreeSet<>());
        Mockito.when(label.matches(any(Collection.class))).thenReturn(false);
        Label label=JenkinsWrapper.getInstance().getLabel("Unknown Label");
        Assert.assertFalse(testCloud.canProvision(label));
    }

    @Test
    public void Provision() {
        PowerMockito.mockStatic(Label.class);
        PowerMockito.when(Label.parse(any())).thenReturn(new TreeSet<>());
        Mockito.when(label.matches(any(Collection.class))).thenReturn(true);
        Label label=JenkinsWrapper.getInstance().getLabel("maven-java");
        Collection<NodeProvisioner.PlannedNode> result = testCloud.provision(label, 1);
        assertEquals(1, result.size());
        List<NodeProvisioner.PlannedNode> provisioners=new ArrayList<>(result);
        assertEquals("ECS Slave maven-java",provisioners.get(0).displayName);
        assertEquals(1,provisioners.get(0).numExecutors);
    }
}
