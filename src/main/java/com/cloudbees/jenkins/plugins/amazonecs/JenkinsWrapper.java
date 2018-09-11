package com.cloudbees.jenkins.plugins.amazonecs;

import jenkins.model.Jenkins;

class JenkinsWrapper {

    Jenkins getJenkinsInstance()
    {
        return Jenkins.get();
    }
}
