package com.cloudbees.jenkins.plugins.amazonecs.retentionStrategy;

import com.cloudbees.jenkins.plugins.amazonecs.ECSComputer;
import com.cloudbees.jenkins.plugins.amazonecs.ECSSlave;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.RetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;

public class ECSRetentionStrategy extends RetentionStrategy<ECSComputer> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(ECSRetentionStrategy.class.getName());

    private int idleMinutes =0;
    private boolean isSingleTask = false;


    @DataBoundConstructor
    public ECSRetentionStrategy(boolean isSingleTask, int idleMinutes) {
        this.idleMinutes=idleMinutes;
        this.isSingleTask=isSingleTask;
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

        if(computer instanceof ECSComputer)
        {
            LOGGER.log(Level.INFO,"Terminating {0} because it has completed",computer.getName());
            ECSComputer ecsComputer=(ECSComputer)computer;
            ecsComputer.getNode().setTaskState(ECSSlave.State.Stopping);
        }
    }

    @Override
    public long check(@Nonnull ECSComputer c) {
        ECSSlave slave=c.getNode();
        if(slave!=null)
        {
            ECSSlave.State state=slave.getTaskState();
            switch (state)
            {
                case Running:
                    if (c!=null && isTerminable() && c.isIdle()) {
                        final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                        if (idleMilliseconds > MINUTES.toMillis(idleMinutes)) {
                            LOGGER.log(Level.INFO, "Disconnecting {0}", c.getName());
                            c.getNode().setTaskState(ECSSlave.State.Stopping);
                        }
                    }
                    break;
            }
        }
        return 1;
    }

    @Override
    public void start(ECSComputer c) {
        c.connect(false);
    }

    public boolean isTerminable()
    {
        return idleMinutes!=0;
    }
}
