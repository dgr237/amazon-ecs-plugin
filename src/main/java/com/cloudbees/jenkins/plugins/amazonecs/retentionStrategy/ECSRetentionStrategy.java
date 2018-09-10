package com.cloudbees.jenkins.plugins.amazonecs.retentionStrategy;

import com.cloudbees.jenkins.plugins.amazonecs.ECSComputer;
import com.cloudbees.jenkins.plugins.amazonecs.ECSComputerImpl;
import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper;
import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveImpl;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.RetentionStrategy;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State;

import static java.util.concurrent.TimeUnit.MINUTES;

public class ECSRetentionStrategy extends RetentionStrategy<ECSComputerImpl> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(ECSRetentionStrategy.class.getName());

    private int idleMinutes =0;
    private boolean isSingleTask = false;

    public ECSRetentionStrategy(boolean isSingleTask, int idleMinutes) {
        this.idleMinutes=idleMinutes;
        this.isSingleTask=isSingleTask;
        LOGGER.log(Level.INFO,"ECS Retention Strategy configured: Single Task: "+isSingleTask+"; Idle Minutes: "+idleMinutes);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {

    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        if (isSingleTask) {
            done(executor);
        }
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
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
            ecsComputer.getNode().getHelper().setTaskState(State.Stopping);
        }
    }

    @Override
    public long check(@Nonnull ECSComputerImpl c) {
        ECSSlaveImpl slave=c.getNode();
        if(slave!=null)
        {
            ECSSlaveHelper helper=slave.getHelper();
            helper.checkIfShouldTerminate(idleMinutes);
        }
        return 1;
    }

    @Override
    public void start(ECSComputerImpl c) {
        c.connect(false);
    }


}
