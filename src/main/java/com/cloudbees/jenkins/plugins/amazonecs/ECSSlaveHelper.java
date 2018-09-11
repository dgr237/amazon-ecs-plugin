package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;

public class ECSSlaveHelper
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

    public ECSSlaveHelper(ECSSlave slave, String name, ECSTaskTemplate template) {
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
                    computer.setAcceptingTasks(false);
                }
                break;
            }
            case Running: {
                LOGGER.log(Level.INFO,"Setting Slave "+name+" State to Running");

                ECSComputer computer = slave.getECSComputer();
                if (computer != null) {
                    computer.setAcceptingTasks(true);
                }
                break;
            }
            case Stopping: {
                LOGGER.log(Level.INFO,"Setting Slave "+name+" State to Stopping");

                ECSComputer computer = slave.getECSComputer();
                if (computer != null) {
                    computer.setAcceptingTasks(false);
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

    static String getSlaveName(ECSTaskTemplate ecsTaskTemplate) {
        String randString= RandomStringUtils.random(5,"bcdefghijklmnopqrstuvwxyz0123456789");
        String name=ecsTaskTemplate.getTemplateName();
        if(StringUtils.isEmpty(name)) {
            return String.format("%s-%s",DEFAULT_AGENT_PREFIX,randString);
        }

        name=name.replaceAll("[ _]","-").toLowerCase();
        name=name.substring(0, Math.min(name.length(),62-randString.length()));
        return String.format("%s-%s",name,randString);
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
            cloud.getEcsService().deleteTask(taskArn, cloud.getCluster());
        }
    }

    public void checkIfShouldTerminate(int idleMinutes) {
        ECSComputer computer=slave.getECSComputer();
        switch (taskState)
        {
            case Running:
                if (computer!=null && idleMinutes!=0 && computer.isIdle()) {
                    final long idleMilliseconds = System.currentTimeMillis() - computer.getIdleStartMilliseconds();
                    if (idleMilliseconds > MINUTES.toMillis(idleMinutes)) {
                        LOGGER.log(Level.INFO, "Computer is Idle. Disconnecting {0}", computer.getName());
                        setTaskState(State.Stopping);
                    }
                }
                break;
        }
    }
}
