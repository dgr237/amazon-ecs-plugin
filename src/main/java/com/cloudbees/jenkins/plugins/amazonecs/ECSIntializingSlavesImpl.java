package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.Extension;
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

@Extension
public class ECSIntializingSlavesImpl extends ECSInitializingSlaves {
    private static final Logger LOGGER = Logger.getLogger(ECSIntializingSlavesImpl.class.getName());

    private static final Set<ECSSlave.State> initializingStates=new HashSet<>(Arrays.asList(ECSSlave.State.Initializing, ECSSlave.State.TaskDefinitionCreated, ECSSlave.State.TaskLaunched, ECSSlave.State.TaskCreated));

    private static boolean isInitialising(Node n) {
        ECSSlave slave=(ECSSlave) n;
        if(slave==null)
            return false;
        else
            return initializingStates.contains(slave.getTaskState());
    }

    @Override
    @Nonnull
    public Set<String> getInitializingECSSlaves(@CheckForNull Label label) {
        if (label != null) {
            return label.getNodes().stream()
                    .filter(ECSSlave.class::isInstance)
                    .filter(ECSIntializingSlavesImpl::isInitialising)
                    .map(Node::getNodeName)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }
}