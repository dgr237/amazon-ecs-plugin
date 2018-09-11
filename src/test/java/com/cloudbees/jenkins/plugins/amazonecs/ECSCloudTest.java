package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ListTasksResult;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ECSCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    ECSInitializingSlavesResolver initializingSlavesResolver;
    ECSTaskTemplate testTemplate;
    ECSCloud testCloud;
    ECSService mockService;
    ECSClient mockClient;
    Label label;
    @Before
    public void setup()
    {
        label=mock(Label.class);
        mockClient=mock(ECSClient.class);
        mockService=new ECSService("Credentials","us-east-1");
        mockService.init(mockClient);
        initializingSlavesResolver=Mockito.spy(new ECSInitializingSlavesResolver());
        doReturn(new Object[] {}).when(initializingSlavesResolver).getNodes(label);
        testTemplate=new ECSTaskTemplate("maven-java","cloudbees/maven-java","FARGATE",null,2048,0,2048,false,null,null,null,null,null).withLabel("maven-java").withSecurityGroups("secGroup").withSubnets("subnets").withPrivileged(true).withSingleRunTask(true).withIdleTerminationMinutes(1);
        testCloud= Mockito.spy(new ECSCloud("ECS Cloud","ecsClusterArn","us-east-1").withCredentialsId("ecsUserId").withJenkinsUrl("http://jenkinsUrl:8080").withMaxSlaves(5).withSlaveTimeoutInSeconds(60).withTemplates(testTemplate).withTunnel("myJenkins:50000"));        Mockito.when(testCloud.getEcsService()).thenReturn(mockService);
        Mockito.when(mockClient.listTasks(any())).thenReturn(new ListTasksResult());
    }

    @After
    public void shutdown()
    {
        //TODO How to shutdown
    }

    @Test
    public void canProvisionLabelSupportedByECSShouldReturnTrue() {
        Label label=Jenkins.get().getLabel("maven-java");
        Assert.assertTrue(testCloud.canProvision(label));
    }

    @Test
    public void canProvisionLabelNotSupportedByECSShouldReturnFalse() {
        Label label=Jenkins.get().getLabel("Unknown Label");
        Assert.assertFalse(testCloud.canProvision(label));
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