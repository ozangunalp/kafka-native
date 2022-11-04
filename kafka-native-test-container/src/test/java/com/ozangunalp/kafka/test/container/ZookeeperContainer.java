package com.ozangunalp.kafka.test.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import io.strimzi.test.container.StrimziZookeeperContainer;

public class ZookeeperContainer extends StrimziZookeeperContainer {
    private String name;

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);

        ContainerUtils.recordContainerOutput(name, this);
    }

    public ZookeeperContainer withName(String name) {
        this.name = name;
        return this;
    }

}
