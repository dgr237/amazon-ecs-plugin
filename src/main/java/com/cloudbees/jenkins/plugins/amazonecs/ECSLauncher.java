package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State;

import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.*;
import static java.util.logging.Level.*;

class ECSLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(ECSLauncher.class.getName());

    ECSLauncher() {
        super(false);
    }

    @Override
    public boolean isLaunchSupported() {
        return true;
    }
    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        if (!(computer instanceof ECSComputerImpl)) {
            throw new IllegalArgumentException("This launcher can only be used with ECSComputer");
        }

        ECSComputer ecsComputer = (ECSComputer) computer;

        launch(ecsComputer,listener);
    }

    void launch(ECSComputer computer, TaskListener listener)
    {
        LOGGER.log(Level.INFO,"Launch called on computer: "+computer.getName());
        ECSSlaveLaunchWorkflow workflow=new ECSSlaveLaunchWorkflow(computer,listener);
        workflow.run();
    }

    public static class ECSSlaveLaunchWorkflow {

        private Object waitHandle=new Object();
        private final ECSComputer computer;
        private final TaskListener listener;
        private ECSSlave slave;
        private ECSTaskTemplate template;
        private ECSCloud cloud;
        private ECSService service;
        private PrintStream logger;
        private TaskDefinition taskDefinition;
        private String taskArn;
        private State state;

        public ECSSlaveLaunchWorkflow(ECSComputer computer, TaskListener listener) {
            this.computer = computer;
            this.listener = listener;
        }

        public void run() {
            try {
                slave = computer.getNode();
                if (slave == null) {
                    throw new IllegalStateException("Node has been removed, cannot launch" + computer.getName());
                }

                if (slave.getHelper().getTaskState() != None) {
                    LOGGER.log(Level.INFO, "Slave " + slave.getNodeName() + " has already been initialized");
                    return;
                }

                cloud = slave.getCloud();
                if (cloud == null) {
                    throw new IllegalStateException("Cloud has been removed: " + slave.getNodeName());
                }
                template = slave.getHelper().getTemplate();
                if (template == null) {
                    throw new IllegalStateException("Template is null for Slave: " + slave.getNodeName());
                }
                service = cloud.getEcsService();
                if (service == null) {
                    throw new IllegalStateException("ECSClient is null for Slave: " + slave.getNodeName());
                }
                logger = listener.getLogger();
                setTaskState(Initializing);
            } catch (IllegalStateException ex) {
                LOGGER.log(WARNING, "Error launching slave: " + slave.getNodeName(), ex);
                setTaskState(Stopping);
            }
        }

        private void createTaskDefinition() {
            TaskDefinition taskDefinition;
            try {
                if (template.getTaskDefinitionOverride() == null) {
                    taskDefinition = service.registerTemplate(slave.getCloud(), template);
                } else {
                    LOGGER.log(FINE, "Attempting to find task definition family or ARN: {0}", template.getTaskDefinitionOverride());

                    taskDefinition = service.findTaskDefinition(template.getTaskDefinitionOverride());
                    if (taskDefinition == null) {
                        LOGGER.log(WARNING,"Could not find task definition family or ARN: " + template.getTaskDefinitionOverride());
                        setTaskState(Stopping);
                        return;
                    }

                    LOGGER.log(FINE, "Found task definition: {0}", taskDefinition.getTaskDefinitionArn());
                }
            } catch (ServerException | ClientException | InvalidParameterException | ClusterNotFoundException ex) {
                LOGGER.log(Level.WARNING, "Error Creating Task Definition for Label: " + template.getLabel(), ex);
                setTaskState(Stopping);
                return;
            }
            setTaskDefinition(taskDefinition);
            setTaskState(TaskDefinitionCreated);

        }


        private void runTask() {
            try {
                LOGGER.log(Level.INFO, "Running task definition {0} on slave {1}", new Object[]{taskDefinition.getTaskDefinitionArn(), slave.getNodeName()});

                String taskArn = service.runEcsTask(slave, template, cloud.getCluster(), slave.getHelper().getDockerRunCommand(), taskDefinition);
                LOGGER.log(Level.INFO, "Slave {0} - Slave Task Started : {1}",
                        new Object[]{slave.getNodeName(), taskArn});
                setTaskArn(taskArn);
                setTaskState(TaskCreated);
            } catch (ServerException | ClientException | AbortException | UnsupportedFeatureException | PlatformUnknownException | PlatformTaskDefinitionIncompatibilityException | AccessDeniedException | BlockedException | InvalidParameterException | ClusterNotFoundException ex) {
                LOGGER.log(Level.WARNING, "Slave " + slave.getNodeName() + " - Cannot create ECS Task", ex);
                setTaskState(Stopping);
            }
        }

        private void waitForTaskToRun() {

            LOGGER.log(INFO, "Waiting for Task to be running: {0}", taskArn);
            synchronized (waitHandle) {
                try {
                    int i = 0;
                    int j = 100; // wait 600 seconds
                    // wait for Pod to be running
                    while (i++ < j && state == TaskCreated) {
                        String status = service.getTaskStatus(cloud, taskArn);

                        if (status.equals("STOPPED") || status.equals("DEPROVISIONING")) {
                            LOGGER.log(INFO, "Task: " + taskArn + " has been Stopped");
                            setTaskState(Stopping);
                            break;
                        }

                        if (status.equals("RUNNING")) {
                            setTaskState(TaskLaunched);
                            break;
                        }
                        LOGGER.log(FINE, "Waiting for Task to be running ({1}/{2}): {0}: Current State: {3}", new Object[]{taskArn, i, j, status});
                        logger.printf("Waiting for Task to be running (%2$s/%3$s): %1$s%n", taskArn, i, j);
                        waitHandle.wait(6000);
                    }
                } catch (ServerException | ClientException | InvalidParameterException | ClusterNotFoundException | InterruptedException ex) {
                    LOGGER.log(SEVERE, "Error Getting Task Status: " + taskArn, ex);
                    setTaskState(Stopping);
                }
            }
        }

        private void waitForAgentToConnect() {
            synchronized (waitHandle) {
                int i = 0;
                int j = cloud.getSlaveTimoutInSeconds();
                try {
                    while (i++ < j && state == TaskLaunched) {
                        if (slave.getECSComputer() == null) {
                            LOGGER.log(WARNING, "Node was deleted, computer is null");
                            setTaskState(Stopping);
                            break;
                        }
                        if (computer.isOnline()) {
                            setTaskState(Running);
                            break;
                        }
                        LOGGER.log(FINE, "Waiting for agent to connect ({1}/{2}): {0}", new Object[]{slave.getNodeName(), i, j});
                        logger.printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", slave.getNodeName(), i, j);
                        waitHandle.wait(1000);
                    }
                    if (state== TaskLaunched) {
                        LOGGER.log(WARNING, "Agent " + slave.getNodeName() + " is not connected after " + cloud.getSlaveTimoutInSeconds() + " attempts. Stopping Slave.");
                        setTaskState(Stopping);
                    }
                } catch (InterruptedException ex) {
                    LOGGER.log(SEVERE, "Error Waiting for Agent to connect: " + slave.getNodeName(), ex);
                    setTaskState(Stopping);
                }
            }
        }


        private void saveSlave() {
            try {
                slave.save();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Could not save agent: "+slave.getNodeName(), ex);
            }
        }

        private void setTaskDefinition(TaskDefinition taskDefinition) {
            this.taskDefinition = taskDefinition;
        }

        private void setTaskArn(String taskArn) {
            this.taskArn = taskArn;
            slave.getHelper().setTaskArn(taskArn);
        }

        private void setTaskState(State state) {
            this.state = state;

            slave.getHelper().setTaskState(state);
            switch (this.state) {
                case Initializing:
                    createTaskDefinition();
                    break;
                case TaskDefinitionCreated:
                    runTask();
                    break;
                case TaskCreated:
                    waitForTaskToRun();
                    break;
                case TaskLaunched:
                    waitForAgentToConnect();
                    break;
                case Running:
                    saveSlave();
                    break;
                case Stopping:
                    break;
            }
        }

    }
}
