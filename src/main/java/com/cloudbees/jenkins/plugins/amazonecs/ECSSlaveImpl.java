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
import hudson.slaves.*;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * This slave should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSSlaveImpl extends AbstractCloudSlave implements ECSSlave {
    private static final String DEFAULT_AGENT_PREFIX = "jenkins-agent";
    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private ECSSlaveStateManager slave;
    private final String cloudName;
    private final ECSTaskTemplate template;



    public ECSSlaveImpl(String name, ECSTaskTemplate template, String nodeDescription, String cloudName, String labelStr,
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
        slave=new ECSSlaveStateManager(this,name,template);
        this.cloudName=cloudName;
        this.template=template;
    }


    private String getCloudName() {
        return cloudName;
    }

    public ECSTaskTemplate getTemplate() {
        return template;
    }

    @Override
    public ECSComputer getECSComputer() {
        SlaveComputer computer=getComputer();
        if(computer instanceof ECSComputerImpl)
        {
            return (ECSComputer) computer;
        }
        return null;

    }

    public ECSSlaveStateManager getInnerSlave() {
        return slave;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new ECSComputerImpl(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException { slave._terminate(listener); }

    @Override
    public ECSCloud getCloud() {
        Cloud cloud = Jenkins.get().getCloud(getCloudName());
        if (cloud instanceof ECSCloud) {
            return (ECSCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + ECSCloud.class.getName());
        }
    }

    public Collection<String> getDockerRunCommand() { return slave.getDockerRunCommand(); }

    public static ECSSlaveImpl.Builder builder() {
        return new ECSSlaveImpl.Builder();
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

    @Override
    public String toString() {
        return String.format("ECSSlave name: %s", name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ECSSlaveImpl that = (ECSSlaveImpl) o;

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

        public ECSSlaveImpl build() throws IOException, Descriptor.FormException {
            Validate.notNull(ecsTaskTemplate);
            Validate.notNull(cloud);
            return new ECSSlaveImpl(name == null ? getSlaveName(ecsTaskTemplate) : name,
                    ecsTaskTemplate,
                    nodeDescription == null ? ecsTaskTemplate.getTemplateName() : nodeDescription,
                    cloud.name,
                    label == null ? ecsTaskTemplate.getLabel() : label,
                    computerLauncher == null ? new ECSLauncher() : computerLauncher,
                    new ECSRetentionStrategy(ecsTaskTemplate.isSingleRunTask(), ecsTaskTemplate.getIdleTerminationMinutes()));
        }
    }
}