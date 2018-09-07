package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Label;
import hudson.model.Node;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ECSInitializingSlavesResolverImpl implements ECSInitializingSlavesResolver {

    private static final Logger LOGGER = Logger.getLogger(ECSInitializingSlavesResolverImpl.class.getName());


    private static final Set<ECSSlaveImpl.State> initializingStates = new HashSet<>(Arrays.asList(ECSSlaveImpl.State.Initializing, ECSSlaveImpl.State.TaskDefinitionCreated, ECSSlaveImpl.State.TaskLaunched, ECSSlaveImpl.State.TaskCreated));

    private static boolean isInitialising(Node n) {
        ECSSlaveImpl slave = (ECSSlaveImpl) n;
        if (slave == null)
            return false;
        else
            return initializingStates.contains(slave.getTaskState());
    }

    /**
     * Returns the agents in provisioning for the current label.
     *
     * @param label The label being checked
     * @return The agents names in provisioning for the current label.
     */
    @Nonnull
    public Set<String> getInitializingECSSlaves(@CheckForNull Label label) {
        if (label != null) {
            return label.getNodes().stream()
                    .filter(ECSSlaveImpl.class::isInstance)
                    .filter(ECSInitializingSlavesResolverImpl::isInitialising)
                    .map(Node::getNodeName)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }
}