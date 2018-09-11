package com.cloudbees.jenkins.plugins.amazonecs;

import com.sun.org.apache.xpath.internal.operations.Bool;

public interface ECSComputer {
    ECSSlave getNode();
    String getName();
    String getJnlpMac();
    boolean isOnline();
    long getIdleStartMilliseconds();
    boolean isIdle();
    void setAcceptingTasks(boolean acceptingTasks);
}
