package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Node;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

class ProvisioningCallback implements Callable<Node> {
    private final ECSCloud cloud;
    private final ECSTaskTemplate template;

    ProvisioningCallback(@Nonnull ECSCloud cloud, @Nonnull ECSTaskTemplate template) {
        this.cloud=cloud;
        this.template=template;
    }

    @Override
    public Node call() throws Exception {
        return ECSSlaveImpl.builder()
                .ecsTaskTemplate(template)
                .cloud(cloud)
                .build();
    }
}
