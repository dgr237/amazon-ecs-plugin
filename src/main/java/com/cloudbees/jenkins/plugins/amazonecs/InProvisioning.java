package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public abstract class InProvisioning implements ExtensionPoint {
    /**
     * Returns the agents names in provisioning according to all implementations of this extension point for the given label.
     *
     * @param label the {@link Label} being checked.
     * @return the agents names in provisioning according to all implementations of this extension point for the given label.
     */
    @Nonnull
    static Set<String> getAllInProvisioning(@CheckForNull Label label) {
        return all().stream()
                .flatMap(c -> c.getInProvisioning(label).stream())
                .collect(toSet());
    }

    private static ExtensionList<InProvisioning> all() {
        return ExtensionList.lookup(InProvisioning.class);
    }

    /**
     * Returns the agents in provisioning for the current label.
     *
     * @param label The label being checked
     * @return The agents names in provisioning for the current label.
     */
    @Nonnull
    abstract Set<String> getInProvisioning(Label label);
}