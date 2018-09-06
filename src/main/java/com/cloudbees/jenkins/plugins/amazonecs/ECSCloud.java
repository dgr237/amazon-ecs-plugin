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

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private static final int DEFAULT_SLAVE_TIMEOUT = 900;
    private static final int DEFAULT_MAX_SLAVES = 10;


    /**
     * Id of the {@link AmazonWebServicesCredentials} used to connect to Amazon ECS
     */
    @Nonnull
    private final String credentialsId;
    private final String cluster;
    private final List<ECSTaskTemplate> templates;
    private String regionName;

    /**
     * Tunnel connection through
     */
    @CheckForNull
    private String tunnel;
    private String jenkinsUrl;
    private int slaveTimoutInSeconds;
    private int maxSlaves;

    private ECSService ecsService;

    @DataBoundConstructor
    public ECSCloud(String name, List<ECSTaskTemplate> templates, @Nonnull String credentialsId,
            String cluster, String regionName, String jenkinsUrl, int slaveTimoutInSeconds) {
        super(name);
        this.credentialsId = credentialsId;
        this.cluster = cluster;
        this.templates = templates;
        this.regionName = regionName;
        LOGGER.log(Level.INFO, "Create cloud {0} on ECS cluster {1} on the region {2}", new Object[]{name, cluster, regionName});

        if (StringUtils.isNotBlank(jenkinsUrl)) {
            this.jenkinsUrl = jenkinsUrl;
        } else {
            this.jenkinsUrl = JenkinsLocationConfiguration.get().getUrl();
        }

        if (slaveTimoutInSeconds > 0) {
            this.slaveTimoutInSeconds = slaveTimoutInSeconds;
        } else {
            this.slaveTimoutInSeconds = DEFAULT_SLAVE_TIMEOUT;
        }

        this.maxSlaves = DEFAULT_MAX_SLAVES;
    }

    synchronized void init(ECSService service)
    {
        this.ecsService=service;
    }

    private List<ECSComputer> getCurrentComputers() {
        Computer[] all = Jenkins.get().getComputers();
        List<ECSComputer> runningECSComputers = new ArrayList<>();
        for (Computer computer : all) {
            if (computer instanceof ECSComputer) {
                ECSComputer ecsComputer = (ECSComputer) computer;
                if (ecsComputer.isConnecting() || ecsComputer.isAcceptingTasks()) {
                    runningECSComputers.add(ecsComputer);
                }
            }
        }
        return runningECSComputers;
    }

    private boolean waitForSufficientClusterResources(int timeoutSeconds, ECSTaskTemplate template) {
        return getEcsService().areSufficientClusterResourcesAvailable(timeoutSeconds, template, getCluster());
    }


    synchronized ECSService getEcsService() {
        if (ecsService == null) {
            ecsService = new ECSServiceImpl(credentialsId, regionName);
        }
        return ecsService;
    }

    @Nonnull
    public List<ECSTaskTemplate> getTemplates() {
        return templates != null ? templates : Collections.emptyList();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getCluster() {
        return cluster;
    }


    public String getTunnel() {
        return tunnel;
    }

    @DataBoundSetter
    public void setTunnel(String tunnel) {
        this.tunnel = tunnel;
    }

    public int getSlaveTimoutInSeconds() {
        return slaveTimoutInSeconds;
    }

    public void setSlaveTimoutInSeconds(int slaveTimoutInSeconds) {
        this.slaveTimoutInSeconds = slaveTimoutInSeconds;
    }

    public int getMaxSlaves() {
        return maxSlaves;
    }

    @DataBoundSetter
    public void setMaxSlaves(int maxSlaves) {
        this.maxSlaves = maxSlaves;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }


    @CheckForNull
    private static AmazonWebServicesCredentials getCredentials(@Nullable String credentialsId) {
        return AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.get());
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    private ECSTaskTemplate getTemplate(Label label) {
        if (label == null) {
            return null;
        }
        for (ECSTaskTemplate t : getTemplates()) {
            if (label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
    }


    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            Set<String> allInProvisioning = ECSInitializingSlaves.getAllInitializingECSSlaves(label);
            LOGGER.log(Level.FINE, () -> "Excess Workload : " + excessWorkload);
            LOGGER.log(Level.FINE, () -> "Initializing ECS Agents : " + allInProvisioning.size());
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            LOGGER.log(Level.INFO, "Excess workload after pending ECS agents: " + toBeProvisioned);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            final ECSTaskTemplate template = getTemplate(label);

            for (int i = 1; i <= toBeProvisioned; i++) {
                if (!checkIfAdditionalSlaveCanBeProvisioned(template, label)) {
                    break;
                }
				LOGGER.log(Level.INFO, "Will provision {0}, for label: {1}", new Object[]{template.getDisplayName(), label} );

                r.add(new NodeProvisioner.PlannedNode(template.getDisplayName(), Computer.threadPoolForRemoting
                  .submit(new ProvisioningCallback(this, template)), 1));
            }
            return r;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to provision ECS slave", e);
            return Collections.emptyList();
        }
    }

    private boolean checkIfAdditionalSlaveCanBeProvisioned(ECSTaskTemplate template, Label label) {
        List<String> allRunningTasks = getEcsService().getRunningTasks(this);
        LOGGER.log(Level.INFO, "ECS Slaves Initializing/ Running: {0}", allRunningTasks.size());
        if (allRunningTasks.size() >= maxSlaves) {
            LOGGER.log(Level.INFO, "ECS Slaves Initializing/ Running: {0}, exceeds max Slaves: {1}", new Object[]{allRunningTasks.size(), maxSlaves});
            return false;
        }
        return template.isFargate() || waitForSufficientClusterResources(1000 * slaveTimoutInSeconds, template);
    }

    void deleteTask(String taskArn) {
         getEcsService().deleteTask(taskArn, cluster);
     }



    public static Region getRegion(String regionName) {
        if (StringUtils.isNotEmpty(regionName)) {
            return RegionUtils.getRegion(regionName);
        } else {
            return Region.getRegion(Regions.US_EAST_1);
        }
    }



    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        private static final String CLOUD_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.get());
        }

        public ListBoxModel doFillRegionNameItems() {
            final ListBoxModel options = new ListBoxModel();
            for (Region region : RegionUtils.getRegions()) {
                options.add(region.getName());
            }
            return options;
        }

        public ListBoxModel doFillClusterItems(@QueryParameter String credentialsId, @QueryParameter String regionName) {
            ECSService ecsService = new ECSServiceImpl(credentialsId, regionName);
            try {
                List<String> allClusterArns=ecsService.getClusterArns();
                final ListBoxModel options = new ListBoxModel();
                for (final String arn : allClusterArns) {
                    options.add(arn);
                }
                return options;
            } catch (AmazonClientException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName + ":" + e);
                LOGGER.log(Level.FINE, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName, e);
                return new ListBoxModel();
            } catch (RuntimeException e) {
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName, e);
                return new ListBoxModel();
            }
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            if (value.length() > 0 && value.length() <= 127 && value.matches(CLOUD_NAME_PATTERN)) {
                return FormValidation.ok();
            }
            return FormValidation.error("Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
        }

    }

}
