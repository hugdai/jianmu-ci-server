package dev.jianmu.infrastructure.docker;

import dev.jianmu.task.aggregate.DockerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;

/**
 * @class: TestDockerWorker
 * @description: 测试TestDockerWorker
 * @author: Ethan Liu
 * @create: 2021-04-16 14:12
 **/
@Service
@Profile("test")
public class TestDockerWorker implements DockerWorker {
    private static final Logger logger = LoggerFactory.getLogger(TestDockerWorker.class);

    @Override
    public void createVolume(String volumeName) {
        logger.info("createVolume: {}", volumeName);
    }

    @Override
    public void deleteVolume(String volumeName) {
        logger.info("deleteVolume: {}", volumeName);
    }

    @Override
    public void runTask(DockerTask dockerTask, BufferedWriter logWriter) {
        logger.info("runTask: {}", dockerTask);
    }
}
