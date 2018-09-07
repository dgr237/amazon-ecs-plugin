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

public interface ECSInitializingSlavesResolver {
    /**
     * Returns the agents in provisioning for the current label.
     *
     * @param label The label being checked
     * @return The agents names in provisioning for the current label.
     */
    Set<String> getInitializingECSSlaves(@CheckForNull Label label);
}