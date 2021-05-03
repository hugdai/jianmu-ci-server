package dev.jianmu.task.aggregate;

import dev.jianmu.task.aggregate.spec.ContainerSpec;

/**
 * @class: DockerTask
 * @description: Docker任务封装
 * @author: Ethan Liu
 * @create: 2021-04-14 20:14
 **/
public class DockerTask {
    private String taskInstanceId;
    private String businessId;
    private String projectId;
    // 任务定义唯一Key
    private String defKey;
    private String resultFile;
    // 容器规格定义
    private ContainerSpec spec;

    private DockerTask() {
    }

    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getDefKey() {
        return defKey;
    }

    public String getResultFile() {
        return resultFile;
    }

    public ContainerSpec getSpec() {
        return spec;
    }

    public static final class Builder {
        private String taskInstanceId;
        private String businessId;
        private String projectId;
        // 任务定义唯一Key
        private String defKey;
        private String resultFile;
        // 容器规格定义
        private ContainerSpec spec;

        private Builder() {
        }

        public static Builder aDockerTask() {
            return new Builder();
        }

        public Builder taskInstanceId(String taskInstanceId) {
            this.taskInstanceId = taskInstanceId;
            return this;
        }

        public Builder businessId(String businessId) {
            this.businessId = businessId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder defKey(String defKey) {
            this.defKey = defKey;
            return this;
        }

        public Builder resultFile(String resultFile) {
            this.resultFile = resultFile;
            return this;
        }

        public Builder spec(ContainerSpec spec) {
            this.spec = spec;
            return this;
        }

        public DockerTask build() {
            DockerTask dockerTask = new DockerTask();
            dockerTask.spec = this.spec;
            dockerTask.projectId = this.projectId;
            dockerTask.businessId = this.businessId;
            dockerTask.taskInstanceId = this.taskInstanceId;
            dockerTask.defKey = this.defKey;
            dockerTask.resultFile = this.resultFile;
            return dockerTask;
        }
    }
}
