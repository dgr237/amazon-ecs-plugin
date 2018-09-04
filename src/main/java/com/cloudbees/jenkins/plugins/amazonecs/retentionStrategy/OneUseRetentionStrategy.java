package com.cloudbees.jenkins.plugins.amazonecs.retentionStrategy;

import com.cloudbees.jenkins.plugins.amazonecs.ECSComputer;
import hudson.node_monitors.ResponseTimeMonitor;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.RetentionStrategy;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OneUseRetentionStrategy extends RetentionStrategy<ECSComputer> {

    private static final Logger LOGGER = Logger.getLogger(OneUseRetentionStrategy.class.getName());

    @Override
    public boolean isManualLaunchAllowed(ECSComputer c) {
        return false;
    }

    @Override
    @GuardedBy("hudson.model.Queue.lock")
    public long check(@Nonnull ECSComputer c) {
        LOGGER.log(Level.FINE, "Checking computer: {0}", c);

        AbstractCloudSlave node = c.getNode();

        // If the computer is NOT idle, then it is currently running some task.
        // In this case, we are going to tell Jenkins that it can no longer accept
        // any new tasks, which will cause it to create a new node for any subsequent
        // tasks.
        if (!c.isIdle()) {
            LOGGER.log(Level.FINE, "Computer is not idle; setting it to no longer accept tasks.");
            c.setAcceptingTasks(false);
        }

        // If the computer IS idle AND it is no longer accepting tasks, then it has
        // already had a task and completed it.  In this case, we are going to terminate
        // the node.
        if (c.isIdle() && !c.isAcceptingTasks() && node != null) {
            LOGGER.log(Level.FINE, "Computer is idle and not accepting tasks; terminating it.");
            try {
                node.terminate();
            } catch (InterruptedException | IOException e) {
                LOGGER.log(Level.WARNING, "Failed to terminate " + c.getName(), e);
            }
        }

        // If the Response Time Monitor has marked this computer as not responding, then
        // we are going to terminate the node to free up resources.
        if (c.getOfflineCause() instanceof ResponseTimeMonitor.Data && node != null) {
            LOGGER.log(Level.FINE, "Computer is not responding; terminating it");
            try {
                node.terminate();
            } catch (InterruptedException | IOException e) {
                LOGGER.log(Level.WARNING, "Failed to terminate " + c.getName(), e);
            }
        }

        // Tell Jenkins to check again in 1 minute.
        return 1;
    }
}
