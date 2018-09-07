package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.TaskDefinition;
import hudson.AbortException;
import hudson.model.TaskListener;
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
import java.util.Collection;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public abstract class ECSLauncherTestSetup {

    ECSService mockService;
    ECSCloud testCloud;
    ECSTaskTemplate testTemplate;
    ECSSlave mockSlave;
    ECSComputer mockComputer;
    TaskListener mockTaskListener;
    static final String taskDefinitionArn="DummyTaskDefinitionArn";
    static final TaskDefinition definition=new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn);
    static final String taskArn="DummyTaskArn";
    String nodeName;
    ECSSlaveImpl.State testState;
    @Before
    public void startup() throws AbortException
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
        Mockito.when(mockTaskListener.getLogger()).thenReturn(new PrintStream(new NullOutputStream()));
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
            this.testState = state;
            return null;
        }).when(slave).setTaskState(any(ECSSlaveImpl.State.class));
        doAnswer((Answer) innvocation -> {
            String taskArn = innvocation.getArgumentAt(0, String.class);
            Assert.assertEquals(this.taskArn, taskArn);
            return null;
        }).when(slave).setTaskArn(any(String.class));
        return slave;
    }

    void runTestBase() {
        ECSLauncher launcher = new ECSLauncher();
        launcher.launch(mockComputer, mockTaskListener);

    }

    public static class ECSSlaveCreatedSuccessfully extends ECSLauncherTestSetup {
        public void setupScenario() throws AbortException {
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

        @Test
        public void runTest() throws AbortException
        {
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Running, mockSlave.getTaskState());
        }
    }

    public static class ECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSupplied extends ECSLauncherTestSetup {
        public void setupScenario() throws AbortException {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);

            Mockito.when(mockSlave.isOnline()).thenReturn(true);
            Mockito.when(mockService.findTaskDefinition(taskDefinitionArn)).thenReturn(definition);
            Mockito.verify(mockService,Mockito.never()).registerTemplate(testCloud,testTemplate);
            Mockito.when(mockService.runEcsTask(mockSlave,testTemplate,testCloud.getCluster(),mockSlave.getDockerRunCommand(),definition)).thenReturn(taskArn);
            Mockito.when(mockService.getTaskStatus(testCloud,taskArn)).thenReturn("RUNNING");
        }

        @Test
        public void runTest() throws AbortException
        {
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Running, mockSlave.getTaskState());
        }
    }

    public static class ECSSlaveIsStoppedWhenTaskIsStopped extends ECSLauncherTestSetup {
        public void setupScenario() throws AbortException {
            Mockito.when(mockService.registerTemplate(testCloud,testTemplate)).thenReturn(definition);
            Mockito.when(mockService.runEcsTask(mockSlave,testTemplate,testCloud.getCluster(),mockSlave.getDockerRunCommand(),definition)).thenReturn(taskArn);

            Mockito.when(mockService.getTaskStatus(testCloud,taskArn)).thenReturn("DEPROVISIONING");
        }

        @Test
        public void runTest() throws AbortException
        {
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Stopping, mockSlave.getTaskState());
        }
    }

    public static class ECSSlaveIsStoppedWhenTemplateCreationFails extends ECSLauncherTestSetup {
        public void setupScenario() throws AbortException {
            Mockito.when(mockService.registerTemplate(testCloud,testTemplate)).thenThrow(new InvalidParameterException("Test Error"));
        }

        @Test
        public void runTest() throws AbortException
        {
            setupScenario();
            runTestBase();
            Assert.assertEquals(ECSSlaveImpl.State.Stopping, mockSlave.getTaskState());
        }
    }

    public static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
        }
    }

//    public static class MockECSSlave implements ECSSlave {
//        private ECSCloud cloud;
//        private ECSComputer computer;
//        private ECSTaskTemplate template;
//        private ECSSlaveImpl.State state;
//        private String taskArn;
//        private String nodeName;
//        private int setToOnlineAfterCallsToIsOnline;
//        private int callsToIsOnline;
//
//        public MockECSSlave(ECSCloud cloud, ECSComputer computer, ECSTaskTemplate template)
//        {
//            this.cloud=cloud;
//            this.computer=computer;
//            this.template=template;
//            this.state= ECSSlaveImpl.State.None;
//            this.nodeName=ECSSlaveImpl.getSlaveName(template);
//            this.setToOnlineAfterCallsToIsOnline=setToOnlineAfterCallsToIsOnline;
//            callsToIsOnline=1;
//        }
//        @Override
//        public ECSCloud getCloud() {
//            return cloud;
//        }
//
//        @Override
//        public ECSSlaveImpl.State getTaskState() {
//            return state;
//        }
//
//        @Override
//        public void setTaskState(ECSSlaveImpl.State state) {
//            this.state=state;
//        }
//
//        @Override
//        public ECSTaskTemplate getTemplate() {
//            return template;
//        }
//
//        @Override
//        public ECSComputer getECSComputer() {
//            return computer;
//        }
//
//        @Override
//        public void save() throws IOException {
//
//        }
//
//        @Override
//        public void setTaskArn(String taskArn) {
//            this.taskArn=taskArn;
//        }
//
//        @Override
//        public String getNodeName() {
//            return nodeName;
//        }
//
//        @Override
//        public Collection<String> getDockerRunCommand() {
//            return ;
//        }
//
//        @Override
//        public Boolean isOnline() {
//            return false;
//        }
//
//
//    }
}