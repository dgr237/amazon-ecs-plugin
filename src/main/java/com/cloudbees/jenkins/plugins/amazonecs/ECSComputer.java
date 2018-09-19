package com.cloudbees.jenkins.plugins.amazonecs;


public interface ECSComputer {
    ECSSlave getECSNode();
    String getName();
    String getJnlpMac();
    boolean isOnline();
    long getIdleStartMilliseconds();
    boolean isIdle();
    void setAcceptingTasks(boolean acceptingTasks);
}
