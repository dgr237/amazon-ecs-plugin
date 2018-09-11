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
import org.apache.commons.lang.Validate;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This helper should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSSlaveImpl extends AbstractCloudSlave implements ECSSlave {
    private ECSSlaveHelper helper;
    private final String cloudName;

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
        helper =new ECSSlaveHelper(this,name,template);
        this.cloudName=cloudName;
    }


    private String getCloudName() {
        return cloudName;
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

    public ECSSlaveHelper getHelper() {
        return helper;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new ECSComputerImpl(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException { helper._terminate(listener); }

    @Override
    public ECSCloud getCloud() {
        Cloud cloud = Jenkins.get().getCloud(getCloudName());
        if (cloud instanceof ECSCloud) {
            return (ECSCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + ECSCloud.class.getName());
        }
    }

    public static ECSSlaveImpl.Builder builder() {
        return new ECSSlaveImpl.Builder();
    }

    @Override
    public String toString() {
        return String.format("ECSSlave name: %s", name);
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
            return new ECSSlaveImpl(name == null ? ECSSlaveHelper.getSlaveName(ecsTaskTemplate) : name,
                    ecsTaskTemplate,
                    nodeDescription == null ? ecsTaskTemplate.getTemplateName() : nodeDescription,
                    cloud.name,
                    label == null ? ecsTaskTemplate.getLabel() : label,
                    computerLauncher == null ? new ECSLauncher() : computerLauncher,
                    new ECSRetentionStrategy(ecsTaskTemplate.isSingleRunTask(), ecsTaskTemplate.getIdleTerminationMinutes()));
        }
    }
}