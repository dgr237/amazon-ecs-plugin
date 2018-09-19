package com.cloudbees.jenkins.plugins.amazonecs.retentionstrategy;

import com.cloudbees.jenkins.plugins.amazonecs.*;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue.Task;
import hudson.slaves.RetentionStrategy;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State;

public class ECSRetentionStrategy extends RetentionStrategy<ECSComputerImpl> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(ECSRetentionStrategy.class.getName());

    private final int idleMinutes;
    private final boolean isSingleTask;

    public ECSRetentionStrategy(boolean isSingleTask, int idleMinutes) {
        this.idleMinutes=idleMinutes;
        this.isSingleTask=isSingleTask;
        LOGGER.log(Level.INFO,"ECS Retention Strategy configured: Single Task: {0}; Idle Minutes: {1}", new Object[] {isSingleTask, idleMinutes});
    }

    @Override
    public void taskAccepted(Executor executor, Task task) {
        //Do nothing. part of interface but don't need to do anything
    }

    @Override
    public void taskCompleted(Executor executor, Task task, long durationMS) {
        if (isSingleTask) {
            done(executor);
        }
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Task task, long durationMS, Throwable problems) {
        if(isSingleTask) {
            done(executor);
        }
    }

    private void done(Executor executor)
    {
        Computer computer=executor.getOwner();

        if(computer instanceof ECSComputerImpl)
        {
            LOGGER.log(Level.INFO,"Terminating {0} because it has completed",computer.getName());
            ECSComputer ecsComputer=(ECSComputer)computer;
            ecsComputer.getECSNode().getHelper().setTaskState(State.STOPPING);
        }
    }

    @Override
    public long check(@Nonnull ECSComputerImpl c) {
        ECSSlave slave=c.getECSNode();
        if(slave!=null)
        {
            ECSSlaveHelper helper=slave.getHelper();
            helper.checkIfShouldTerminate(idleMinutes);
        }
        return 1;
    }

    @Override
    public void start(@Nonnull ECSComputerImpl c) {
        c.connect(false);
    }


}
