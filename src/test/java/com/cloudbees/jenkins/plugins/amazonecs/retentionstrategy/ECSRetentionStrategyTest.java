package com.cloudbees.jenkins.plugins.amazonecs.retentionstrategy;

import com.cloudbees.jenkins.plugins.amazonecs.*;
import hudson.model.Executor;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.RUNNING;
import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.STOPPING;
import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ECSComputerImpl.class)
public class ECSRetentionStrategyTest {
    private ECSComputerImpl computer;
    private Executor executor;
    private ECSSlaveHelper helper;

    @Before
    public void setup() {
        computer = mock(ECSComputerImpl.class);
        ECSSlave slave = mock(ECSSlave.class);

        ECSTaskTemplate testTemplate = new ECSTaskTemplate("maven-java","maven-java",null,"FARGATE")
                .withImage("cloudbees/maven-java")
                .withMemory(2048)
                .withCpu(2048)
                .withAssignPublicIp(true)
                .withSecurityGroups("secGroup")
                .withSubnets("subnets")
                .withPrivileged(true)
                .withSingleRunTask(true)
                .withIdleTerminationMinutes(1);
        helper = new ECSSlaveHelper(slave, "Test", testTemplate);
        executor = mock(Executor.class);
        when(slave.getECSComputer()).thenReturn(computer);
        when(slave.getHelper()).thenReturn(helper);
        when(executor.getOwner()).thenReturn(computer);
        when(computer.getECSNode()).thenReturn(slave);
        helper.setTaskState(RUNNING);
    }

    @Test
    public void whenTaskCompletedCalledAndRetentionPolicyIsSingleTaskThenSlaveIsStopped() {
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(true,1);
        strategy.taskCompleted(executor,null,1000);
        assertEquals(STOPPING,helper.getTaskState());
    }

    @Test
    public void whenTaskCompletedCalledAndRetentionPolicyIsNotSingleTaskThenSlaveIsRunning() {
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(false,1);
        strategy.taskCompleted(executor,null,1000);
        assertEquals(RUNNING,helper.getTaskState());
    }

    @Test
    public void whenTaskCompletedWithProblemsCalledAndRetentionPolicyIsSingleTaskThenSlaveIsStopped() {
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(true,1);
        strategy.taskCompletedWithProblems(executor,null,1000,null);
        assertEquals(STOPPING,helper.getTaskState());
    }

    @Test
    public void whenTaskCompletedWithProblemsCalledAndRetentionPolicyIsNotSingleTaskThenSlaveIsStopped() {
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(false,1);
        strategy.taskCompletedWithProblems(executor,null,1000,null);
        assertEquals(RUNNING,helper.getTaskState());
    }

    @Test
    public void whenCheckCalledAndComputerIsNotIdleThenStateShouldBeRunning() {
        //doAnswer(invocation -> {return false;}).when(computer).isIdle();
        when(computer.isIdle()).thenReturn(false);
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(false,1);
        strategy.check(computer);
        assertEquals(RUNNING,helper.getTaskState());
    }

    @Test
    public void whenCheckCalledAndComputerIsIdleButWithinTheTimespanThenStateShouldBeRunning() {
        //doAnswer(invocation -> {return false;}).when(computer).isIdle();
        when(computer.isIdle()).thenReturn(true);
        when(computer.getIdleStartMilliseconds()).thenReturn(new DateTime().minus(2000).getMillis());
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(false,1);
        strategy.check(computer);
        assertEquals(RUNNING,helper.getTaskState());
    }

    @Test
    public void whenCheckCalledAndComputerIsIdleButNotWithinTheTimespanThenStateShouldBeStopping() {
        //doAnswer(invocation -> {return false;}).when(computer).isIdle();
        when(computer.isIdle()).thenReturn(true);
        when(computer.getIdleStartMilliseconds()).thenReturn(new DateTime().minus(120000).getMillis());
        ECSRetentionStrategy strategy=new ECSRetentionStrategy(false,1);
        strategy.check(computer);
        assertEquals(STOPPING,helper.getTaskState());
    }
}
