package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.model.Node;
import org.antlr.v4.runtime.misc.NotNull;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

public class ProvisioningCallback implements Callable<Node> {
    ECSCloud cloud;
    ECSTaskTemplate template;

    public ProvisioningCallback(@Nonnull ECSCloud cloud, @Nonnull ECSTaskTemplate template) {
        this.cloud=cloud;
        this.template=template;
    }

    @Override
    public Node call() throws Exception {
        return ECSSlave.builder()
                .ecsTaskTemplate(template)
                .cloud(cloud)
                .build();
    }
}
