<template>
  <div class="workflow-definition-editor" v-loading="loading">
    <div class="right-top-btn">
      <router-link :to="{name: 'index'}">
        <jm-button class="jm-icon-button-cancel" size="small">取消</jm-button>
      </router-link>
      <jm-button type="primary" class="jm-icon-button-preserve" size="small"
                 @click="save" :loading="loading">保存
      </jm-button>
    </div>
    <jm-tabs v-model="activatedTab" class="tabs">
      <jm-tab-pane name="dsl" lazy>
        <template #label><i class="jm-icon-tab-dsl"></i> DSL模式</template>
        <jm-dsl-editor id="workflow-definition-editor" class="dsl-editor" v-model:value="editorForm.dslText"/>
      </jm-tab-pane>
    </jm-tabs>
  </div>
</template>

<script lang="ts">
import { defineComponent, getCurrentInstance, inject, ref } from 'vue';
import { useRouter } from 'vue-router';
import { save } from '@/api/workflow-definition';
import { ISaveForm } from '@/model/modules/workflow-definition';
import { adaptHeight, IAutoHeight } from '@/utils/auto-height';
import { fetchProjectDetail } from '@/api/view-no-auth';

const autoHeight: IAutoHeight = {
  elementId: 'workflow-definition-editor',
  offsetTop: 215,
};

export default defineComponent({
  props: {
    id: String,
  },
  setup(props: any) {
    const { proxy } = getCurrentInstance() as any;
    const router = useRouter();
    const reloadMain = inject('reloadMain') as () => void;

    const editMode = !!props.id;
    const editorForm = ref<ISaveForm>({
      id: props.id,
      dslText: '',
    });
    const loading = ref<boolean>(false);

    if (editMode) {
      loading.value = !loading.value;

      fetchProjectDetail(props.id).then(({ dslText }) => {
        editorForm.value.dslText = dslText;

        loading.value = !loading.value;

        proxy.$nextTick(() => adaptHeight(autoHeight));
      }).catch((err: Error) => {
        loading.value = !loading.value;

        proxy.$throw(err, proxy);

        proxy.$nextTick(() => adaptHeight(autoHeight));
      });
    } else {
      proxy.$nextTick(() => adaptHeight(autoHeight));
    }

    return {
      editMode,
      editorForm,
      loading,
      activatedTab: ref<string>('dsl'),
      save: () => {
        if (editorForm.value.dslText === '') {
          proxy.$error('DSL不能为空');

          return;
        }

        // 开启loading
        loading.value = true;

        save({ ...editorForm.value })
          .then(() => {
            if (!editMode) {
              // 刷新流程定义列表
              reloadMain();
            }

            proxy.$success(editMode ? '编辑成功' : '新增成功');

            router.push({ name: 'index' });
          })
          .catch((err: Error) => {
            // 关闭loading
            loading.value = false;

            proxy.$throw(err, proxy);
          });
      },
    };
  },
});
</script>

<style scoped lang="less">
.workflow-definition-editor {
  font-size: 14px;
  color: #333333;
  margin-bottom: 25px;

  .right-top-btn {
    position: fixed;
    right: 20px;
    top: 78px;

    .jm-icon-button-cancel::before,
    .jm-icon-button-preserve::before {
      font-weight: bold;
    }

    a {
      margin-right: 10px;
    }
  }

  .tabs {
    background-color: #FFFFFF;
    border-radius: 4px 4px 0 0;

    .dsl-editor {
      z-index: 1;
    }
  }
}
</style>
