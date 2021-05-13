package dev.jianmu.api.dto;

import dev.jianmu.project.aggregate.GitRepo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * @class: GitRepoDto
 * @description: GitRepoDto
 * @author: Ethan Liu
 * @create: 2021-05-13 19:18
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "GitRepoDto")
public class GitRepoDto {
    @Schema(required = true)
    @NotBlank(message = "参数Uri不能为空")
    private String uri;
    private GitRepo.Type type;
    private String httpsUsername;
    private String httpsPassword;
    private String PrivateKey;
    @Schema(required = true)
    @NotBlank(message = "参数Branch不能为空")
    private String branch;
}
