package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;
import hudson.model.TaskListener;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State;
import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JenkinsWrapper.class)
public class ECSLauncherTest {



    @Test
    public void testWhenECSSlaveIsCreatedSuccessfully() {
        new ECSSlaveCreatedSuccessfullyScenario().runTest();
    }

    @Test
    public void testThatECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSupplied() {
        new ECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSuppliedScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenECSTaskIsTerminated()
    {
        new ECSSlaveIsStoppedWhenECSTaskIsTerminatedScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenTemplateCreationFails()
    {
        new ECSSlaveIsStoppedWhenTemplateCreationFailsScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARN()
    {
        new ECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARNScenario().runTest();
    }

    @Test
    public void testThatECSSlaveIsStoppedWhenRunTaskThrowsAnException()
    {
        new ECSSlaveIsStoppedWhenRunTaskCallThrowsExceptionScenario().runTest();
    }


    class ECSLauncherTestBase {

        ECSService ecsService;
        ECSClient mockECSClient;
        ECSCloud testCloud;
        ECSTaskTemplate testTemplate;
        ECSSlave mockSlave;
        ECSSlaveHelper helper;
        ECSComputer mockComputer;
        TaskListener mockTaskListener;
        final String taskDefinitionArn="DummyTaskDefinitionArn";
        final TaskDefinition definition=new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn).withContainerDefinitions(new ContainerDefinition().withName("ECSCloud-maven-java").withImage("cloudbees/maven-java").withCpu(2048).withMemory(2048).withEssential(true).withPrivileged(false));
        final String taskArn="DummyTaskArn";
        String nodeName;
        ECSSlaveHelper.State testState;

        void runCommonSetup()
        {
            ecsService=new ECSService("TestCredentials","us-east-1");
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
                    .withPrivileged(false)
                    .withSingleRunTask(true)
                    .withIdleTerminationMinutes(1);
            nodeName=ECSSlaveHelper.getSlaveName(testTemplate);
            testCloud= Mockito.spy(new ECSCloud("ECS Cloud","ecsClusterArn","us-east-1").withCredentialsId("ecsUserId").withJenkinsUrl("http://jenkinsUrl:8080").withMaxSlaves(5).withSlaveTimeoutInSeconds(60).withTemplates(testTemplate).withTunnel("myJenkins:50000"));
            Mockito.when(testCloud.getTemplate(org.mockito.Matchers.eq(null))).thenReturn(testTemplate);
            PowerMockito.mockStatic(JenkinsWrapper.class);
            PowerMockito.when(JenkinsWrapper.getECSService(any(String.class),any(String.class))).thenReturn(ecsService);
            mockECSClient=mock(ECSClient.class);
            mockComputer =mock(ECSComputer.class);
            ecsService.init(mockECSClient);
            mockSlave =createSlave();
            mockTaskListener=mock(TaskListener.class);
            Mockito.when(mockComputer.getECSNode()).thenReturn(mockSlave);
            Mockito.when(mockComputer.getName()).thenReturn("TestComputer");
            Mockito.when(mockComputer.getJnlpMac()).thenReturn("TestSecret");
            Mockito.when(mockTaskListener.getLogger()).thenReturn(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {

                }
            }));
            testState= NONE;

        }

        private ECSSlave createSlave() {
            ECSSlave slave = mock(ECSSlave.class);
            helper =mock(ECSSlaveHelper.class);

            Mockito.when(slave.getHelper()).thenReturn(helper);
            Mockito.when(slave.getNodeName()).thenReturn(nodeName);
            Mockito.when(slave.getECSComputer()).thenReturn(mockComputer);
            Mockito.when(slave.getCloud()).thenReturn(testCloud);
            Mockito.when(helper.getDockerRunCommand()).thenReturn(Arrays.asList("MyRunCommand"));
            Mockito.when(helper.getTemplate()).thenReturn(testTemplate);
            Mockito.when(helper.getTaskState()).thenAnswer((Answer<State>) invocation -> testState);
            doAnswer(invocation -> {
                testState = invocation.getArgumentAt(0, State.class);
                return null;
            }).when(helper).setTaskState(any(State.class));
            doAnswer(invocation -> {
                String taskArnToCheck = invocation.getArgumentAt(0, String.class);
                Assert.assertEquals(taskArn, taskArnToCheck);
                return null;
            }).when(helper).setTaskArn(any(String.class));
            return slave;
        }

        void runTestBase() {
            ECSLauncher launcher = new ECSLauncher(false);
            launcher.launch(mockComputer, mockTaskListener);
        }

    }

    class ECSSlaveCreatedSuccessfullyScenario extends ECSLauncherTestBase {

        private void setupScenario() {
            Mockito.when(mockComputer.isOnline()).thenAnswer(new Answer<Boolean>() {
                int isOnlineCallCount=0;
                public Boolean answer(InvocationOnMock invocation) {
                    return ++isOnlineCallCount >= 2;
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

        void runTest()
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(RUNNING, helper.getTaskState());
        }
    }

    class ECSSlaveCreatedSuccessfullyIfValidTaskDefinitionArnSuppliedScenario extends ECSLauncherTestBase {
        private void setupScenario()  {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);

            Mockito.when(mockComputer.isOnline()).thenReturn(true);
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(definition));
            Mockito.verify(mockECSClient,Mockito.never()).registerTaskDefinition(any());
            Mockito.when(mockECSClient.runTask(any())).thenReturn(new RunTaskResult().withTasks(new Task().withTaskArn(taskArn)));
            Mockito.when(mockECSClient.describeTasks(any())).thenReturn(new DescribeTasksResult().withTasks(new Task().withLastStatus("RUNNING")));
        }

        void runTest()
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(RUNNING, helper.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenECSTaskIsTerminatedScenario extends ECSLauncherTestBase {
        private void setupScenario() {
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(definition));

            Mockito.when(mockECSClient.runTask(any())).thenReturn(new RunTaskResult().withTasks(new Task().withTaskArn(taskArn)));
            Mockito.when(mockECSClient.describeTasks(any())).thenReturn(new DescribeTasksResult().withTasks(new Task().withLastStatus("DEPROVISIONING")));
        }

        void runTest()
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(STOPPING, helper.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenRunTaskCallThrowsExceptionScenario extends ECSLauncherTestBase {
        private void setupScenario()  {
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(definition));
            Mockito.when(mockECSClient.runTask(any())).thenThrow(new AccessDeniedException("User is not permissioned"));
        }

        void runTest()
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(STOPPING, helper.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenTemplateCreationFailsScenario extends ECSLauncherTestBase {
        private void setupScenario() {
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenReturn(new DescribeTaskDefinitionResult());
            Mockito.when(mockECSClient.registerTaskDefinition(any())).thenThrow(new InvalidParameterException("Test Error"));
        }

        void runTest()
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(STOPPING, helper.getTaskState());
        }
    }

    class ECSSlaveIsStoppedWhenTemplateNotFoundForTaskDefinitionARNScenario extends ECSLauncherTestBase {
        private void setupScenario() {
            testTemplate.setTaskDefinitionOverride(taskDefinitionArn);
            Mockito.when(mockECSClient.describeTaskDefinition(any())).thenThrow(new ClientException("ARN not found"));
        }

        void runTest()
        {
            runCommonSetup();
            setupScenario();
            runTestBase();
            Assert.assertEquals(STOPPING, helper.getTaskState());
        }
    }

}