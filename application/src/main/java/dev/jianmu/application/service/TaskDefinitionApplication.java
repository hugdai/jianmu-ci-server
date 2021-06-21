package dev.jianmu.application.service;

import com.github.pagehelper.PageInfo;
import dev.jianmu.application.exception.DataNotFoundException;
import dev.jianmu.infrastructure.client.RegistryClient;
import dev.jianmu.infrastructure.mybatis.version.TaskDefinitionRepositoryImpl;
import dev.jianmu.parameter.aggregate.Parameter;
import dev.jianmu.parameter.repository.ParameterRepository;
import dev.jianmu.task.aggregate.Definition;
import dev.jianmu.task.aggregate.DockerDefinition;
import dev.jianmu.task.aggregate.TaskParameter;
import dev.jianmu.task.repository.DefinitionRepository;
import dev.jianmu.version.aggregate.TaskDefinition;
import dev.jianmu.version.aggregate.TaskDefinitionVersion;
import dev.jianmu.version.repository.TaskDefinitionVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @class: TaskDefinitionApplication
 * @description: 任务定义门面类
 * @author: Ethan Liu
 * @create: 2021-03-25 20:31
 **/
@Service
public class TaskDefinitionApplication {
    private static final Logger logger = LoggerFactory.getLogger(TaskDefinitionApplication.class);
    private final DefinitionRepository definitionRepository;
    private final ParameterRepository parameterRepository;
    private final TaskDefinitionRepositoryImpl taskDefinitionRepository;
    private final TaskDefinitionVersionRepository taskDefinitionVersionRepository;
    private final RegistryClient registryClient;

    @Inject
    public TaskDefinitionApplication(
            DefinitionRepository definitionRepository,
            ParameterRepository parameterRepository,
            TaskDefinitionRepositoryImpl taskDefinitionRepository,
            TaskDefinitionVersionRepository taskDefinitionVersionRepository,
            RegistryClient registryClient
    ) {
        this.definitionRepository = definitionRepository;
        this.parameterRepository = parameterRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskDefinitionVersionRepository = taskDefinitionVersionRepository;
        this.registryClient = registryClient;
    }

    private List<Parameter> createParameters(Set<TaskParameter> parameters) {
        var parameterMap = parameters.stream()
                .map(taskParameter ->
                        Map.entry(
                                taskParameter,
                                Parameter.Type
                                        .valueOf(taskParameter.getType())
                                        .newParameter(taskParameter.getValue())
                        )
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        parameters.forEach(taskParameter -> {
            var parameterId = parameterMap.get(taskParameter).getId();
            taskParameter.setParameterId(parameterId);
        });
        return new ArrayList<>(parameterMap.values());
    }

    private List<Parameter> mergeParameters(Set<TaskParameter> inputParameters, Set<TaskParameter> outputParameters) {
        // 创建参数存储
        var inParameters = this.createParameters(inputParameters);
        var outParameters = this.createParameters(outputParameters);
        inParameters.addAll(outParameters);
        return inParameters;
    }

    @Transactional
    public void createDockerDefinition(DockerDefinition dockerDefinition) {
        // 创建参数存储
        var parameters = this.mergeParameters(dockerDefinition.getInputParameters(), dockerDefinition.getOutputParameters());
        // 保存
        this.parameterRepository.addAll(parameters);
        this.definitionRepository.add(dockerDefinition);
    }

    @Transactional
    public void installDefinition(String ref, String version) {
        var dockerDefinition = this.registryClient.findByRefAndVersion(ref, version)
                .filter(definition -> definition instanceof DockerDefinition)
                .map(definition -> (DockerDefinition) definition)
                .orElseThrow(() -> new DataNotFoundException("未找到该组件定义"));
        // 创建参数存储
        var parameters = this.mergeParameters(dockerDefinition.getInputParameters(), dockerDefinition.getOutputParameters());
        // 保存
        this.parameterRepository.addAll(parameters);
        this.definitionRepository.add(dockerDefinition);
    }

    public Definition findByKey(String key) {
        String[] strings = key.split(":");
        return this.definitionRepository.findByRefAndVersion(strings[0], strings[1]).orElseThrow(() -> new DataNotFoundException("未找到该任务定义版本"));
    }

    public TaskDefinitionVersion findByRefAndName(String ref, String name) {
        return this.taskDefinitionVersionRepository.findByTaskDefinitionRefAndName(ref, name)
                .orElseThrow(() -> new DataNotFoundException("未找到该任务定义版本"));
    }

    public TaskDefinition findByRef(String ref) {
        return this.taskDefinitionRepository.findByRef(ref).orElseThrow(() -> new DataNotFoundException("未找到该任务定义"));
    }

    public List<TaskDefinitionVersion> findVersionByRef(String ref) {
        return this.taskDefinitionVersionRepository.findByTaskDefinitionRef(ref);
    }

    public PageInfo<TaskDefinition> findAll(String name, int pageNum, int pageSize) {
        return this.taskDefinitionRepository.findAll(name, pageNum, pageSize);
    }

    public void deleteTaskDefinitionVersion(String ref, String name) {
        var versions = this.taskDefinitionVersionRepository
                .findByTaskDefinitionRef(ref);
        if (versions.size() == 1) {
            this.taskDefinitionRepository
                    .findByRef(versions.get(0).getTaskDefinitionRef())
                    .ifPresent(this.taskDefinitionRepository::delete);
        }
        var version = versions.stream()
                .filter(v -> v.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new DataNotFoundException("未找到该任务定义版本"));
        var strings = version.getDefinitionKey().split(":");
        this.taskDefinitionVersionRepository.delete(version);
        this.definitionRepository.delete(strings[0], strings[1]);
    }
}
