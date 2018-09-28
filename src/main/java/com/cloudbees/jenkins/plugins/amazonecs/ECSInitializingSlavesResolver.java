package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Label;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.cloudbees.jenkins.plugins.amazonecs.ECSSlaveHelper.*;


class ECSInitializingSlavesResolver {

    private static final Set<State> initializingStates = new HashSet<>(Arrays.asList(State.INITIALIZING, State.TASK_DEFINITION_CREATED, State.TASK_LAUNCHED, State.TASK_CREATED));

    /**
     * Returns the agents in provisioning for the current label.
     *
     * @param label The label being checked
     * @return The agents names in provisioning for the current label.
     */
    @Nonnull
    Set<String> getInitializingECSSlaves(@CheckForNull Label label) {
        Set<String> result=new HashSet<>();
        if (label != null) {

            Object[] nodes = getNodes(label);
            for (Object node : nodes) {
                if (ECSSlave.class.isInstance(node)) {
                    ECSSlave slave = (ECSSlave) node;
                    if (initializingStates.contains(slave.getHelper().getTaskState())) {
                        result.add(slave.getNodeName());
                    }
                }
            }
        }
        return result;
    }

    Object[] getNodes(Label label)
    {
        return label.getNodes().toArray();
    }
}
