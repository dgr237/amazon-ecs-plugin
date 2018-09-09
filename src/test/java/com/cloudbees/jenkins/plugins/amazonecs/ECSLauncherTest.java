package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;
import hudson.AbortException;
import hudson.model.TaskListener;
import org.junit.Assert;
import org.junit.Test;
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
import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveStateManager.State;

public class ECSLauncherTest {



    @Test
    public void testWhenECSSlaveIsCreatedSuccessfully() throws AbortException {
        new ECSSlaveCreatedSuccessfullyScenario().runTest();
    }

    @Test
    public void testThatECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSupplied() throws AbortException {
        new ECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSuppliedScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenECSTaskIsTerminated() throws AbortException
    {
        new ECSSlaveIsStoppedWhenECSTaskIsTerminatedScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenTemplateCreationFails() throws AbortException
    {
        new ECSSlaveIsStoppedWhenTemplateCreationFailsScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARN() throws AbortException
    {
        new ECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARNScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenRunTaskThrowsAnException() throws AbortException
    {
        new ECSSlaveIsStoppedWhenRunTaskCallThrowsExceptionScenario().runTest();
    }


    class ECSLauncherTestBase {

        ECSService ecsService;
        ECSClient mockECSClient;
        ECSCloud testCloud;
        ECSTaskTemplate testTemplate;
        ECSSlave mockSlave;
        ECSSlaveStateManager mockInnerSlave;
        ECSComputer mockComputer;
        TaskListener mockTaskListener;
        final String taskDefinitionArn="DummyTaskDefinitionArn";
        final TaskDefinition definition=new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn).withContainerDefinitions(new ContainerDefinition().withName("ECSCloud-maven-java").withImage("cloudbees/maven-java").withCpu(2048).withMemory(2048).withEssential(true).withPrivileged(false));
        final String taskArn="DummyTaskArn";
        String nodeName;
        ECSSlaveStateManager.State testState;

        void runCommonSetup()
        {
            ecsService=new ECSService("TestCredentials","us-east-1");
            testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"cloudbees/maven-java","FARGATE",null,2048,0,2048,"subnet","secGroup",true,false,null,null,null,null,null,null);
            nodeName=ECSSlaveImpl.getSlaveName(testTemplate);
            testCloud= Mockito.spy(new ECSCloud("ECS Cloud", Arrays.asList(testTemplate),"ecsUser","ecsClusterArn","us-east-1","http://jenkinsUrl",30));
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
            testState= State.None;

        }

        private ECSSlave createSlave() {
            ECSSlave slave = mock(ECSSlave.class);
            mockInnerSlave=mock(ECSSlaveStateManager.class);

            Mockito.when(slave.getInnerSlave()).thenReturn(mockInnerSlave);
            Mockito.when(slave.getNodeName()).thenReturn(nodeName);
            Mockito.when(slave.getECSComputer()).thenReturn(mockComputer);
            Mockito.when(slave.getCloud()).thenReturn(testCloud);
            Mockito.when(mockInnerSlave.getDockerRunCommand()).thenReturn(Arrays.asList("MyRunCommand"));
            Mockito.when(mockInnerSlave.getTemplate()).thenReturn(testTemplate);
            Mockito.when(mockInnerSlave.getTaskState()).thenAnswer(new Answer<State>() {
                public State answer(InvocationOnMock invocation) {
                    return testState;
                }
            });
            doAnswer((Answer) innvocation -> {
                State state = innvocation.getArgumentAt(0, State.class);
                testState = state;
                return null;
            }).when(mockInnerSlave).setTaskState(any(State.class));
            doAnswer((Answer) innvocation -> {
                String taskArnToCkeck = innvocation.getArgumentAt(0, String.class);
                Assert.assertEquals(taskArn, taskArnToCkeck);
                return null;
            }).when(mockInnerSlave).setTaskArn(any(String.class));
            return slave;
        }

        void runTestBase() {
            ECSLauncher launcher = new ECSLauncher();
            launcher.launch(mockComputer, mockTaskListener);
        }

    }

    class ECSSlaveCreatedSuccessfullyScenario extends ECSLauncherTestBase {

        private void setupScenario() throws AbortException {
            Mockito.when(mockComputer.isOnline()).thenAnswer(new Answer<Boolean>() {
                int isOnlineCallCount=0;
                public Boolean answer(InvocationOnMock invocation) {
                    if(++isOnlineCallCount<2)
                        return false;
                    else
                        return true;
                }
            });
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult());
            Mockito.when(mockECSClient.registerTaskDefinition(any())).thenReturn(new RegisterTaskDefinitionResult().withTaskDefinition(definition));
            Mockito.when(mockECSClient.runTask(any())).thenReturn(new RunTaskResult().withTasks(new Task().withTaskArn(taskArn)));
            Mockito.when(mockECSClient.describeTasks(any())).thenAnswer(new Answer<DescribeTasksResult>() {
                int getTaskStatusCallCount=0;
                public DescribeTasksResult answer(InvocationOnMock invocation) {
                    if(++getTaskStatusCallCount<2)
                        return new DescribeTasksResult().withTasks(new Task().withLastStatus("PENDING"));
                    else
                        return new DescribeTasksResult().withTasks(new Task().withLastStatus("RUNNING"));
                }
            });
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(State.Running, mockInnerSlave.getTaskState());
        }
    }

    class ECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSuppliedScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);

            Mockito.when(mockComputer.isOnline()).thenReturn(true);
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(definition));
            Mockito.verify(mockECSClient,Mockito.never()).registerTaskDefinition(any());
            Mockito.when(mockECSClient.runTask(any())).thenReturn(new RunTaskResult().withTasks(new Task().withTaskArn(taskArn)));
            Mockito.when(mockECSClient.describeTasks(any())).thenReturn(new DescribeTasksResult().withTasks(new Task().withLastStatus("RUNNING")));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(State.Running, mockInnerSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenECSTaskIsTerminatedScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(definition));

            Mockito.when(mockECSClient.runTask(any())).thenReturn(new RunTaskResult().withTasks(new Task().withTaskArn(taskArn)));
            Mockito.when(mockECSClient.describeTasks(any())).thenReturn(new DescribeTasksResult().withTasks(new Task().withLastStatus("DEPROVISIONING")));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(State.Stopping, mockInnerSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenRunTaskCallThrowsExceptionScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(definition));
            Mockito.when(mockECSClient.runTask(any())).thenThrow(new AccessDeniedException("User is not permissioned"));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(State.Stopping, mockInnerSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenTemplateCreationFailsScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult());
            Mockito.when(mockECSClient.registerTaskDefinition(any())).thenThrow(new InvalidParameterException("Test Error"));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(State.Stopping, mockInnerSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARNScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenThrow(new ClientException("ARN not found"));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(State.Stopping, mockInnerSlave.getTaskState());
        }
    }

}