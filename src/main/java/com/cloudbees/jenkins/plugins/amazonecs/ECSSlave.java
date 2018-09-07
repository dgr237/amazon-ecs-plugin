package com.cloudbees.jenkins.plugins.amazonecs;

import java.io.IOException;
import java.util.Collection;

public interface ECSSlave {
    ECSCloud getCloud();
    ECSSlaveImpl.State getTaskState();
    void setTaskState(ECSSlaveImpl.State state);
    ECSTaskTemplate getTemplate();
    ECSComputer getECSComputer();
    void save() throws IOException;
    void setTaskArn(String taskArn);
    String getNodeName();
    Collection<String> getDockerRunCommand();
    Boolean isOnline();
}
