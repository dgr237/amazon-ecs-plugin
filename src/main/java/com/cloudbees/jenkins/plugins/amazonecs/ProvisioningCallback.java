package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Node;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

class ProvisioningCallback implements Callable<Node> {
    private final ECSCloud cloud;
    private final ECSTaskTemplate template;
    private final ECSService service;

    ProvisioningCallback(@Nonnull ECSCloud cloud, @Nonnull ECSService service, @Nonnull ECSTaskTemplate template) {
        this.cloud=cloud;
        this.template=template;
        this.service=service;
    }

    @Override
    public Node call() throws Exception {
        return ECSSlave.builder()
                .ecsTaskTemplate(template)
                .cloud(cloud)
                .build(service);
    }
}
