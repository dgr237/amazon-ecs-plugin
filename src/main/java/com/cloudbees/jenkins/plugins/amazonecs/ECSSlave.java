/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.jenkins.plugins.amazonecs;

import com.cloudbees.jenkins.plugins.amazonecs.retentionStrategy.ECSRetentionStrategy;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.*;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This slave should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSSlave extends AbstractCloudSlave {
    private static final String DEFAULT_AGENT_PREFIX = "jenkins-agent";
    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private final String cloudName;
    private final ECSTaskTemplate template;
    private String taskArn;
    private String taskDefinitonArn;
    private State taskState;

    public enum State { None, Initializing, Running, Stopping }

    public ECSSlave(String name, ECSTaskTemplate template, String nodeDescription, String cloudName, String labelStr,
                    ComputerLauncher launcher, RetentionStrategy rs) throws Descriptor.FormException, IOException {
        super(name,
                nodeDescription,
                template.getRemoteFSRoot(),
                1,
                Mode.EXCLUSIVE,
                labelStr,
                launcher,
                rs,
                new ArrayList<>());
        this.cloudName = cloudName;
        this.template = template;
        this.taskState=State.None;
    }


    private String getCloudName() {
        return cloudName;
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
                LOGGER.log(Level.INFO,"Setting Slave "+getNodeName()+" State to Initializing");
                ECSComputer computer = (ECSComputer) getComputer();
                if (computer != null) {
                    computer.setAcceptingTasks(false);
                }
                break;
            }
            case Running: {
                LOGGER.log(Level.INFO,"Setting Slave "+getNodeName()+" State to Running");

                ECSComputer computer = (ECSComputer) getComputer();
                if (computer != null) {
                    computer.setAcceptingTasks(true);
                }
                break;
            }
            case Stopping: {
                LOGGER.log(Level.INFO,"Setting Slave "+getNodeName()+" State to Stopping");

                ECSComputer computer = (ECSComputer) getComputer();
                if (computer != null) {
                    computer.setAcceptingTasks(false);
                }
                try {
                    terminate();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error Terminating Slave when state set to Stopping");
                }
                break;
            }
        }
    }
    @Override
    public AbstractCloudComputer createComputer() {
        return new ECSComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating ECS Task Instance for agent {0}", name);
        ECSCloud cloud = null;
        try {
            cloud = getCloud();
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Unable to terminate agent {}. Cloud may have been removed. There may be leftover tasks", name);
        }

        VirtualChannel channel=this.getChannel();
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

    public ECSCloud getCloud() {
        Cloud cloud = Jenkins.get().getCloud(getCloudName());
        if (cloud instanceof ECSCloud) {
            return (ECSCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + ECSCloud.class.getName());
        }
    }

    Collection<String> getDockerRunCommand() {
        ECSCloud cloud=getCloud();
        Collection<String> command = new ArrayList<>();
        command.add("-url");
        command.add(cloud.getJenkinsUrl());
        String tunnel=cloud.getTunnel();
        if (StringUtils.isNotBlank(tunnel)) {
            command.add("-tunnel");
            command.add(tunnel);
        }
        command.add(getComputer().getJnlpMac());
        command.add(getComputer().getName());
        return command;
    }


    public static ECSSlave.Builder builder() {
        return new ECSSlave.Builder();
    }

    private static String getSlaveName(ECSTaskTemplate ecsTaskTemplate) {
        String randString= RandomStringUtils.random(5,"bcdefghijklmnopqrstuvwxyz0123456789");
        String name=ecsTaskTemplate.getTemplateName();
        if(StringUtils.isEmpty(name)) {
            return String.format("%s-%s",DEFAULT_AGENT_PREFIX,randString);
        }

        name=name.replaceAll("[ _]","-").toLowerCase();
        name=name.substring(0, Math.min(name.length(),62-randString.length()));
        return String.format("%s-%s",name,randString);
    }



    @Override
    public String toString() {
        return String.format("ECSSlave name: %s", name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ECSSlave that = (ECSSlave) o;

        return (cloudName != null ? cloudName.equals(that.cloudName) : that.cloudName == null) && (template != null ? template.equals(that.template) : that.template == null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (cloudName != null ? cloudName.hashCode() : 0);
        result = 31 * result + (template != null ? template.hashCode() : 0);
        return result;
    }

    public static class Builder {

        private String name;
        private String nodeDescription;
        private ECSTaskTemplate ecsTaskTemplate;
        private ECSCloud cloud;
        private String label;
        private ComputerLauncher computerLauncher;
        private RetentionStrategy retentionStrategy;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder nodeDescription(String nodeDescription) {
            this.nodeDescription = nodeDescription;
            return this;
        }

        public Builder ecsTaskTemplate(ECSTaskTemplate ecsTaskTemplate) {
            this.ecsTaskTemplate = ecsTaskTemplate;
            return this;
        }

        public Builder cloud(ECSCloud ecsCloud) {
            this.cloud = ecsCloud;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder computerLauncher(ComputerLauncher computerLauncher) {
            this.computerLauncher = computerLauncher;
            return this;
        }

        public Builder retentionStrategy(RetentionStrategy retentionStrategy) {
            this.retentionStrategy = retentionStrategy;
            return this;
        }

        public ECSSlave build() throws IOException, Descriptor.FormException {
            Validate.notNull(ecsTaskTemplate);
            Validate.notNull(cloud);
            return new ECSSlave(name == null ? getSlaveName(ecsTaskTemplate) : name,
                    ecsTaskTemplate,
                    nodeDescription == null ? ecsTaskTemplate.getTemplateName() : nodeDescription,
                    cloud.name,
                    label == null ? ecsTaskTemplate.getLabel() : label,
                    computerLauncher == null ? new ECSLauncher() : computerLauncher,
                    new ECSRetentionStrategy(ecsTaskTemplate.isSingleRunTask(), ecsTaskTemplate.getIdleTerminationMinutes()));
        }
    }
}