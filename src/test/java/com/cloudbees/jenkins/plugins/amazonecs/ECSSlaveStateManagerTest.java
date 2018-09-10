package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.amazonaws.services.ecs.model.TaskDefinition;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class ECSSlaveStateManagerTest {

    private ECSService ecsService;
    private ECSClient mockECSClient;
    private ECSCloud testCloud;
    private ECSComputer mockComputer;
    private ECSSlave mockSlave;
    private ECSSlaveStateManager stateManager;

    ECSTaskTemplate testTemplate;
    TaskListener mockTaskListener;
    final String taskDefinitionArn="DummyTaskDefinitionArn";
    final TaskDefinition definition=new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn).withContainerDefinitions(new ContainerDefinition().withName("ECSCloud-maven-java").withImage("cloudbees/maven-java").withCpu(2048).withMemory(2048).withEssential(true).withPrivileged(false));
    final String taskArn="DummyTaskArn";
    String nodeName;
    ECSSlaveStateManager.State testState;

    @Before
    public void setup() throws IOException, InterruptedException {
        ecsService=new ECSService("TestCredentials","us-east-1");
        testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"cloudbees/maven-java","FARGATE",null,2048,0,2048,"subnet","secGroup",true,false,null,null,null,null,null,null);
        nodeName=ECSSlaveImpl.getSlaveName(testTemplate);
        testCloud= Mockito.spy(new ECSCloud("ECS Cloud", Arrays.asList(testTemplate),"ecsUser","ecsClusterArn","us-east-1","http://jenkinsUrl",30));
        testCloud.setTunnel("jenkinsUrl:50000");
        Mockito.when(testCloud.getTemplate(org.mockito.Matchers.eq(null))).thenReturn(testTemplate);
        mockECSClient=mock(ECSClient.class);
        mockComputer =mock(ECSComputer.class);
        ecsService.init(mockECSClient);
        Mockito.when(testCloud.getEcsService()).thenReturn(ecsService);
        mockSlave =createSlave();
        mockTaskListener=mock(TaskListener.class);
        Mockito.when(mockComputer.getNode()).thenReturn(mockSlave);
        Mockito.when(mockComputer.getName()).thenReturn("TestComputer");
        Mockito.when(mockComputer.getJnlpMac()).thenReturn("TestSecret");
        Mockito.when(mockTaskListener.getLogger()).thenReturn(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {

            }
        }));

    }

    private ECSSlave createSlave() throws IOException, InterruptedException {
        ECSSlave slave = mock(ECSSlave.class);
        stateManager =new ECSSlaveStateManager(slave,nodeName,testTemplate);
        Mockito.when(slave.getStateManager()).thenReturn(stateManager);
        Mockito.when(slave.getNodeName()).thenReturn(nodeName);
        Mockito.when(slave.getECSComputer()).thenReturn(mockComputer);
        Mockito.when(slave.getCloud()).thenReturn(testCloud);
        doAnswer((Answer) invocation -> {
            stateManager._terminate(mockTaskListener);
            return null;
        }).when(slave).terminate();
        return slave;
    }

    @Test
    public void testDockerRunCommandIsCorrect()
    {
        Assert.assertArrayEquals(new String[] {"-url","http://jenkinsUrl","-tunnel","jenkinsUrl:50000","TestSecret","TestComputer"},stateManager.getDockerRunCommand().toArray());
    }

    @Test
    public void whenStateIsSetToRunningThenComputerIsSetToAcceptingTasks()
    {
        stateManager.setTaskState(ECSSlaveStateManager.State.Running);
        Mockito.verify(mockComputer,Mockito.times(1)).setAcceptingECSTasks(true);
    }

    @Test
    public void whenStateIsSetToStoppingThenComputerIsSetNotToAcceptingTasks() throws IOException {
        VirtualChannel channel=mock(VirtualChannel.class);
        Mockito.when(mockSlave.getChannel()).thenReturn(channel);
        stateManager.setTaskArn(taskArn);
        stateManager.setTaskState(ECSSlaveStateManager.State.Stopping);

        Mockito.verify(mockComputer,Mockito.times(1)).setAcceptingECSTasks(false);
        Mockito.verify(channel,Mockito.times(1)).close();
        Mockito.verify(mockECSClient,Mockito.times(1)).stopTask(new StopTaskRequest().withCluster("ecsClusterArn").withTask(taskArn));

    }
}
