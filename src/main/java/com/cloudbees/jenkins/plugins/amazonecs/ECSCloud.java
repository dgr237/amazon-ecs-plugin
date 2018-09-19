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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.cloudbees.jenkins.plugins.amazonecs.credentials.ECSCredentialsHelper;
import com.cloudbees.jenkins.plugins.amazonecs.credentials.ECSCredentials;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(ECSCloud.class.getName());

    private static final int DEFAULT_SLAVE_TIMEOUT = 900;
    private static final int DEFAULT_MAX_SLAVES = 0;
    /**
     * Id of the {@link ECSCredentials} used to connect to Amazon ECS
     */
    private String credentialsId;
    private final String cluster;
    private final List<ECSTaskTemplate> templates;
    private final String regionName;
    private String tunnel;
    private String jenkinsUrl;
    private int slaveTimoutInSeconds;
    private int maxSlaves;

    @DataBoundConstructor
    public ECSCloud(String name, String cluster, String regionName) {
        super(name);
        this.cluster = cluster;
        this.regionName = regionName;
        this.templates = new ArrayList<>();
        this.maxSlaves = DEFAULT_MAX_SLAVES;
        this.slaveTimoutInSeconds=DEFAULT_SLAVE_TIMEOUT;
        LOGGER.log(Level.INFO, "Create cloud {0} on ECS cluster {1} on the region {2}", new Object[]{name, cluster, regionName});
    }

    ECSService getEcsService() {
        return JenkinsWrapper.getECSService(credentialsId, regionName);
    }

    private ECSInitializingSlavesResolver initializingSlavesResolver() {
        return new ECSInitializingSlavesResolver();
    }

    public String getRegionName() {
        return regionName;
    }

    public String getCluster() {
        return cluster;
    }

    //region Templates
    @Nonnull
    public List<ECSTaskTemplate> getTemplates() {
        return templates != null ? templates : Collections.emptyList();
    }

    @DataBoundSetter
    public void setTemplates(List<ECSTaskTemplate> templates)
    {
        this.templates.clear();
        this.templates.addAll(templates);
    }

    public ECSCloud withTemplates(ECSTaskTemplate... templates)
    {
        setTemplates(Arrays.asList(templates));
        return this;
    }
    //endregion

    //region CredentialsId
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId)
    {
        this.credentialsId=credentialsId;
    }

    public ECSCloud withCredentialsId(String credentialsId)
    {
        setCredentialsId(credentialsId);
        return this;
    }
    //endregion

    //region Tunnel
    public String getTunnel() {
        return tunnel;
    }

    @DataBoundSetter
    public void setTunnel(String tunnel) {
        this.tunnel = tunnel;
    }

    public ECSCloud withTunnel(String tunnel)
    {
        setTunnel(tunnel);
        return this;
    }
    //endregion

    //region SlaveTimeoutInSeconds
    public int getSlaveTimoutInSeconds() {
        return slaveTimoutInSeconds;
    }

    @DataBoundSetter
    public void setSlaveTimoutInSeconds(int slaveTimoutInSeconds) {
        if (slaveTimoutInSeconds > 0) {
            this.slaveTimoutInSeconds = slaveTimoutInSeconds;
        } else {
            this.slaveTimoutInSeconds = DEFAULT_SLAVE_TIMEOUT;
        }
    }

    public ECSCloud withSlaveTimeoutInSeconds(int slaveTimeoutInSeconds)
    {
        setSlaveTimoutInSeconds(slaveTimeoutInSeconds);
        return this;
    }
    //endregion

    //region MaxSlaves
    public int getMaxSlaves() {
        return maxSlaves;
    }

    @DataBoundSetter
    public void setMaxSlaves(int maxSlaves) {
        this.maxSlaves = maxSlaves;
    }

    public ECSCloud withMaxSlaves(int maxSlaves)
    {
        setMaxSlaves(maxSlaves);
        return this;
    }
    //endregion

    // region Jenkins URl
    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        if (StringUtils.isNotBlank(jenkinsUrl)) {
            this.jenkinsUrl = jenkinsUrl;
        } else {
            JenkinsLocationConfiguration config = JenkinsWrapper.getJenkinsLocationConfiguration();
            if (config != null) {
                this.jenkinsUrl = config.getUrl();
            }
            else {
                this.jenkinsUrl = "";
            }
        }
    }

    public ECSCloud withJenkinsUrl(String jenkinsUrl)
    {
        setJenkinsUrl(jenkinsUrl);
        return this;
    }
    //endregion

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    public ECSTaskTemplate getTemplate(Label label) {
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
            Set<String> allInProvisioning = initializingSlavesResolver().getInitializingECSSlaves(label);
            LOGGER.log(Level.FINE, () -> "Excess Workload : " + excessWorkload);
            LOGGER.log(Level.FINE, () -> "INITIALIZING ECS Agents : " + allInProvisioning.size());
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            LOGGER.log(Level.INFO, "Excess workload after pending ECS agents: {0}", toBeProvisioned);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            final ECSTaskTemplate template = getTemplate(label);

            for (int i = 1; i <= toBeProvisioned; i++) {
                if (!getEcsService().checkIfAdditionalSlaveCanBeProvisioned(cluster, template, maxSlaves)) {
                    break;
                }
                LOGGER.log(Level.INFO, "Will provision {0}, for label: {1}", new Object[]{template.getDisplayName(), label});

                r.add(new NodeProvisioner.PlannedNode(template.getDisplayName(), Computer.threadPoolForRemoting
                        .submit(new ProvisioningCallback(this, template)), 1));
            }
            return r;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to provision ECS slave", e);
            return Collections.emptyList();
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        private static final String CLOUD_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        @Nonnull
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return ECSCredentialsHelper.doFillCredentialsIdItems(JenkinsWrapper.getInstance());
        }

        public ListBoxModel doFillRegionNameItems() {
            final ListBoxModel options = new ListBoxModel();
            for (Region region : RegionUtils.getRegions()) {
                options.add(region.getName());
            }
            return options;
        }

        public ListBoxModel doFillClusterItems(@QueryParameter String credentialsId, @QueryParameter String regionName) {
            ECSService ecsClient = new ECSService(credentialsId, regionName);
            try {
                List<String> allClusterArns = ecsClient.getClusterArns();
                final ListBoxModel options = new ListBoxModel();
                for (final String arn : allClusterArns) {
                    options.add(arn);
                }
                return options;
            } catch (RuntimeException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialsId + ", regionName=" + regionName + ":" + e);
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
