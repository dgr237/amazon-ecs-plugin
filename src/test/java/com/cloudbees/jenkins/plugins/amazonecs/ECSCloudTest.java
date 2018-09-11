package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ListTasksResult;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.junit.*;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Label.class)
public class ECSCloudTest {
    private ECSInitializingSlavesResolver initializingSlavesResolver;
    private ECSTaskTemplate testTemplate;
    private ECSCloud testCloud;
    private ECSService mockService;
    private ECSClient mockClient;
    private Label label;
    private JenkinsWrapper wrapper;
    private Jenkins jenkins;

    @Before
    public void setup()
    {
        label=mock(Label.class);
        jenkins=mock(Jenkins.class);
        Mockito.when(jenkins.getLabel(any())).thenReturn(label);
        wrapper=mock(JenkinsWrapper.class);
        Mockito.when(wrapper.getJenkinsInstance()).thenReturn(jenkins);
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
        PowerMockito.mockStatic(Label.class);
        PowerMockito.when(Label.parse(any())).thenReturn(new TreeSet<>());
        Mockito.when(label.matches(any(Collection.class))).thenReturn(true);
        Label label=wrapper.getJenkinsInstance().getLabel("maven-java");
        Assert.assertTrue(testCloud.canProvision(label));
    }

    @Test
    public void canProvisionLabelNotSupportedByECSShouldReturnFalse() {
        PowerMockito.mockStatic(Label.class);
        PowerMockito.when(Label.parse(any())).thenReturn(new TreeSet<>());
        Mockito.when(label.matches(any(Collection.class))).thenReturn(false);
        Label label=wrapper.getJenkinsInstance().getLabel("Unknown Label");
        Assert.assertFalse(testCloud.canProvision(label));
    }

    @Test
    public void Provision() {
        PowerMockito.mockStatic(Label.class);
        PowerMockito.when(Label.parse(any())).thenReturn(new TreeSet<>());
        Mockito.when(label.matches(any(Collection.class))).thenReturn(true);
        Label label=wrapper.getJenkinsInstance().getLabel("maven-java");
        Collection<NodeProvisioner.PlannedNode> result = testCloud.provision(label, 1);
        assertEquals(1, result.size());
        List<NodeProvisioner.PlannedNode> provisioners=new ArrayList<>(result);
        assertEquals("ECS Slave maven-java",provisioners.get(0).displayName);
        assertEquals(1,provisioners.get(0).numExecutors);
    }
}