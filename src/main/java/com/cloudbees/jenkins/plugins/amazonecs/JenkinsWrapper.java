package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

public final class JenkinsWrapper {

    private JenkinsWrapper() {
    }

    public static Jenkins getInstance() {
        return Jenkins.getInstance();
    }

    static JenkinsLocationConfiguration getJenkinsLocationConfiguration() {
        return JenkinsLocationConfiguration.get();
    }

    static ECSService getECSService(String credentialsId, String regionName) {
        return new ECSService(credentialsId, regionName);
    }

    public static ECSService getECSService(AWSCredentialsProvider credentialsProvider, String regionName) {
        return new ECSService(credentialsProvider, regionName);
    }

    public static ClientConfiguration getClientConfiguration()
    {
        ProxyConfiguration proxy = getInstance().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        if (proxy != null) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }
        return clientConfiguration;
    }
}
