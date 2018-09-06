package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class DefaultInProvisioning extends InProvisioning {
    private static final Logger LOGGER = Logger.getLogger(DefaultInProvisioning.class.getName());

    private static boolean isNotAcceptingTasks(Node n) {
        ECSSlave slave=(ECSSlave) n;

        return slave==null || slave.getTaskState()!= ECSSlave.State.Running;
    }

    @Override
    @Nonnull
    public Set<String> getInProvisioning(@CheckForNull Label label) {
        if (label != null) {
            return label.getNodes().stream()
                    .filter(ECSSlave.class::isInstance)
                    .filter(DefaultInProvisioning::isNotAcceptingTasks)
                    .map(Node::getNodeName)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }
}