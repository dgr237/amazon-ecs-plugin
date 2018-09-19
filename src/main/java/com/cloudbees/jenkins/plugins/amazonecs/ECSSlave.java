package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.remoting.VirtualChannel;

import java.io.IOException;

public interface ECSSlave {
    ECSSlaveHelper getHelper();
    ECSComputer getECSComputer();
    ECSCloud getCloud();
    void terminate() throws IOException, InterruptedException;
    VirtualChannel getChannel();
    String getNodeName();
    void save() throws IOException;

}
