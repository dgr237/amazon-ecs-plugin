package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.AccessDeniedException;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.TaskDefinition;
import hudson.AbortException;
import hudson.model.TaskListener;
import org.junit.Assert;
import org.junit.Before;
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

        ECSService mockService;
        ECSCloud testCloud;
        ECSTaskTemplate testTemplate;
        ECSSlave mockSlave;
        ECSComputer mockComputer;
        TaskListener mockTaskListener;
        final String taskDefinitionArn="DummyTaskDefinitionArn";
        final TaskDefinition definition=new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn);
        final String taskArn="DummyTaskArn";
        String nodeName;
        ECSSlaveImpl.State testState;

        void runCommonSetup()
        {
            testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"cloudbees/maven-java","FARGATE",null,2048,0,2048,"subnet","secGroup",true,false,null,null,null,null,null,null);
            nodeName=ECSSlaveImpl.getSlaveName(testTemplate);
            testCloud= Mockito.spy(new ECSCloud("ECS Cloud", Arrays.asList(testTemplate),"ecsUser","ecsClusterArn","us-east-1","http://jenkinsUrl",30));
            Mockito.when(testCloud.getTemplate(org.mockito.Matchers.eq(null))).thenReturn(testTemplate);
            mockService=mock(ECSService.class);
            mockComputer =mock(ECSComputer.class);
            Mockito.when(testCloud.getEcsService()).thenReturn(mockService);
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
            testState= ECSSlaveImpl.State.None;

        }

        private ECSSlave createSlave() {
            ECSSlave slave = mock(ECSSlave.class);
            Mockito.when(slave.getNodeName()).thenReturn(nodeName);
            Mockito.when(slave.getECSComputer()).thenReturn(mockComputer);
            Mockito.when(slave.getCloud()).thenReturn(testCloud);
            Mockito.when(slave.getDockerRunCommand()).thenReturn(Arrays.asList("MyRunCommand"));
            Mockito.when(slave.getTemplate()).thenReturn(testTemplate);
            Mockito.when(slave.getTaskState()).thenAnswer(new Answer<ECSSlaveImpl.State>() {
                public ECSSlaveImpl.State answer(InvocationOnMock invocation) {
                    return testState;
                }
            });
            doAnswer((Answer) innvocation -> {
                ECSSlaveImpl.State state = innvocation.getArgumentAt(0, ECSSlaveImpl.State.class);
                testState = state;
                return null;
            }).when(slave).setTaskState(any(ECSSlaveImpl.State.class));
            doAnswer((Answer) innvocation -> {
                String taskArnToCkeck = innvocation.getArgumentAt(0, String.class);
                Assert.assertEquals(taskArn, taskArnToCkeck);
                return null;
            }).when(slave).setTaskArn(any(String.class));
            return slave;
        }

        void runTestBase() {
            ECSLauncher launcher = new ECSLauncher();
            launcher.launch(mockComputer, mockTaskListener);
        }

    }

    class ECSSlaveCreatedSuccessfullyScenario extends ECSLauncherTestBase {

        private void setupScenario() throws AbortException {
            Mockito.when(mockSlave.isOnline()).thenAnswer(new Answer<Boolean>() {
                int isOnlineCallCount=0;
                public Boolean answer(InvocationOnMock invocation) {
                    if(++isOnlineCallCount<2)
                        return false;
                    else
                        return true;
                }
            });
            Mockito.when(mockService.registerTemplate(testCloud,testTemplate)).thenReturn(definition);
            Mockito.when(mockService.runEcsTask(mockSlave,testTemplate,testCloud.getCluster(),mockSlave.getDockerRunCommand(),definition)).thenReturn(taskArn);

            Mockito.when(mockService.getTaskStatus(testCloud,taskArn)).thenAnswer(new Answer<String>() {
                int getTaskStatusCallCount=0;
                public String answer(InvocationOnMock invocation) {
                    if(++getTaskStatusCallCount<2)
                        return "PENDING";
                    else
                        return "RUNNING";
                }
            });
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Running, mockSlave.getTaskState());
        }
    }

    class ECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSuppliedScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);

            Mockito.when(mockSlave.isOnline()).thenReturn(true);
            Mockito.when(mockService.findTaskDefinition(taskDefinitionArn)).thenReturn(definition);
            Mockito.verify(mockService,Mockito.never()).registerTemplate(testCloud,testTemplate);
            Mockito.when(mockService.runEcsTask(mockSlave,testTemplate,testCloud.getCluster(),mockSlave.getDockerRunCommand(),definition)).thenReturn(taskArn);
            Mockito.when(mockService.getTaskStatus(testCloud,taskArn)).thenReturn("RUNNING");
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Running, mockSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenECSTaskIsTerminatedScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            Mockito.when(mockService.registerTemplate(testCloud,testTemplate)).thenReturn(definition);
            Mockito.when(mockService.runEcsTask(mockSlave,testTemplate,testCloud.getCluster(),mockSlave.getDockerRunCommand(),definition)).thenReturn(taskArn);

            Mockito.when(mockService.getTaskStatus(testCloud,taskArn)).thenReturn("DEPROVISIONING");
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Stopping, mockSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenRunTaskCallThrowsExceptionScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            Mockito.when(mockService.registerTemplate(testCloud,testTemplate)).thenReturn(definition);
            Mockito.when(mockService.runEcsTask(mockSlave,testTemplate,testCloud.getCluster(),mockSlave.getDockerRunCommand(),definition)).thenThrow(new AccessDeniedException("User is not permissioned"));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Stopping, mockSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenTemplateCreationFailsScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            Mockito.when(mockService.registerTemplate(testCloud,testTemplate)).thenThrow(new InvalidParameterException("Test Error"));
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Stopping, mockSlave.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARNScenario extends ECSLauncherTestBase {
        private void setupScenario() throws AbortException {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);
            Mockito.when(mockService.findTaskDefinition(taskDefinitionArn)).thenReturn(null);
        }

        void runTest() throws AbortException
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Stopping, mockSlave.getTaskState());
        }
    }

}