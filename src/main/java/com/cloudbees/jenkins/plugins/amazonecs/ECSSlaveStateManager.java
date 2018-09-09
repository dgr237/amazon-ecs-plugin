package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSSlaveStateManager
{
    public enum State { None, Initializing, TaskDefinitionCreated, TaskCreated, TaskLaunched, Running, Stopping }

    private static final String DEFAULT_AGENT_PREFIX = "jenkins-agent";
    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private final ECSSlave slave;
    private final String name;
    private final ECSTaskTemplate template;
    private String taskArn;
    private String taskDefinitonArn;
    private State taskState;

    public ECSSlaveStateManager(ECSSlave slave, String name, ECSTaskTemplate template) {
        this.slave=slave;
        this.name=name;
        this.template=template;
        taskState= State.None;
    }


    public Collection<String> getDockerRunCommand() {
        ECSCloud cloud=slave.getCloud();
        ECSComputer computer=slave.getECSComputer();
        Collection<String> command = new ArrayList<>();
        command.add("-url");
        command.add(cloud.getJenkinsUrl());
        String tunnel=cloud.getTunnel();
        if (StringUtils.isNotBlank(tunnel)) {
            command.add("-tunnel");
            command.add(tunnel);
        }
        command.add(computer.getJnlpMac());
        command.add(computer.getName());
        return command;
    }

    public ECSTaskTemplate getTemplate() {
        return template;
    }


    public String getTaskArn() {
        return taskArn;
    }

    public void setTaskArn(String taskArn) {
        this.taskArn = taskArn;
    }

    public State getTaskState() {return taskState;}

    public void setTaskState(State currentState) {
        taskState = currentState;
        switch (taskState) {
            case Initializing: {
                LOGGER.log(Level.INFO,"Setting Slave "+name+" State to Initializing");
                ECSComputer computer = slave.getECSComputer();
                if (computer != null) {
                    computer.setAcceptingECSTasks(false);
                }
                break;
            }
            case Running: {
                LOGGER.log(Level.INFO,"Setting Slave "+name+" State to Running");

                ECSComputer computer = slave.getECSComputer();
                if (computer != null) {
                    computer.setAcceptingECSTasks(true);
                }
                break;
            }
            case Stopping: {
                LOGGER.log(Level.INFO,"Setting Slave "+name+" State to Stopping");

                ECSComputer computer = slave.getECSComputer();
                if (computer != null) {
                    computer.setAcceptingECSTasks(false);
                }
                try {
                    slave.terminate();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error Terminating Slave when state set to Stopping");
                }
                break;
            }
        }
    }

    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating ECS Task Instance for agent {0}", name);
        ECSCloud cloud = null;
        try {
            cloud = slave.getCloud();
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Unable to terminate agent {}. Cloud may have been removed. There may be leftover tasks", name);
        }

        VirtualChannel channel=slave.getChannel();
        if(channel!=null)
        {
            LOGGER.log(Level.INFO, "Closing Channel for agent {0}", name);
            channel.close();
        }
        if (taskArn != null && cloud != null) {
            LOGGER.log(Level.INFO, "Deleting Task: {0} for agent {1}", new Object[] {taskArn, name});
            cloud.deleteTask(taskArn);
        }
    }
}
