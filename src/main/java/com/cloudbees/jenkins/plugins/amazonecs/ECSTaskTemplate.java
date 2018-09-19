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

import com.amazonaws.services.ecs.model.*;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.ObjectUtils;
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
public class ECSTaskTemplate extends AbstractDescribableImpl<ECSTaskTemplate> {
    private static final Logger LOGGER = Logger.getLogger(ECSTaskTemplate.class.getName());
    private static final int DEFAULT_LAUNCH_TIMEOUT=600;
    /**
     * Template Name
     */
    private String templateName;
    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see Label
     */
    @CheckForNull
    private String label;

    /**
     * Task Definition Override to use, instead of a Jenkins-managed Task definition. May be a family name or an ARN.
     */
    @CheckForNull
    private String taskDefinitionOverride;

    /**
     * Docker image
     *
     * @see ContainerDefinition#withImage(String)
     */
    private String image;
    /**
     * Slave remote FS
     */
    @Nullable
    private String remoteFSRoot;
    /**
     * The number of MiB of memory reserved for the Docker container. If your
     * container attempts to exceed the memory allocated here, the container
     * is killed by ECS.
     *
     * @see ContainerDefinition#withMemory(Integer)
     */
    private int memory;
    /**
     * The soft limit (in MiB) of memory to reserve for the container. When
     * system memory is under contention, Docker attempts to keep the container
     * memory to this soft limit; however, your container can consume more
     * memory when it needs to, up to either the hard limit specified with the
     * memory parameter (if applicable), or all of the available memory on the
     * container instance, whichever comes first.
     *
     * @see ContainerDefinition#withMemoryReservation(Integer)
     */
    private int memoryReservation;



    /**
     * The number of <code>cpu</code> units reserved for the container. A
     * container instance has 1,024 <code>cpu</code> units for every CPU
     * core. This parameter specifies the minimum amount of CPU to reserve
     * for a container, and containers share unallocated CPU units with other
     * containers on the instance with the same ratio as their allocated
     * amount.
     *
     * @see ContainerDefinition#withCpu(Integer)
     */
    private int cpu;

    /**
     * Subnets to be assigned on the awsvpc network when using Fargate
     *
     * @see AwsVpcConfiguration#setSubnets(Collection)
     */
    private String subnets;

    /**
     * Security groups to be assigned on the awsvpc network when using Fargate
     *
     * @see AwsVpcConfiguration#setSecurityGroups(Collection)
     */
    private String securityGroups;

    /**
     * Assign a public Ip to instance on awsvpc network when using Fargate
     *
     * @see AwsVpcConfiguration#setAssignPublicIp(String)
     */
    private boolean assignPublicIp;

    /**
     * Space delimited list of Docker dns search domains
     *
     * @see ContainerDefinition#withDnsSearchDomains(Collection)
     */
    @CheckForNull
    private String dnsSearchDomains;

    /**
     * Space delimited list of Docker entry points
     *
     * @see ContainerDefinition#withEntryPoint(String...)
     */
    @CheckForNull
    private String entrypoint;

    /**
     * ARN of the IAM role to use for the slave ECS task
     *
     * @see RegisterTaskDefinitionRequest#withTaskRoleArn(String)
     */
    @CheckForNull
    private String taskrole;

    /**
     * ARN of the IAM role to use for the slave ECS task
     *
     * @see RegisterTaskDefinitionRequest#withExecutionRoleArn(String)
     */
    @CheckForNull
    private String executionRole;

    /**
     * JVM arguments to start slave.jar
     */
    @CheckForNull
    private String jvmArgs;

    /**
     * Container mount points, imported from volumes
     */
    private final List<MountPointEntry> mountPoints;

    /**
     * Task launch type
     */
    @Nonnull
    private String launchType;

    /**
     * Indicates whether the container should run in privileged mode
     */
    private boolean privileged;

    /**
     * User for conatiner
     */
    @Nullable
    private String containerUser;
    private int idleTerminationMinutes;
    private int slaveLaunchTimeoutSeconds;
    private boolean singleRunTask;
    private final List<EnvironmentEntry> environments;
    private final List<ExtraHostEntry> extraHosts;
    private final List<PortMappingEntry> portMappings;

    /**
     * The log configuration specification for the container.
     * This parameter maps to LogConfig in the Create a container section of
     * the Docker Remote API and the --log-driver option to docker run.
     * Valid log drivers are displayed in the LogConfiguration data type.
     * This parameter requires version 1.18 of the Docker Remote API or greater
     * on your container instance. To check the Docker Remote API version on
     * your container instance, log into your container instance and run the
     * following command: sudo docker version | grep "Server API version"
     * The Amazon ECS container agent running on a container instance must
     * register the logging drivers available on that instance with the
     * ECS_AVAILABLE_LOGGING_DRIVERS environment variable before containers
     * placed on that instance can use these log configuration options.
     * For more information, see Amazon ECS Container Agent Configuration
     * in the Amazon EC2 Container Service Developer Guide.
     */
    @CheckForNull
    private String logDriver;
    private final List<LogDriverOption> logDriverOptions;

    @DataBoundConstructor
    public ECSTaskTemplate() {
        this.launchType=LaunchType.EC2.toString();
        // if the user enters a task definition override, always prefer to use it, rather than the jenkins template.
        this.logDriverOptions = new ArrayList<>();
        this.environments = new ArrayList<>();
        this.extraHosts = new ArrayList<>();
        this.mountPoints = new ArrayList<>();
        this.portMappings = new ArrayList<>();
        this.slaveLaunchTimeoutSeconds=DEFAULT_LAUNCH_TIMEOUT;
    }

    //region taskDefinitionOverride
    public String getTaskDefinitionOverride() {
        return taskDefinitionOverride;
    }

    @DataBoundSetter
    public void setTaskDefinitionOverride(String taskDefinitionOverride) {
        String taskDefOverride = StringUtils.trimToNull(taskDefinitionOverride);
        if (!StringUtils.isBlank(taskDefOverride)) {
            this.taskDefinitionOverride = taskDefOverride;
            // Always set the template name to the empty string if we are using a task definition override,
            // since we don't want Jenkins to touch our definitions.
            this.templateName = "";
        } else {
            // If the template name is empty we will add a default name and a
            // random element that will help to find it later when we want to delete it.
            this.templateName = this.templateName.isEmpty() ?
                    "jenkinsTask-" + UUID.randomUUID().toString() : this.templateName;
            // Make sure we don't have both a template name and a task definition override.
            this.taskDefinitionOverride = null;
        }
    }

    public ECSTaskTemplate withTaskDefinitionOverride(String taskDefinitionOverride) {
        setTaskDefinitionOverride(taskDefinitionOverride);
        return this;
    }
    //endregion

    //region Taskrole
    public String getTaskrole() {
        return taskrole;
    }

    @DataBoundSetter
    public void setTaskrole(String taskRoleArn) {
        this.taskrole = StringUtils.trimToNull(taskRoleArn);
    }

    public ECSTaskTemplate withTaskRole(String taskRoleArn) {
        setTaskrole(taskRoleArn);
        return this;
    }
    //endregion

    //region executionRole
    public String getExecutionRole() {
        return executionRole;
    }

    @DataBoundSetter
    public void setExecutionRole(String executionRole) {
        this.executionRole = StringUtils.trimToNull(executionRole);
    }

    public ECSTaskTemplate withExecutionRole(String executionRole) {
        setExecutionRole(executionRole);
        return this;
    }
    //endregion

    //region entrypoint
    public String getEntrypoint() {
        return entrypoint;
    }

    @DataBoundSetter
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = StringUtils.trimToNull(entrypoint);
    }

    public ECSTaskTemplate withEntrypoint(String entrypoint) {
        setEntrypoint(entrypoint);
        return this;
    }

    //endregion

    //region jvmArgs
    public String getJvmArgs() {
        return jvmArgs;
    }

    @DataBoundSetter
    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = StringUtils.trimToNull(jvmArgs);
    }

    public ECSTaskTemplate withJvmArgs(String jvmArgs) {
        setJvmArgs(jvmArgs);
        return this;
    }
    //endregion

    //region containerUser
    public String getContainerUser() {
        return containerUser;
    }

    @DataBoundSetter
    public void setContainerUser(String containerUser) {
        this.containerUser = StringUtils.trimToNull(containerUser);
    }

    public ECSTaskTemplate withContainerUser(String containerUser) {
        setContainerUser(containerUser);
        return this;
    }
    //endregion

    //region logDriver
    public String getLogDriver() {
        return logDriver;
    }

    @DataBoundSetter
    public void setLogDriver(String logDriver) {
        this.logDriver = StringUtils.trimToNull(logDriver);
    }

    public ECSTaskTemplate withLogDriver(String logDriver) {
        setLogDriver(logDriver);
        return this;
    }
    //endregion

    //region subnets
    public String getSubnets() {
        return subnets;
    }

    @DataBoundSetter
    public void setSubnets(String subnets) {
        this.subnets = StringUtils.trimToNull(subnets);
    }

    public ECSTaskTemplate withSubnets(String subnets) {
        setSubnets(subnets);
        return this;
    }
    //endregion

    //region SecurityGroups
    public String getSecurityGroups() {
        return securityGroups;
    }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) {
        this.securityGroups = StringUtils.trimToNull(securityGroups);
    }

    public ECSTaskTemplate withSecurityGroups(String securityGroups) {
        setSecurityGroups(securityGroups);
        return this;
    }
    //endregion

    //region dnsSearchDomains
    public String getDnsSearchDomains() {
        return dnsSearchDomains;
    }

    @DataBoundSetter
    public void setDnsSearchDomains(String dnsSearchDomains) {
        this.dnsSearchDomains = StringUtils.trimToNull(dnsSearchDomains);
    }

    public ECSTaskTemplate withDnsSearchDomains(String dnsSearchDomains) {
        setDnsSearchDomains(dnsSearchDomains);
        return this;
    }
    //endregion

    //region privileged
    public boolean getPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    public ECSTaskTemplate withPrivileged(boolean privileged) {
        setPrivileged(privileged);
        return this;
    }
    //endregion

    //region idleTerminationMinutes
    public int getIdleTerminationMinutes() {
        return idleTerminationMinutes;
    }

    @DataBoundSetter
    public void setIdleTerminationMinutes(int idleTerminationMinutes) {
        this.idleTerminationMinutes = idleTerminationMinutes;
    }

    public ECSTaskTemplate withIdleTerminationMinutes(int idleTerminationMinutes) {
        setIdleTerminationMinutes(idleTerminationMinutes);
        return this;
    }
    //endregion

    //region singleRunTask
    public boolean isSingleRunTask() {
        return singleRunTask;
    }

    @DataBoundSetter
    public void setSingleRunTask(boolean singleRunTask) {
        this.singleRunTask = singleRunTask;
    }

    public ECSTaskTemplate withSingleRunTask(boolean singleRunTask) {
        setSingleRunTask(singleRunTask);
        return this;
    }
    //endregion

    //region label
    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public ECSTaskTemplate withLabel(String label) {
        setLabel(label);
        return this;
    }
    //endregion

    //region Template Name
    public String getTemplateName() {
        return templateName;
    }

    @DataBoundSetter
    public void setTemplateName(String templateName)
    {
        this.templateName=templateName;
    }

    public ECSTaskTemplate withTemplateName(String templateName) {
        setTemplateName(templateName);
        return this;
    }
    //endregion

    //region image
    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image=image;
    }

    public ECSTaskTemplate withImage(String image) {
        setImage(image);
        return this;
    }
    //endregion

    //region remoteFSRoot
    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    @DataBoundSetter
    public void setRemoteFSRoot(String remoteFSRoot) {
        this.remoteFSRoot = remoteFSRoot;
    }

    public ECSTaskTemplate withRemoteFSRoot(String remoteFSRoot) {
        setRemoteFSRoot(remoteFSRoot);
        return this;
    }
    //endregion

    //region memory
    public int getMemory() {
        return memory;
    }

    @DataBoundSetter
    public void setMemory(int memory) {
        this.memory=memory;
    }

    public ECSTaskTemplate withMemory(int memory) {
        setMemory(memory);
        return this;
    }
    //endregion

    //region memoryReservation
    public int getMemoryReservation() {
        return memoryReservation;
    }

    @DataBoundSetter
    public void setMemoryReservation(int memoryReservation) {
        this.memoryReservation=memoryReservation;
    }

    public ECSTaskTemplate withMemoryReservation(int memoryReservation) {
        setMemoryReservation(memoryReservation);
        return this;
    }

    /* a hint to ECSClient regarding whether it can ask AWS to make a new container or not */
    public int getMemoryConstraint() {
        return this.memoryReservation>0?this.memoryReservation:this.memory;
    }
    //endregion

    //region cpu
    public int getCpu() {
        return cpu;
    }

    @DataBoundSetter
    public void setCpu(int cpu) {
        this.cpu=cpu;
    }

    public ECSTaskTemplate withCpu(int cpu) {
        setCpu(cpu);
        return this;
    }
    //endregion

    //region assignPublicIP
    public boolean getAssignPublicIp() {
        return assignPublicIp;
    }

    @DataBoundSetter
    public void setAssignPublicIp(Boolean assignPublicIp)
    {
        this.assignPublicIp=assignPublicIp;
    }

    public ECSTaskTemplate withAssignPublicIp(Boolean assignPublicIp) {
        setAssignPublicIp(assignPublicIp);
        return this;
    }
    //endregion

    //region launchType
    public String getLaunchType() {
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(this.launchType), LaunchType.EC2.toString());
    }

    public void setLaunchType(String launchType) {
        this.launchType = StringUtils.defaultIfBlank(launchType, LaunchType.EC2.toString());
    }

    public ECSTaskTemplate withLaunchType(String launchType) {
        setLaunchType(launchType);
        return this;
    }

    public boolean isFargate() {
        return getLaunchType().equals(LaunchType.FARGATE.toString());
    }
    //endregion

    //region slaveLaunchTimeoutInSeconds
    public int getSlaveLaunchTimeoutSeconds() {
        return slaveLaunchTimeoutSeconds;
    }

    @DataBoundSetter
    public void setSlaveLaunchTimeoutSeconds(int slaveLaunchTimeoutSeconds) {
        int newValue=slaveLaunchTimeoutSeconds==0?DEFAULT_LAUNCH_TIMEOUT:slaveLaunchTimeoutSeconds;
        this.slaveLaunchTimeoutSeconds = newValue;
    }

    public ECSTaskTemplate withSlaveLaunchTimeoutSeconds(int slaveLaunchTimeoutSeconds)
    {
        setSlaveLaunchTimeoutSeconds(slaveLaunchTimeoutSeconds);
        return this;
    }

    //region LogDriverOption
    public List<LogDriverOption> getLogDriverOptions() {
        return new ArrayList<>(logDriverOptions);
    }

    @DataBoundSetter
    public void setLogDriverOptions(List<LogDriverOption> logDriverOptions) {
        this.logDriverOptions.clear();
        if(logDriverOptions!=null) {
            this.logDriverOptions.addAll(logDriverOptions);
        }
    }

    public ECSTaskTemplate withLogDriverOptions(List<LogDriverOption> logDriverOptions) {
        setLogDriverOptions(logDriverOptions);
        return this;
    }

    Map<String,String> getLogDriverOptionsMap() {
        if (null == logDriverOptions || logDriverOptions.isEmpty()) {
            return null;
        }
        Map<String,String> options = new HashMap<>();
        for (LogDriverOption logDriverOption : logDriverOptions) {
            String name = logDriverOption.name;
            String value = logDriverOption.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            options.put(name, value);
        }
        return options;
    }


    public static class LogDriverOption extends AbstractDescribableImpl<LogDriverOption>{
        private final String name;
        private final String value;

        @DataBoundConstructor
        public LogDriverOption(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "LogDriverOption{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<LogDriverOption> {
            @Override
            @Nonnull
            public String getDisplayName() {
                return "logDriverOption";
            }
        }
    }
    //endregion

    //region environments
    public List<EnvironmentEntry> getEnvironments() {
        return new ArrayList<>(environments);
    }

    public void setEnvironments(List<EnvironmentEntry> environments) {
        this.environments.clear();
        if(environments!=null) {
            this.environments.addAll(environments);
        }
    }

    public ECSTaskTemplate withEnvironments(List<EnvironmentEntry> environments)
    {
        setEnvironments(environments);
        return this;
    }

    Collection<KeyValuePair> getEnvironmentKeyValuePairs() {
        if (null == environments || environments.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<KeyValuePair> items = new ArrayList<>();
        for (EnvironmentEntry environment : environments) {
            String name = environment.name;
            String value = environment.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            items.add(new KeyValuePair().withName(name).withValue(value));
        }
        return items;
    }

    public static class EnvironmentEntry extends AbstractDescribableImpl<EnvironmentEntry> {
        private final String name;
        private final String value;

        @DataBoundConstructor
        public EnvironmentEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "EnvironmentEntry{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<EnvironmentEntry> {
            @Override
            @Nonnull
            public String getDisplayName() {
                return "EnvironmentEntry";
            }
        }
    }
    //endregion

    //region extraHosts
    public List<ExtraHostEntry> getExtraHosts() {
        return new ArrayList<>(extraHosts);
    }

     @DataBoundSetter
     public void setExtraHosts(List<ExtraHostEntry> extraHosts) {
         this.extraHosts.clear();
         if (extraHosts != null) {
             this.extraHosts.addAll(extraHosts);
         }
     }

     public ECSTaskTemplate withExtraHosts(List<ExtraHostEntry> extraHosts) {
        setExtraHosts(extraHosts);
        return this;
     }

    Collection<HostEntry> getExtraHostEntries() {
        if (null == extraHosts || extraHosts.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<HostEntry> items = new ArrayList<>();
        for (ExtraHostEntry extrahost : extraHosts) {
            String ipAddress = extrahost.ipAddress;
            String hostname = extrahost.hostname;
            if (StringUtils.isEmpty(ipAddress) || StringUtils.isEmpty(hostname)) {
                continue;
            }
            items.add(new HostEntry().withIpAddress(ipAddress).withHostname(hostname));
        }
        return items;
    }

    public static class ExtraHostEntry extends AbstractDescribableImpl<ExtraHostEntry> {
        public final String ipAddress;
        public final String hostname;

        @DataBoundConstructor
        public ExtraHostEntry(String ipAddress, String hostname) {
            this.ipAddress = ipAddress;
            this.hostname = hostname;
        }

        @Override
        public String toString() {
            return "ExtraHostEntry{" + ipAddress + ": " + hostname + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ExtraHostEntry> {
            @Override
            @Nonnull
            public String getDisplayName() {
                return "ExtraHostEntry";
            }
        }
    }
    //endregion

    //region mountPoints
    public List<MountPointEntry> getMountPoints() {
        return new ArrayList<>(mountPoints);
    }

    @DataBoundSetter
    public void setMountPoints(List<MountPointEntry> mountPoints) {
        this.mountPoints.clear();
        if(mountPoints!=null) {
            this.mountPoints.addAll(mountPoints);
        }
    }

    public ECSTaskTemplate withMountPoints(List<MountPointEntry> mountPoints) {
        setMountPoints(mountPoints);
        return this;
    }

    Collection<Volume> getVolumeEntries() {
        Collection<Volume> vols = new LinkedList<>();
        if (null != mountPoints ) {
            for (MountPointEntry mount : mountPoints) {
                String name = mount.name;
                String sourcePath = mount.sourcePath;
                HostVolumeProperties hostVolume = new HostVolumeProperties();
                if (StringUtils.isEmpty(name)) {
                    continue;
                }
                if (! StringUtils.isEmpty(sourcePath)) {
                    hostVolume.setSourcePath(sourcePath);
                }
                vols.add(new Volume().withName(name)
                                     .withHost(hostVolume));
            }
        }
        return vols;
    }

    Collection<MountPoint> getMountPointEntries() {
        if (null == mountPoints || mountPoints.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<MountPoint> mounts = new ArrayList<>();
        for (MountPointEntry mount : mountPoints) {
            String src = mount.name;
            String path = mount.containerPath;
            Boolean ro = mount.readOnly;
            if (StringUtils.isEmpty(src) || StringUtils.isEmpty(path)) {
                continue;
            }
            mounts.add(new MountPoint().withSourceVolume(src)
                                       .withContainerPath(path)
                                       .withReadOnly(ro));
        }
        return mounts;
    }

    public static class MountPointEntry extends AbstractDescribableImpl<MountPointEntry> {
        private final String name;
        private final String sourcePath;
        private final String containerPath;
        private final Boolean readOnly;

        @DataBoundConstructor
        public MountPointEntry(String name,
                               String sourcePath,
                               String containerPath,
                               Boolean readOnly) {
            this.name = name;
            this.sourcePath = sourcePath;
            this.containerPath = containerPath;
            this.readOnly = readOnly;
        }

        @Override
        public String toString() {
            return "MountPointEntry{name:" + name +
                    ", sourcePath:" + sourcePath +
                    ", containerPath:" + containerPath +
                    ", readOnly:" + readOnly + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<MountPointEntry> {
            @Override
            @Nonnull
            public String getDisplayName() {
                return "MountPointEntry";
            }
        }
    }
    //endregion

    //region portMappings
    public List<PortMappingEntry> getPortMappings() {
        return new ArrayList<>(portMappings);
    }

    public void setPortMappings(List<PortMappingEntry> portMappings)
    {
        this.portMappings.clear();
        if(portMappings!=null) {
            this.portMappings.addAll(portMappings);
        }
    }

    public ECSTaskTemplate withPortMappings(List<PortMappingEntry> portMappings) {
        setPortMappings(portMappings);
        return this;
    }

    Collection<PortMapping> getPortMappingEntries() {
        if (null == portMappings || portMappings.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<PortMapping> ports = new ArrayList<>();
        for (PortMappingEntry portMapping : this.portMappings) {
            Integer container = portMapping.containerPort;
            Integer host = portMapping.hostPort;
            String protocol = portMapping.protocol;

            ports.add(new PortMapping().withContainerPort(container)
                                       .withHostPort(host)
                                       .withProtocol(protocol));
        }
        return ports;
    }

    public static class PortMappingEntry extends AbstractDescribableImpl<PortMappingEntry> {
        private final Integer containerPort;
        private final Integer hostPort;
        private final String protocol;

        @DataBoundConstructor
        public PortMappingEntry(Integer containerPort, Integer hostPort, String protocol) {
            this.containerPort = containerPort;
            this.hostPort = hostPort;
            this.protocol = protocol;
        }

        @Override
        public String toString() {
            return "PortMappingEntry{" +
                    "containerPort=" + containerPort +
                    ", hostPort=" + hostPort +
                    ", protocol='" + protocol + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PortMappingEntry> {
            public ListBoxModel doFillProtocolItems() {
                final ListBoxModel options = new ListBoxModel();
                options.add("TCP", "tcp");
                options.add("UDP", "udp");
                return options;
            }

            @Override
            @Nonnull
            public String getDisplayName() {
                return "PortMappingEntry";
            }
        }
    }

    //endregion

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "ECS Slave " + label;
    }

    ContainerDefinition buildContainerDefinition(String familyName) {
        final ContainerDefinition def = new ContainerDefinition()
                .withName(familyName)
                .withImage(image)
                .withEnvironment(getEnvironmentKeyValuePairs())
                .withExtraHosts(getExtraHostEntries())
                .withMountPoints(getMountPointEntries())
                .withPortMappings(getPortMappingEntries())
                .withCpu(getCpu())
                .withPrivileged(getPrivileged())
                .withEssential(true);

        /*
            at least one of memory or memoryReservation has to be set
            the form validation will highlight if the settings are inappropriate
        */
        /* this is the soft limit */
        if (getMemoryReservation() > 0) {
            def.withMemoryReservation(getMemoryReservation());
        }

        /* this is the hard limit */
        if (getMemory() > 0) {
            def.withMemory(getMemory());
        }

        if (getDnsSearchDomains() != null) {
            def.withDnsSearchDomains(StringUtils.split(getDnsSearchDomains()));
        }

        if (getEntrypoint() != null) {
            def.withEntryPoint(StringUtils.split(getEntrypoint()));
        }

        if (getJvmArgs() != null) {
            def.withEnvironment(new KeyValuePair()
                    .withName("JAVA_OPTS").withValue(getJvmArgs()))
                    .withEssential(true);
        }

        if (getContainerUser() != null) {
            def.withUser(getContainerUser());
        }

        if (getLogDriver() != null) {
            LogConfiguration logConfig = new LogConfiguration();
            logConfig.setLogDriver(getLogDriver());
            logConfig.setOptions(getLogDriverOptionsMap());
            def.withLogConfiguration(logConfig);
        }
        return def;
    }

    public RegisterTaskDefinitionRequest createRegisterTaskDefinitionRequestIfNotMatched(String familyName, TaskDefinition currentTaskDefinition) {
        ContainerDefinition def = buildContainerDefinition(familyName);

        boolean templateMatchesExistingContainerDefinition = false;
        boolean templateMatchesExistingVolumes = false;
        boolean templateMatchesExistingTaskRole = false;
        boolean templateMatchesExistingExecutionRole = false;

        if (currentTaskDefinition != null) {
            templateMatchesExistingContainerDefinition = def.equals(currentTaskDefinition.getContainerDefinitions().get(0));
            LOGGER.log(Level.INFO, "Match on container definition: {0}", new Object[]{templateMatchesExistingContainerDefinition});
            LOGGER.log(Level.FINE, "Match on container definition: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingContainerDefinition, def, currentTaskDefinition.getContainerDefinitions().get(0)});

            templateMatchesExistingVolumes = ObjectUtils.equals(getVolumeEntries(), currentTaskDefinition.getVolumes());
            LOGGER.log(Level.INFO, "Match on volumes: {0}", new Object[]{templateMatchesExistingVolumes});
            LOGGER.log(Level.FINE, "Match on volumes: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingVolumes, getVolumeEntries(), currentTaskDefinition.getVolumes()});

            templateMatchesExistingTaskRole = getTaskrole() == null || getTaskrole().equals(currentTaskDefinition.getTaskRoleArn());
            LOGGER.log(Level.INFO, "Match on task role: {0}", new Object[]{templateMatchesExistingTaskRole});
            LOGGER.log(Level.FINE, "Match on task role: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingTaskRole, getTaskrole(), currentTaskDefinition.getTaskRoleArn()});

            templateMatchesExistingExecutionRole = getExecutionRole() == null || getExecutionRole().equals(currentTaskDefinition.getExecutionRoleArn());
            LOGGER.log(Level.INFO, "Match on execution role: {0}", new Object[]{templateMatchesExistingExecutionRole});
            LOGGER.log(Level.FINE, "Match on execution role: {0}; template={1}; last={2}", new Object[]{templateMatchesExistingExecutionRole, getExecutionRole(), currentTaskDefinition.getExecutionRoleArn()});
        }

        boolean isMatch = templateMatchesExistingContainerDefinition && templateMatchesExistingVolumes && templateMatchesExistingTaskRole && templateMatchesExistingExecutionRole;
        if (isMatch) {
            LOGGER.log(Level.FINE, "Task Definition already exists: {0}", new Object[]{currentTaskDefinition.getTaskDefinitionArn()});

            return null;
        }

        return createRegisterTaskDefinitionRequest(familyName,def);
    }

    private RegisterTaskDefinitionRequest createRegisterTaskDefinitionRequest(String familyName, ContainerDefinition containerDefinition) {
        final RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
                .withFamily(familyName)
                .withVolumes(getVolumeEntries())
                .withContainerDefinitions(containerDefinition);

        if (isFargate()) {
            request
                    .withRequiresCompatibilities(getLaunchType())
                    .withNetworkMode("awsvpc")
                    .withMemory(String.valueOf(getMemoryConstraint()))
                    .withCpu(String.valueOf(getCpu()));
            if(!StringUtils.isEmpty(executionRole)){
                request.withExecutionRoleArn(executionRole);
            }
        }
        if (getTaskrole() != null) {
            request.withTaskRoleArn(getTaskrole());
        }
        return request;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSTaskTemplate> {

        private static final String TEMPLATE_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        private static final Map<Integer,Set<Integer>> VALID_MEMORY_SETTINGS_BY_CPU;

        static {
            VALID_MEMORY_SETTINGS_BY_CPU = new HashMap<>();
            VALID_MEMORY_SETTINGS_BY_CPU.put(256, new HashSet<>(Arrays.asList(512, 1024, 2048)));
            VALID_MEMORY_SETTINGS_BY_CPU.put(512, createSet(1024,4096,1024));
            VALID_MEMORY_SETTINGS_BY_CPU.put(1024, createSet(2048,8192,1024));
            VALID_MEMORY_SETTINGS_BY_CPU.put(2048, createSet(4096,16384,1024));
            VALID_MEMORY_SETTINGS_BY_CPU.put(4096, createSet(8192,30720,1024));
        }

        private static final String FARGATE="FARGATE";

        private static Set<Integer> createSet(int minValue, int maxValue, int increment)
        {
            Set<Integer> result=new HashSet<>();
            for (int value=minValue; value<=maxValue; value+=increment) {
                result.add(value);
            }
            return result;
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return Messages.Template();
        }

        public ListBoxModel doFillLaunchTypeItems() {
            final ListBoxModel options = new ListBoxModel();
            for (LaunchType launchType: LaunchType.values()) {
                options.add(launchType.toString());
            }
            return options;
        }

        public FormValidation doCheckTaskDefinitionOverride(@QueryParameter("taskDefinitionOverride") String taskArn, @QueryParameter("templateName") String templateName) {
            return validateTemplateDef(taskArn, templateName);
        }

        public FormValidation doCheckTemplateName(@QueryParameter("taskDefinitionOverride") String taskArn, @QueryParameter("templateName") String templateName) {
            return validateTemplateDef(taskArn,templateName);
        }


        public FormValidation validateTemplateDef(String taskArn, String templateName) {
            if (StringUtils.isEmpty(taskArn)) {
                if (StringUtils.isEmpty(templateName)) {
                    return FormValidation.error("Either the Task Definition ARN must be supplied or the Task Definition Creation Settings must be completed including the Template Name");
                } else {
                    if (templateName.length() > 0 && templateName.length() <= 127 && templateName.matches(TEMPLATE_NAME_PATTERN)) {
                        return FormValidation.ok();
                    }
                    return FormValidation.error("Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
                }
            } else {
                if (!taskArn.matches("arn:aws:ecs:[a-z0-9|-]+:[0-9]+:task\\/[a-z0-9|-]*")) {
                    return FormValidation.error("Task ARN is not valid");
                }
            }
            return FormValidation.ok();
        }


        public FormValidation doCheckSubnets(@QueryParameter("subnets") String subnets, @QueryParameter("launchType") String launchType) {
            if (FARGATE.equals(launchType)) {
                if (StringUtils.isEmpty(subnets)) {
                    return FormValidation.error("Subnets need to be set, when using FARGATE");
                }
                else
                {
                    String[] subNets=StringUtils.split(subnets,',');
                    for (String subNet: subNets) {
                        String current=subNet.trim();
                        if(!current.matches("subnet-[a-f0-9|-]*")) {
                            return FormValidation.error("Subnet: "+current+" is not a valid subnet");
                        }
                    }
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckSecurityGroups(@QueryParameter("securityGroups") String securityGroups, @QueryParameter("launchType") String launchType) {
            if (FARGATE.equals(launchType)) {
                if(StringUtils.isEmpty(securityGroups)) {
                    return FormValidation.error("SecurityGroups need to be set, when using FARGATE");
                }
                else
                {
                    String[] secGroups=StringUtils.split(securityGroups,',');
                    for (String secGroup: secGroups) {
                        String current=secGroup.trim();
                        if(!current.matches("sg-[a-f0-9|-]{17}")) {
                            return FormValidation.error("SecurityGroup: "+current+" is not a valid SecurityGroup");
                        }
                    }
                }
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckCpu(@QueryParameter("taskDefinitionOverride") String taskArn, @QueryParameter("launchType") String launchType, @QueryParameter("cpu") int cpu) {
            if(StringUtils.isEmpty(taskArn)) {
                if(FARGATE.equals(launchType)) {
                    if(!VALID_MEMORY_SETTINGS_BY_CPU.containsKey(cpu)) {
                        return FormValidation.error("For Fargate tasks: The valid CPU settings are: " + StringUtils.join(VALID_MEMORY_SETTINGS_BY_CPU.keySet().toArray(), ", "));
                    }
                }
                else {
                    if(cpu<128 || cpu> 10240) {
                        return FormValidation.error("For EC2 tasks: The CPU setting should be between 128 and 10240");
                    }
                }
            }
            return FormValidation.ok();
        }

        /* we validate both memory and memoryReservation fields to the same rules */
        public FormValidation doCheckMemory(@QueryParameter("memory") int memory, @QueryParameter("memoryReservation") int memoryReservation, @QueryParameter("taskDefinitionOverride") String taskArn, @QueryParameter("launchType") String launchType, @QueryParameter("cpu") int cpu) {
            if(StringUtils.isEmpty(taskArn)) {
                return validateMemorySettings(memory, memoryReservation, launchType,cpu);
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMemoryReservation(@QueryParameter("memory") int memory, @QueryParameter("memoryReservation") int memoryReservation, @QueryParameter("taskDefinitionOverride") String taskArn, @QueryParameter("launchType") String launchType, @QueryParameter("cpu") int cpu) {
            if (StringUtils.isEmpty(taskArn)) {
                return validateMemorySettings(memory, memoryReservation, launchType, cpu);
            }
            return FormValidation.ok();
        }

        private FormValidation validateMemorySettings(int memory, int memoryReservation, String launchType, int cpu) {
            if (memory < 0 || memoryReservation < 0) {
                return FormValidation.error("memory and/or memoryReservation must be 0 or a positive integer");
            }
            if (memory == 0 && memoryReservation == 0) {
                return FormValidation.error("at least one of memory or memoryReservation are required to be > 0");
            }

            if (memory > 0 && memoryReservation > 0 && memory <= memoryReservation) {
                return FormValidation.error("memory must be greater than memoryReservation if both are specified");
            }

            if (FARGATE.equals(launchType) && VALID_MEMORY_SETTINGS_BY_CPU.containsKey(cpu)) {
                int tempMemory = Math.max(memory, memoryReservation);
                Set<Integer> validMemorySettings = VALID_MEMORY_SETTINGS_BY_CPU.get(cpu);
                if (!validMemorySettings.contains(tempMemory)) {
                    return FormValidation.error("For Fargate tasks: The valid Memory settings for CPU: " + cpu + " are: " + StringUtils.join(validMemorySettings.toArray(), ", "));
                }
            }

            return FormValidation.ok();
        }
    }
}
