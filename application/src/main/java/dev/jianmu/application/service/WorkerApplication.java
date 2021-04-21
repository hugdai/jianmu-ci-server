package dev.jianmu.application.service;

import dev.jianmu.infrastructure.docker.DockerWorker;
import dev.jianmu.infrastructure.storage.StorageService;
import dev.jianmu.parameter.repository.ParameterRepository;
import dev.jianmu.parameter.repository.ReferenceRepository;
import dev.jianmu.parameter.service.ParameterDomainService;
import dev.jianmu.parameter.service.ReferenceDomainService;
import dev.jianmu.task.aggregate.DockerDefinition;
import dev.jianmu.task.aggregate.TaskInstance;
import dev.jianmu.task.aggregate.Worker;
import dev.jianmu.task.repository.DefinitionRepository;
import dev.jianmu.task.repository.WorkerRepository;
import dev.jianmu.task.service.WorkerDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @class: WorkerApplication
 * @description: 任务执行器门面类
 * @author: Ethan Liu
 * @create: 2021-04-02 12:30
 **/
@Service
@Transactional
public class WorkerApplication {
    private static final Logger logger = LoggerFactory.getLogger(WorkerApplication.class);
    private final WorkerRepository workerRepository;
    private final DefinitionRepository definitionRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterDomainService parameterDomainService;
    private final StorageService storageService;
    private final DockerWorker dockerWorker;
    private final WorkerDomainService workerDomainService;
    private final ReferenceRepository referenceRepository;
    private final ReferenceDomainService referenceDomainService;

    @Inject
    public WorkerApplication(
            WorkerRepository workerRepository,
            DefinitionRepository definitionRepository,
            ParameterRepository parameterRepository,
            ParameterDomainService parameterDomainService,
            StorageService storageService,
            DockerWorker dockerWorker, WorkerDomainService workerDomainService,
            ReferenceRepository referenceRepository,
            ReferenceDomainService referenceDomainService
    ) {
        this.workerRepository = workerRepository;
        this.definitionRepository = definitionRepository;
        this.parameterRepository = parameterRepository;
        this.parameterDomainService = parameterDomainService;
        this.storageService = storageService;
        this.dockerWorker = dockerWorker;
        this.workerDomainService = workerDomainService;
        this.referenceRepository = referenceRepository;
        this.referenceDomainService = referenceDomainService;
    }

    public void online(String workerId) {
        Worker worker = this.workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("未找到该Worker"));
        worker.online();
        this.workerRepository.updateStatus(worker);
    }

    public void offline(String workerId) {
        Worker worker = this.workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("未找到该Worker"));
        worker.offline();
        this.workerRepository.updateStatus(worker);
    }

    public void createVolume(String volumeName) {
        this.dockerWorker.createVolume(volumeName);
    }

    public void deleteVolume(String volumeName) {
        this.dockerWorker.deleteVolume(volumeName);
    }

    public void runTask(TaskInstance taskInstance) {
        // 创建DockerTask
        var taskDefinition = this.definitionRepository
                .findByKey(taskInstance.getDefKey())
                .orElseThrow(() -> new RuntimeException("未找到该任务定义"));
        if (!(taskDefinition instanceof DockerDefinition)) {
            throw new RuntimeException("任务定义类型错误");
        }
        var environmentMap = this.getEnvironmentMap(taskInstance);
        var dockerTask = this.workerDomainService
                .createDockerTask((DockerDefinition) taskDefinition, taskInstance, environmentMap);
        // 创建logWriter
        var logWriter = this.storageService.writeLog(taskInstance.getId());
        // 执行任务
        this.dockerWorker.runTask(dockerTask, logWriter);
    }

    private Map<String, String> getEnvironmentMap(TaskInstance taskInstance) {
        var parameterMap = taskInstance.getParameters().stream()
                .map(taskParameter -> Map.entry(
                        "JIANMU_" + taskParameter.getRef().toUpperCase(),
                        taskParameter.getParameterId()
                        )
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var references = this.referenceRepository
                .findByContextIds(
                        Set.of(
                                taskInstance.getTriggerId() + taskInstance.getAsyncTaskKey(),
                                taskInstance.getBusinessId() + taskInstance.getAsyncTaskKey(),
                                taskInstance.getDefKey()
                        )
                );
        references.forEach(reference -> logger.info("-------------reference parameterId--------: {}", reference.getParameterId()));
        var newParameterMap = this.referenceDomainService
                .calculateIds(parameterMap, references);
        var parameters = this.parameterRepository
                .findByIds(new HashSet<>(newParameterMap.values()));
        return this.parameterDomainService.createParameterMap(newParameterMap, parameters)
                .entrySet().stream()
                // TODO 处理参数类型
                .map(entry -> Map.entry(entry.getKey(), (String) entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
