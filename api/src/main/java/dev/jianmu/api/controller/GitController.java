package dev.jianmu.api.controller;

import dev.jianmu.api.dto.GitRepoDto;
import dev.jianmu.api.mapper.GitRepoMapper;
import dev.jianmu.api.vo.GitRepoVo;
import dev.jianmu.application.service.GitApplication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * @class: GitController
 * @description: Git API
 * @author: Ethan Liu
 * @create: 2021-05-14 11:17
 **/
@RestController
@RequestMapping("git")
@Tag(name = "Git", description = "Git API")
public class GitController {
    private final GitApplication gitApplication;

    public GitController(GitApplication gitApplication) {
        this.gitApplication = gitApplication;
    }

    @GetMapping("/list")
    public Map<String, Boolean> listFiles(@RequestParam("dir") String dir) {
        return this.gitApplication.listFiles(dir);
    }

    @PostMapping("/clone")
    @Operation(summary = "克隆Git库", description = "克隆Git库并返回文件Map，当使用SSH克隆时必须提供key")
    public GitRepoVo cloneGitRepo(@RequestBody @Valid GitRepoDto gitRepoDto) {
        var gitRepo = GitRepoMapper.INSTANCE.toGitRepo(gitRepoDto);
        var files = this.gitApplication.cloneGitRepo(gitRepo);
        return GitRepoMapper.INSTANCE.toGitRepoVo(gitRepo, files);
    }
}
