<template>
  <div :class="{'import-step-one': true, authe}">
    <div class="right-top-btn">
      <router-link :to="{name: 'index'}">
        <jm-button class="jm-icon-button-cancel" size="small" :loading="loading">取消</jm-button>
      </router-link>
      <jm-button type="primary" class="jm-icon-button-next" size="small"
                 :loading="loading" @click="next">下一步
      </jm-button>
    </div>
    <jm-form :model="gitCloneForm" :rules="gitCloneFormRule" ref="gitCloneFormRef" @submit.prevent>
      <jm-form-item label="URL" prop="uri">
        <jm-input v-model="gitCloneForm.uri" clearable
                  placeholder="请输入URL，例如：https://gitee.com/jianmu-dev/jianmu-ci-ui.git"/>
      </jm-form-item>
      <jm-form-item label="分支" prop="branch">
        <jm-input v-model="gitCloneForm.branch" clearable placeholder="请输入分支"/>
      </jm-form-item>
      <jm-form-item label="认证:">
        <jm-switch v-model="authe" @change="handleAutheChange"/>
      </jm-form-item>
      <div v-if="authe" class="authentication">
        <div>
          <jm-form-item label="类型" prop="credential.type">
            <jm-select v-model="gitCloneForm.credential.type" clearable disabled
                       placeholder="请选择类型" @change="handleCredentialTypeChange">
              <jm-option v-for="type in types" :key="type" :label="type" :value="type"></jm-option>
            </jm-select>
          </jm-form-item>
          <template v-if="gitCloneForm.credential.type === WorkflowDefinitionImporterTypeEnum.HTTPS">
            <jm-form-item label="用户名" prop="credential.userKey">
              <jm-cascader :props="cascaderProps" clearable placeholder="请选择用户名"
                           @change="handleUserKeyChange"/>
            </jm-form-item>
            <jm-form-item label="密码" prop="credential.passKey">
              <jm-select v-model="gitCloneForm.credential.passKey" clearable
                         placeholder="请选择密码" no-data-text="请选择用户名">
                <jm-option v-for="item in passKeys" :key="item" :label="item" :value="item"></jm-option>
              </jm-select>
            </jm-form-item>
          </template>
          <jm-form-item v-else-if="gitCloneForm.credential.type === WorkflowDefinitionImporterTypeEnum.SSH" label="私钥"
                        prop="credential.privateKey">
            <jm-cascader :props="cascaderProps" clearable placeholder="请选择私钥"
                         @change="handlePrivateKeyChange"/>
          </jm-form-item>
        </div>
      </div>
    </jm-form>
  </div>
</template>

<script lang="ts">
import { defineComponent, getCurrentInstance, inject, ref } from 'vue';
import { IGitCloneForm } from '@/model/modules/workflow-definition';
import { WorkflowDefinitionImporterTypeEnum } from '@/api/dto/enumeration';
import { listSecretKey, queryNamespace } from '@/api/view-no-auth';
import { cloneGit } from '@/api/workflow-definition';
import { START_PAGE_NUM } from '@/utils/constants';
import { IGitVo } from '@/api/dto/workflow-definition';

export default defineComponent({
  setup() {
    const { proxy } = getCurrentInstance() as any;

    const gitCloneFormRef = ref();
    const gitCloneForm = ref<IGitCloneForm>({
      uri: '',
      credential: {
        type: WorkflowDefinitionImporterTypeEnum.HTTPS,
      },
      branch: 'master',
    });
    const gitCloneFormRule = ref<object>({
      uri: [
        { required: true, message: 'URI不能为空', trigger: 'blur' },
      ],
      branch: [
        { required: true, message: '分支不能为空', trigger: 'blur' },
      ],
      credential: {
        type: [
          { required: true, message: '请选择类型', trigger: 'change' },
        ],
        userKey: [
          { required: true, message: '请选择用户名', trigger: 'change' },
        ],
        passKey: [
          { required: true, message: '请选择密码', trigger: 'change' },
        ],
      }
      ,
    });
    const passKeys = ref<string[]>([]);
    const loading = ref<boolean>(false);
    const authe = ref<boolean>(true);
    const nextStep = inject('nextStep') as (git: IGitVo, gitCloneForm: IGitCloneForm) => void;

    return {
      gitCloneFormRef,
      gitCloneForm,
      gitCloneFormRule,
      passKeys,
      loading,
      authe,
      types: Object.values(WorkflowDefinitionImporterTypeEnum),
      handleAutheChange: () => {
        if (authe.value) {
          gitCloneForm.value.credential.type = WorkflowDefinitionImporterTypeEnum.HTTPS;

          return;
        }

        // 清空认证数据
        gitCloneForm.value.credential = {};
      },
      handleCredentialTypeChange: () => {
        delete gitCloneForm.value.credential.namespace;
        delete gitCloneForm.value.credential.userKey;
        delete gitCloneForm.value.credential.passKey;
        delete gitCloneForm.value.credential.privateKey;

        passKeys.value = [];
      },
      WorkflowDefinitionImporterTypeEnum,
      cascaderProps: {
        lazy: true,
        lazyLoad: async (node: any, resolve: any) => {
          const { level } = node;
          const nodes: object[] = [];

          if (level === 0) {
            const { list } = await queryNamespace({
              pageNum: START_PAGE_NUM,
              // 一次性获取10w条，达到获取所有目的
              pageSize: 100 * 1000,
            });
            list.forEach(({ name }) => nodes.push({
              value: name,
              label: name,
              leaf: false,
            }));
          } else {
            const { value } = node;
            const skArr = await listSecretKey(value);
            skArr.forEach(sk => nodes.push({
              value: sk,
              label: sk,
              leaf: true,
            }));
          }

          // 通过调用resolve将子节点数据返回，通知组件数据加载完成
          resolve(nodes);
        },
      },
      handleUserKeyChange: async (newVal: any) => {
        if (newVal) {
          gitCloneForm.value.credential.userKey = newVal[1];

          if (gitCloneForm.value.credential.namespace !== newVal[0]) {
            gitCloneForm.value.credential.namespace = newVal[0];

            delete gitCloneForm.value.credential.passKey;
          }

          passKeys.value = await listSecretKey(gitCloneForm.value.credential.namespace);
        } else {
          delete gitCloneForm.value.credential.namespace;
          delete gitCloneForm.value.credential.userKey;

          delete gitCloneForm.value.credential.passKey;

          passKeys.value = [];
        }
      },
      handlePrivateKeyChange: (newVal: any) => {
        if (newVal) {
          gitCloneForm.value.credential.namespace = newVal[0];
          gitCloneForm.value.credential.privateKey = newVal[1];
        } else {
          delete gitCloneForm.value.credential.namespace;
          delete gitCloneForm.value.credential.privateKey;
        }
      },
      next: () => {
        loading.value = true;

        gitCloneFormRef.value.validate((valid: boolean) => {
          if (!valid) {
            // 关闭loading
            loading.value = false;

            return false;
          }

          cloneGit({ ...gitCloneForm.value }).then((git: IGitVo) => {
            nextStep(git, { ...gitCloneForm.value });

            // 关闭loading
            loading.value = false;
          }).catch((err: Error) => {
            // 关闭loading
            loading.value = false;

            proxy.$throw(err, proxy);
          });
        });
      },
    };
  },
});
</script>

<style scoped lang="less">
.import-step-one {
  width: 500px;
  margin: 0 auto;
  padding: 16px 0;

  .right-top-btn {
    position: fixed;
    right: 20px;
    top: 78px;

    .jm-icon-button-cancel::before,
    .jm-icon-button-next::before {
      font-weight: bold;
    }

    a {
      margin-right: 10px;
    }
  }

  &.authe {
    width: 750px;

    .el-form-item {
      width: 65%;
    }
  }

  .authentication {
    background: rgba(238, 240, 243, 0.5);
    border-radius: 2px;
    padding: 20px;

    > div {
      padding: 0 24px;
      display: flex;
      justify-content: space-between;
      background-color: #FFFFFF;
      border-radius: 2px;
      border: 1px solid #B9CFE6;

      .el-form-item {
        width: auto;
      }
    }
  }

}
</style>