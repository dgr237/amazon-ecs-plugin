package com.cloudbees.jenkins.plugins.amazonecs;

import com.cloudbees.jenkins.plugins.amazonecs.retentionStrategy.ECSRetentionStrategy;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ECSSlaveTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private ECSTaskTemplate testTemplate;

    @Before
    public void setup()
    {
        testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"cloudbees/maven-java","FARGATE",null,2048,0,2048,"subnet","secGroup",true,false,null,null,null,null,null,null);
    }

    @Test
    public void Test() throws IOException, Descriptor.FormException {
        ECSSlaveImpl impl=new ECSSlaveImpl("Test",testTemplate,"TestNode","TestCloud","test-label",new ECSLauncher(),new ECSRetentionStrategy(true,1));

    }

}