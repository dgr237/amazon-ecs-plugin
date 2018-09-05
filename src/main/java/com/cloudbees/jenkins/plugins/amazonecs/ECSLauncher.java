package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.AbortedException;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.google.common.base.Throwables;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import org.apache.log4j.lf5.LogLevel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

class ECSLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(ECSLauncher.class.getName());
    private boolean launched;

    ECSLauncher() {
        super(false);
    }

    @Override
    public boolean isLaunchSupported() {
        return !launched;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        PrintStream logger = listener.getLogger();

        if (!(computer instanceof ECSComputer)) {
            throw new IllegalArgumentException("This launcher can only be used with ECSComputer");
        }

        ECSComputer ecsComputer = (ECSComputer) computer;
        computer.setAcceptingTasks(false);

        ECSSlave slave = ecsComputer.getNode();
        if (slave == null) {
            throw new IllegalStateException("Node has been removed, cannot launch" + computer.getName());
        }

        if (launched) {
            LOGGER.log(Level.INFO, "Agent has already been launched, activating: "+ slave.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        ECSCloud cloud = slave.getCloud();
        ECSTaskTemplate template = slave.getTemplate();
        ECSService service = cloud.getEcsService();
        Date now = new Date();
        Date timeout = new Date(now.getTime() + 1000 * cloud.getSlaveTimoutInSeconds());

        synchronized (cloud.getCluster()) {
            try {
                TaskDefinition taskDefinition;

                if (template.getTaskDefinitionOverride() == null) {
                    taskDefinition = cloud.getEcsService().registerTemplate(slave.getCloud(), template);
                } else {
                    LOGGER.log(Level.FINE, "Attempting to find task definition family or ARN: {0}", template.getTaskDefinitionOverride());

                    taskDefinition = service.findTaskDefinition(template.getTaskDefinitionOverride());
                    if (taskDefinition == null) {
                        throw new RuntimeException("Could not find task definition family or ARN: " + template.getTaskDefinitionOverride());
                    }

                    LOGGER.log(Level.FINE, "Found task definition: {0}", taskDefinition.getTaskDefinitionArn());
                }

                LOGGER.log(Level.INFO, "Running task definition {0} on slave {1}", new Object[]{taskDefinition.getTaskDefinitionArn(), slave.getNodeName()});

                String taskArn = service.runEcsTask(slave, template, cloud.getCluster(), slave.getDockerRunCommand(), taskDefinition);
                LOGGER.log(Level.INFO, "Slave {0} - Slave Task Started : {1}",
                        new Object[]{slave.getNodeName(), taskArn});
                slave.setTaskArn(taskArn);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Slave {0} - Cannot create ECS Task");
                try {
                    slave.terminate();
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Unable to remove Jenkins Node", e);
                }

                throw Throwables.propagate(ex);
            }
            int j = 100; // wait 600 seconds

            String taskArn = slave.getTaskArn();
            LOGGER.log(INFO, "Waiting for Task to be running: {0}", taskArn);


            try {
                // wait for Pod to be running
                for (int i=0; i < j; i++) {
                    String status = service.getTaskStatus(cloud, taskArn);

                    if (status.equals("STOPPED") || status.equals("DEPROVISIONING")) {
                        throw new IllegalStateException("Task: " + taskArn + " has been Stopped");
                    }

                    if (status.equals("RUNNING")) {
                        break;
                    } else {
                        LOGGER.log(INFO, "Waiting for Task to be running ({1}/{2}): {0}: Current State: {3}", new Object[]{taskArn, i, j, status});
                        logger.printf("Waiting for Task to be running (%2$s/%3$s): %1$s%n", taskArn, i, j);

                        Thread.sleep(6000);
                    }

                }

                for (int i=0; i < cloud.getSlaveTimoutInSeconds(); i++) {
                    if (slave.getComputer() == null) {
                        throw new IllegalStateException("Node was deleted, computer is null");
                    }
                    if (slave.getComputer().isOnline()) {
                        break;
                    }
                    LOGGER.log(INFO, "Waiting for agent to connect ({1}/{2}): {0}", new Object[]{taskArn, i, j});
                    logger.printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", taskArn, i, j);
                    Thread.sleep(1000);
                }
                if (!computer.isOnline()) {
                    throw new IllegalStateException("Agent is not connected after " + cloud.getSlaveTimoutInSeconds() + " attempts");
                }

                computer.setAcceptingTasks(true);
            }
            catch (Exception ex) {
                LOGGER.log(SEVERE, "Error Getting Task Status: {0}: {1}", new Object[]{taskArn, ex.getMessage()});
                try {
                    slave.terminate();
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
                }
                throw Throwables.propagate(ex);
            }

            launched = true;
            try {
                slave.save();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Could not save() agent", ex);
            }
        }
    }
}
