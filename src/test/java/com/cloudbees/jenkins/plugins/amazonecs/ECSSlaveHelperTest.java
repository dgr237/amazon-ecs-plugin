package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.StopTaskRequest;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.*;
@RunWith(PowerMockRunner.class)
@PrepareForTest(JenkinsWrapper.class)
public class ECSSlaveHelperTest {

    //private ECSService ecsService;
    private ECSClient mockECSClient;
    private ECSCloud testCloud;
    private ECSComputer mockComputer;
    private ECSSlave mockSlave;
    private ECSSlaveHelper helper;

    private ECSTaskTemplate testTemplate;
    //private TaskListener mockTaskListener;
    private final String taskDefinitionArn="DummyTaskDefinitionArn";
    //private final TaskDefinition definition=new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn).withContainerDefinitions(new ContainerDefinition().withName("ECSCloud-maven-java").withImage("cloudbees/maven-java").withCpu(2048).withMemory(2048).withEssential(true).withPrivileged(false));

    private String nodeName;

    @Before
    public void setup() throws IOException, InterruptedException {
        ECSService ecsService=new ECSService("TestCredentials","us-east-1");
        testTemplate=new ECSTaskTemplate()
                .withTemplateName("maven-java")
                .withImage("cloudbees/maven-java")
                .withLaunchType("FARGATE")
                .withMemory(2048)
                .withCpu(2048)
                .withAssignPublicIp(true)
                .withLabel("maven-java")
                .withSecurityGroups("secGroup")
                .withSubnets("subnets")
                .withPrivileged(true)
                .withSingleRunTask(true)
                .withIdleTerminationMinutes(1);
        nodeName=ECSSlaveHelper.getSlaveName(testTemplate);
        testCloud= Mockito.spy(new ECSCloud("ECS Cloud","ecsClusterArn","us-east-1").withCredentialsId("ecsUserId").withJenkinsUrl("http://jenkinsUrl:8080").withMaxSlaves(5).withSlaveTimeoutInSeconds(60).withTemplates(testTemplate).withTunnel("myJenkins:50000"));        testCloud.setTunnel("jenkinsUrl:50000");
        Mockito.when(testCloud.getTemplate(org.mockito.Matchers.eq(null))).thenReturn(testTemplate);
        mockECSClient=mock(ECSClient.class);
        mockComputer =mock(ECSComputer.class);
        ecsService.init(mockECSClient);
        PowerMockito.mockStatic(JenkinsWrapper.class);
        PowerMockito.when(JenkinsWrapper.getECSService(any(String.class),any(String.class))).thenReturn(ecsService);
        mockSlave =createSlave();
        TaskListener mockTaskListener=mock(TaskListener.class);
        Mockito.when(mockComputer.getECSNode()).thenReturn(mockSlave);
        Mockito.when(mockComputer.getName()).thenReturn("TestComputer");
        Mockito.when(mockComputer.getJnlpMac()).thenReturn("TestSecret");
        Mockito.when(mockTaskListener.getLogger()).thenReturn(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {

            }
        }));

    }

    private ECSSlave createSlave() throws IOException, InterruptedException {
        ECSSlave slave = mock(ECSSlave.class);
        helper =new ECSSlaveHelper(slave,nodeName,testTemplate);
        Mockito.when(slave.getHelper()).thenReturn(helper);
        Mockito.when(slave.getNodeName()).thenReturn(nodeName);
        Mockito.when(slave.getECSComputer()).thenReturn(mockComputer);
        Mockito.when(slave.getCloud()).thenReturn(testCloud);
        doAnswer(invocation -> {
            helper.terminate();
            return null;
        }).when(slave).terminate();
        return slave;
    }

    @Test
    public void testDockerRunCommandIsCorrect()
    {
        Assert.assertArrayEquals(new String[] {"-url","http://jenkinsUrl:8080","-tunnel","jenkinsUrl:50000","TestSecret","TestComputer"}, helper.getDockerRunCommand().toArray());
    }

    @Test
    public void whenStateIsSetToRunningThenComputerIsSetToAcceptingTasks()
    {
        helper.setTaskState(RUNNING);
        Mockito.verify(mockComputer,Mockito.times(1)).setAcceptingTasks(true);
    }

    @Test
    public void whenStateIsSetToStoppingThenComputerIsSetNotToAcceptingTasks() throws IOException {
        String taskArn="DummyTaskArn";
        VirtualChannel channel=mock(VirtualChannel.class);
        Mockito.when(mockSlave.getChannel()).thenReturn(channel);
        helper.setTaskArn(taskArn);
        helper.setTaskState(STOPPING);

        Mockito.verify(mockComputer,Mockito.times(1)).setAcceptingTasks(false);
        Mockito.verify(channel,Mockito.times(1)).close();
        Mockito.verify(mockECSClient,Mockito.times(1)).stopTask(new StopTaskRequest().withCluster("ecsClusterArn").withTask(taskArn));
    }

    @Test
    public void whenStateIsSetToInitializingThenComputerIsSetNotToAcceptingTasks() {
        helper.setTaskState(ECSSlaveHelper.State.INITIALIZING);

        Mockito.verify(mockComputer,Mockito.times(1)).setAcceptingTasks(false);
    }

}
