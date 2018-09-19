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

import com.cloudbees.jenkins.plugins.amazonecs.retentionstrategy.ECSRetentionStrategy;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.*;
import org.apache.commons.lang.Validate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * This helper should only handle a single task and then be shutdown.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
class ECSSlaveImpl extends AbstractCloudSlave implements ECSSlave {

    private static final long serialVersionUID = -3167989896315283037L;
    private transient ECSSlaveHelper helper;
    private final String cloudName;

    private ECSSlaveImpl(String name, ECSTaskTemplate template, String nodeDescription, String cloudName, String labelStr,
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
    protected void _terminate(TaskListener listener) throws IOException { helper.terminate(); }

    @Override
    public ECSCloud getCloud() {
        Cloud cloud = JenkinsWrapper.getInstance().getCloud(getCloudName());
        if (cloud instanceof ECSCloud) {
            return (ECSCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + ECSCloud.class.getName());
        }
    }

    static ECSSlaveImpl.Builder builder() {
        return new ECSSlaveImpl.Builder();
    }

    @Override
    public String toString() {
        return String.format("ECSSlave name: %s", name);
    }


    public static class Builder {

        private ECSTaskTemplate ecsTaskTemplate;
        private ECSCloud cloud;


        Builder ecsTaskTemplate(ECSTaskTemplate ecsTaskTemplate) {
            this.ecsTaskTemplate = ecsTaskTemplate;
            return this;
        }

        Builder cloud(ECSCloud ecsCloud) {
            this.cloud = ecsCloud;
            return this;
        }


        ECSSlaveImpl build() throws IOException, Descriptor.FormException {
            Validate.notNull(ecsTaskTemplate);
            Validate.notNull(cloud);
            return new ECSSlaveImpl(ECSSlaveHelper.getSlaveName(ecsTaskTemplate),
                    ecsTaskTemplate,
                    ecsTaskTemplate.getTemplateName(),
                    cloud.name,
                    ecsTaskTemplate.getLabel(),
                    new ECSLauncher(false),
                    new ECSRetentionStrategy(ecsTaskTemplate.isSingleRunTask(), ecsTaskTemplate.getIdleTerminationMinutes()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false;}
        if (!super.equals(o)) {return false;}
        ECSSlaveImpl ecsSlave = (ECSSlaveImpl) o;
        return cloudName.equals(ecsSlave.cloudName) && super.equals(ecsSlave);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), cloudName);
    }
}