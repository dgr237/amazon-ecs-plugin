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
import org.apache.commons.lang.StringUtils;

import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.State.*;
import static java.util.logging.Level.*;

class ECSLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(ECSLauncher.class.getName());

    ECSLauncher(boolean enableWorkDir) {
        super(enableWorkDir);
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

    static final class ECSSlaveLaunchWorkflow {

        private final Object waitHandle=new Object();
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

        ECSSlaveLaunchWorkflow(ECSComputer computer, TaskListener listener) {
            this.computer = computer;
            this.listener = listener;
        }

        void run() {
            String nodeName=null;
            try {
                slave = computer.getECSNode();

                if (slave == null) {
                    throw new IllegalStateException("Node has been removed, cannot launch" + computer.getName());
                }
                else
                {
                    nodeName=slave.getNodeName();
                }


                if (slave.getHelper().getTaskState() != NONE) {
                    LOGGER.log(Level.INFO, "Slave {0} has already been initialized",nodeName);
                    return;
                }

                cloud = slave.getCloud();
                if (cloud == null) {
                    throw new IllegalStateException("Cloud has been removed: " + nodeName);
                }
                template = slave.getHelper().getTemplate();
                if (template == null) {
                    throw new IllegalStateException("Template is null for Slave: " + nodeName);
                }
                service = cloud.getEcsService();
                logger = listener.getLogger();
                setTaskState(INITIALIZING);
            } catch (IllegalStateException ex) {
                LOGGER.log(WARNING, "Error launching slave: " + StringUtils.defaultIfBlank(nodeName,"{Null}"), ex);
                setTaskState(STOPPING);
            }
        }

        private void createTaskDefinition() {
            TaskDefinition definition;
            try {
                if (template.getTaskDefinitionOverride() == null) {
                    definition = service.registerTemplate(slave.getCloud(), template);
                } else {
                    LOGGER.log(FINE, "Attempting to find task definition family or ARN: {0}", template.getTaskDefinitionOverride());

                    definition = service.findTaskDefinition(template.getTaskDefinitionOverride());
                    if (definition == null) {
                        LOGGER.log(WARNING,"Could not find task definition family or ARN: " + template.getTaskDefinitionOverride());
                        setTaskState(STOPPING);
                        return;
                    }

                    LOGGER.log(FINE, "Found task definition: {0}", definition.getTaskDefinitionArn());
                }
            } catch (ServerException | ClientException | InvalidParameterException | ClusterNotFoundException ex) {
                LOGGER.log(Level.WARNING, "Error Creating Task Definition for Label: " + template.getLabel(), ex);
                setTaskState(STOPPING);
                return;
            }
            setTaskDefinition(definition);
            setTaskState(TASK_DEFINITION_CREATED);

        }


        private void runTask() {
            try {
                LOGGER.log(Level.INFO, "RUNNING task definition {0} on slave {1}", new Object[]{taskDefinition.getTaskDefinitionArn(), slave.getNodeName()});

                String taskarn = service.runEcsTask(slave, template, cloud.getCluster(), slave.getHelper().getDockerRunCommand(), taskDefinition);
                LOGGER.log(Level.INFO, "Slave {0} - Slave Task Started : {1}",
                        new Object[]{slave.getNodeName(), taskarn});
                setTaskArn(taskarn);
                setTaskState(TASK_CREATED);
            } catch (ServerException | ClientException | AbortException | UnsupportedFeatureException | PlatformUnknownException | PlatformTaskDefinitionIncompatibilityException | AccessDeniedException | BlockedException | InvalidParameterException | ClusterNotFoundException ex) {
                LOGGER.log(Level.WARNING, "Slave " + slave.getNodeName() + " - Cannot create ECS Task", ex);
                setTaskState(STOPPING);
            }
        }

        private void waitForTaskToRun() {

            LOGGER.log(INFO, "Waiting for Task to be running: {0}", taskArn);
            synchronized (waitHandle) {
                try {
                    int i = 0;
                    int j = template.getSlaveLaunchTimeoutSeconds();
                    // wait for Pod to be running
                    while (i++ < j && state == TASK_CREATED) {
                        String status = service.getTaskStatus(cloud, taskArn);

                        if (status.equals("STOPPED") || status.equals("DEPROVISIONING")) {
                            LOGGER.log(INFO, "Task: {0} has been Stopped",taskArn);
                            setTaskState(STOPPING);
                            return;
                        }

                        if (status.equals("RUNNING")) {
                            setTaskState(TASK_LAUNCHED);
                            return;
                        }
                        LOGGER.log(FINE, "Waiting for Task to be running ({1}/{2}): {0}: Current State: {3}", new Object[]{taskArn, i, j, status});
                        logger.printf("Waiting for Task to be running (%2$s/%3$s): %1$s%n", taskArn, i, j);
                        waitHandle.wait(1000);
                    }
                } catch (ServerException | ClientException | InvalidParameterException | ClusterNotFoundException | InterruptedException ex) {
                    LOGGER.log(SEVERE, "Error Getting Task Status: " + taskArn, ex);
                    setTaskState(STOPPING);
                }
            }
        }

        private void waitForAgentToConnect() {
            synchronized (waitHandle) {
                int i = 0;
                int j = cloud.getSlaveTimoutInSeconds();
                try {
                    while (i++ < j && state == TASK_LAUNCHED) {
                        if (slave.getECSComputer() == null) {
                            LOGGER.log(WARNING, "Node was deleted, computer is null");
                            setTaskState(STOPPING);
                            return;
                        }
                        if (computer.isOnline()) {
                            setTaskState(RUNNING);
                            return;
                        }
                        LOGGER.log(FINE, "Waiting for agent to connect ({1}/{2}): {0}", new Object[]{slave.getNodeName(), i, j});
                        logger.printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", slave.getNodeName(), i, j);
                        waitHandle.wait(1000);
                    }

                    LOGGER.log(WARNING, "Agent " + slave.getNodeName() + " is not connected after " + cloud.getSlaveTimoutInSeconds() + " attempts. STOPPING Slave.");
                    setTaskState(STOPPING);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(SEVERE, "Error Waiting for Agent to connect: " + slave.getNodeName(), ex);
                    setTaskState(STOPPING);
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
                case INITIALIZING:
                    createTaskDefinition();
                    break;
                case TASK_DEFINITION_CREATED:
                    runTask();
                    break;
                case TASK_CREATED:
                    waitForTaskToRun();
                    break;
                case TASK_LAUNCHED:
                    waitForAgentToConnect();
                    break;
                case RUNNING:
                    saveSlave();
                    break;
                default:
                    break;

            }
        }

    }
}
