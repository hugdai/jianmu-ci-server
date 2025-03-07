<template>
  <div class="workflow-execution-record-detail" v-loading="loading">
    <div class="right-top-btn">
      <router-link :to="{name: 'index'}">
        <jm-button type="primary" class="jm-icon-button-cancel" size="small">关闭</jm-button>
      </router-link>
    </div>
    <div class="basic-section">
      <jm-tooltip content="触发" placement="left">
        <button class="trigger-btn jm-icon-button-on" @click="execute"></button>
      </jm-tooltip>
      <div class="info">
        <div class="name">{{ data.record?.name }}</div>
        <div class="desc" v-html="data.record?.description?.replaceAll('\n', '<br/>')"></div>
      </div>
      <div v-if="!data.record?.status" class="instance-tabs">
        <div class="tab init selected">
          <div class="left-horn"/>
          <div class="right-horn"/>
          <div class="label">-</div>
        </div>
      </div>
      <jm-scrollbar v-else ref="navScrollBar">
        <div class="instance-tabs">
          <div v-for="record of data.allRecords" :key="record.id" :class="{
            tab: true,
            [record.id === data.record.id? 'selected': 'unselected']: true,
            [record.status.toLowerCase()]: true,
          }" @click="changeRecord(record)">
            <div v-if="record.id === data.record.id" class="left-horn"/>
            <div v-if="record.id === data.record.id" class="right-horn"/>
            <div class="label">{{ record.serialNo }}</div>
          </div>
        </div>
      </jm-scrollbar>
      <div :class="{
        'instance-tab-content': true,
        [!data.record?.status? 'init' : data.record.status.toLowerCase()]: true,
      }">
        <div class="item">
          <div class="value">{{ datetimeFormatter(data.record?.startTime) }}</div>
          <div>启动时间</div>
        </div>
        <div class="item">
          <div class="value">{{ datetimeFormatter(data.record?.endTime) }}</div>
          <div>完成时间</div>
        </div>
        <div class="item">
          <div class="value">
            {{
              executionTimeFormatter(data.record?.startTime, data.record?.endTime, data.record?.status === 'RUNNING')
            }}
          </div>
          <div>执行时长</div>
        </div>
        <div class="item">
          <div v-if="!data.record?.id" class="value">无</div>
          <jm-tooltip v-else :content="data.record?.id" placement="top">
            <div class="value ellipsis">{{ data.record?.id }}</div>
          </jm-tooltip>
          <div>流程实例ID</div>
        </div>
        <div class="item">
          <jm-tooltip :content="data.record?.workflowVersion" placement="top">
            <div class="value ellipsis">{{ data.record?.workflowVersion }}</div>
          </jm-tooltip>
          <div>流程版本号</div>
        </div>
        <jm-tooltip v-if="data.record?.status === WorkflowExecutionRecordStatusEnum.RUNNING" content="终止"
                    placement="left">
          <button class="terminate-btn jm-icon-button-stop" @click="terminate"></button>
        </jm-tooltip>
      </div>
    </div>

    <div class="workflow-section">
      <jm-workflow-viewer id="workflow-execution-record-detail-workflow"
                          :dsl="dslSourceCode"
                          :trigger-type="data.record?.triggerType"
                          :node-infos="nodeInfos"
                          :tasks="data.taskRecords"
                          @click-process-log="openProcessLog"
                          @click-task-node="openTaskLog"
                          @click-webhook-node="openWebhookLog"/>
    </div>
    <jm-drawer
      title="查看任务执行日志"
      :size="850"
      v-model="taskLogForm.drawerVisible"
      direction="rtl"
      destroy-on-close>
      <task-log :id="taskLogForm.id" :tab-type="taskLogForm.tabType"/>
    </jm-drawer>
    <jm-drawer
      title="查看流程日志"
      :size="850"
      v-model="processLogDrawer"
      direction="rtl"
      destroy-on-close>
      <process-log/>
    </jm-drawer>
    <jm-drawer
      title="查看Webhook日志"
      :size="850"
      v-model="webhookLogForm.drawerVisible"
      direction="rtl"
      destroy-on-close>
      <webhook-log :eb-target-id="webhookLogForm.id"
                   :trigger-id="webhookLogForm.triggerId"
                   :tab-type="webhookLogForm.tabType"/>
    </jm-drawer>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, getCurrentInstance, inject, onBeforeUnmount, onMounted, ref } from 'vue';
import { createNamespacedHelpers, useStore } from 'vuex';
import { namespace } from '@/store/modules/workflow-execution-record';
import { IOpenTaskLogForm, IOpenWebhookLogForm, IState } from '@/model/modules/workflow-execution-record';
import { datetimeFormatter, executionTimeFormatter } from '@/utils/formatter';
import { TaskStatusEnum, WorkflowExecutionRecordStatusEnum } from '@/api/dto/enumeration';
import TaskLog from '@/views/workflow-execution-record/task-log.vue';
import ProcessLog from '@/views/workflow-execution-record/process-log.vue';
import WebhookLog from '@/views/workflow-execution-record/webhook-log.vue';
import { INodeInfoVo, ITaskExecutionRecordVo, IWorkflowExecutionRecordVo } from '@/api/dto/workflow-execution-record';
import { executeImmediately } from '@/api/workflow-definition';
import { adaptHeight, IAutoHeight } from '@/utils/auto-height';
import sleep from '@/utils/sleep';
import { onBeforeRouteUpdate, useRouter } from 'vue-router';
import { terminate } from '@/api/workflow-execution-record';
import { HttpError, TimeoutError } from '@/utils/rest/error';
import { IProjectDetailVo } from '@/api/dto/project';
import { NodeToolbarTabTypeEnum } from '@/components/workflow/workflow-viewer/utils/enumeration';

const { mapActions, mapMutations } = createNamespacedHelpers(namespace);

const autoHeight: IAutoHeight = {
  elementId: 'workflow-execution-record-detail-workflow',
  offsetTop: 390,
};

export default defineComponent({
  components: { TaskLog, ProcessLog, WebhookLog },
  props: {
    projectId: {
      type: String,
      required: true,
    },
    workflowExecutionRecordId: String,
  },
  setup(props: any) {
    const { proxy } = getCurrentInstance() as any;
    const router = useRouter();
    const state = useStore().state[namespace] as IState;
    const loading = ref<boolean>(false);
    const taskLogForm = ref<IOpenTaskLogForm>({
      drawerVisible: false,
      id: '',
      tabType: '',
    });
    const webhookLogForm = ref<IOpenWebhookLogForm>({
      drawerVisible: false,
      id: '',
      tabType: '',
    });
    const processLogDrawer = ref<boolean>(false);
    const reloadMain = inject('reloadMain') as () => void;
    const navScrollBar = ref();
    let terminateLoad = false;

    const loadDetail = async (refreshing?: boolean) => {
      if (terminateLoad) {
        console.debug('组件已卸载，终止刷新');
        return;
      }

      try {
        if (!refreshing) {
          loading.value = !loading.value;
        }

        try {
          await proxy.fetchDetail({
            projectId: props.projectId,
            workflowExecutionRecordId: props.workflowExecutionRecordId,
          });
        } catch (err) {
          if (!refreshing) {
            throw err;
          }

          if (err instanceof TimeoutError) {
            // 忽略超时错误
            console.warn(err.message);
          } else if (err instanceof HttpError) {
            const { response } = err as HttpError;

            if (response && response.status !== 502) {
              throw err;
            }

            // 忽略错误
            console.warn(err.message);
          }
        }
        if (!refreshing) {
          loading.value = !loading.value;
        }

        const { status } = state.recordDetail.record as IWorkflowExecutionRecordVo;

        if (status === WorkflowExecutionRecordStatusEnum.RUNNING ||
          state.recordDetail.taskRecords.find(item => [TaskStatusEnum.WAITING, TaskStatusEnum.RUNNING].includes(item.status))) {
          console.debug('3秒后刷新');
          await sleep(3000);
          await loadDetail(true);
        }
      } catch (err) {
        if (!refreshing) {
          loading.value = !loading.value;
        }

        proxy.$throw(err, proxy);
      }
    };

    onBeforeRouteUpdate((to, { name }) => {
      if (name !== 'workflow-execution-record-detail') {
        return;
      }

      // 保留滚动偏移量
      proxy.mutateNavScrollLeft(navScrollBar.value.scrollbar.firstElementChild.scrollLeft);
    });

    // 初始化流程执行记录详情
    onMounted(async () => {
      // 初始化滚动偏移量
      if (state.recordDetail.project?.id === props.projectId && props.workflowExecutionRecordId) {
        navScrollBar.value.scrollbar.firstElementChild.scrollLeft = state.recordDetail.navScrollLeft;
      } else {
        proxy.mutateNavScrollLeft(0);
      }

      adaptHeight(autoHeight);

      await loadDetail();
    });

    onBeforeUnmount(() => {
      terminateLoad = true;

      // 清空数据
      proxy.mutateRecordDetail({});
    });

    const data = computed<{
      project?: IProjectDetailVo;
      allRecords: IWorkflowExecutionRecordVo[];
      record?: IWorkflowExecutionRecordVo;
      taskRecords: ITaskExecutionRecordVo[];
    }>(() => state.recordDetail);

    return {
      navScrollBar,
      WorkflowExecutionRecordStatusEnum,
      TaskStatusEnum,
      data,
      loading,
      dslSourceCode: computed<string | undefined>(() => state.recordDetail.recordDsl),
      nodeInfos: computed<INodeInfoVo[]>(() => state.recordDetail.nodeInfos),
      taskLogForm,
      webhookLogForm,
      ...mapMutations({
        mutateRecordDetail: 'mutateRecordDetail',
        mutateNavScrollLeft: 'mutateRecordDetailNavScrollLeft',
      }),
      ...mapActions({
        fetchDetail: 'fetchDetail',
      }),
      loadDetail,
      changeRecord: async (record: IWorkflowExecutionRecordVo) => {
        const { id } = record;

        if (!data.value.record || data.value.record.id === id) {
          // 忽略
          return;
        }

        await router.push({
          name: 'workflow-execution-record-detail',
          query: {
            projectId: props.projectId,
            workflowExecutionRecordId: id,
          },
        });

        // 刷新详情
        reloadMain();
      },
      datetimeFormatter,
      executionTimeFormatter,
      execute: () => {
        const isWarning = !!data.value.project?.eventBridgeId;
        let msg = '<div>确定要触发吗?</div>';
        if (isWarning) {
          msg += '<div style="color: red; margin-top: 5px; font-size: 12px; line-height: normal;">注意：项目已关联事件桥接器，手动触发可能会导致不可预知的结果，请慎重操作。</div>';
        }

        proxy.$confirm(msg, '触发项目执行', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: isWarning ? 'warning' : 'info',
          dangerouslyUseHTMLString: true,
        }).then(() => {
          executeImmediately(props.projectId).then(async () => {
            proxy.$success('操作成功');

            // 清除滚动偏移量
            proxy.mutateNavScrollLeft(0);

            await router.push({
              name: 'workflow-execution-record-detail',
              query: {
                projectId: props.projectId,
              },
            });

            // 刷新详情
            reloadMain();
          }).catch((err: Error) => proxy.$throw(err, proxy));
        }).catch(() => {
        });
      },
      terminate: () => {
        proxy.$confirm('确定要终止吗?', '终止项目执行', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'info',
        }).then(() => {
          if (!data.value.record) {
            return;
          }

          terminate(data.value.record.id).then(() => {
            proxy.$success('终止成功');

            // 刷新详情
            reloadMain();
          }).catch((err: Error) => proxy.$throw(err, proxy));
        }).catch(() => {
        });
      },
      openTaskLog: (nodeId: string, tabType: NodeToolbarTabTypeEnum) => {
        taskLogForm.value.drawerVisible = true;
        taskLogForm.value.id = nodeId;
        taskLogForm.value.tabType = tabType;
      },
      processLogDrawer,
      openProcessLog: () => {
        processLogDrawer.value = true;
      },
      openWebhookLog: (nodeId: string, tabType: NodeToolbarTabTypeEnum) => {
        webhookLogForm.value.drawerVisible = true;
        webhookLogForm.value.id = nodeId;
        webhookLogForm.value.tabType = tabType;
        webhookLogForm.value.triggerId = data.value.record?.triggerId;
      },
    };
  },
});
</script>

<style scoped lang="less">
@primary-color: #096DD9;
@secondary-color: #0091FF;

.workflow-execution-record-detail {
  font-size: 14px;
  color: #333333;
  margin-bottom: 25px;

  .param-value {
    color: #606266;

    &.ellipsis {
      display: inline-block;
      width: 80px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      vertical-align: bottom;
      cursor: default;
    }
  }

  .right-top-btn {
    position: fixed;
    right: 20px;
    top: 78px;

    .jm-icon-button-cancel::before {
      font-weight: bold;
    }
  }

  .basic-section {
    position: relative;
    margin-bottom: 16px;
    padding-top: 16px;
    background: #FFFFFF;

    .trigger-btn {
      position: absolute;
      right: 12px;
      top: 12px;
      width: 50px;
      height: 50px;
      border-radius: 4px;
      font-size: 36px;
      border: 0;
      background-color: transparent;
      color: #6B7B8D;
      cursor: pointer;

      &:active {
        background-color: #EFF7FF;
      }
    }

    .info {
      padding: 0 24px;
      margin-bottom: 16px;

      .name {
        font-size: 20px;
        font-weight: bold;
        color: #082340;
      }

      .desc {
        margin-top: 5px;
        width: 80%;
        font-size: 14px;
        color: #6B7B8D;
      }
    }

    .instance-tabs {
      display: flex;
      color: #FFFFFF;
      white-space: nowrap;

      .tab + .tab {
        margin-left: 8px;
      }

      .tab {
        position: relative;
        //width: 70px;
        flex-basis: 70px;
        flex-shrink: 0;
        height: 60px;
        border-radius: 4px 4px 0 0;

        .left-horn, .right-horn {
          position: absolute;
          bottom: 0;
          width: 8px;
          height: 8px;
          overflow: hidden;

          &::before {
            content: '';
            position: absolute;
            bottom: 0;
            width: 48px;
            height: 48px;
            overflow: hidden;
            background-color: #FFF;
          }
        }

        .left-horn {
          left: -8px;

          &::before {
            right: 0;
            border-bottom-right-radius: 8px;
          }
        }

        .right-horn {
          right: -8px;

          &::before {
            left: 0;
            border-bottom-left-radius: 8px;
          }
        }

        &.init {
          &, .left-horn, .right-horn {
            background-color: #979797;
          }
        }

        &.running {
          &, .left-horn, .right-horn {
            background-color: #10C2C2;
          }
        }

        &.finished {
          &, .left-horn, .right-horn {
            background-color: #3EBB03;
          }
        }

        &.terminated {
          &, .left-horn, .right-horn {
            background-color: #CF1524;
          }
        }

        &.unselected {
          cursor: pointer;
          height: 59px;
          border-bottom: 1px solid #FFF;
          opacity: 0.55;

          .label {
            position: absolute;
            top: 7px;
            right: 10px;
            font-size: 20px;
          }
        }

        &.selected {
          cursor: default;

          .label {
            padding-left: 4px;
            line-height: 60px;
            font-size: 30px;
            text-align: center;

            &::before {
              content: '';
              position: absolute;
              left: 0;
              top: 15px;
              width: 4px;
              height: 30px;
              background: rgba(255, 255, 255, 0.8);
              border-radius: 0 100px 100px 0;
              overflow: hidden;
            }
          }
        }
      }
    }

    .instance-tab-content {
      position: relative;
      display: flex;
      padding: 15px 30px;
      color: #FFFFFF;

      &.init {
        background-color: #979797;
      }

      &.running {
        background-color: #10C2C2;
      }

      &.finished {
        background-color: #3EBB03;
      }

      &.terminated {
        background-color: #CF1524;
      }

      .item + .item {
        margin-left: 80px;
      }

      .item {
        font-size: 14px;

        .value {
          font-size: 16px;
          font-weight: bold;
          margin-bottom: 5px;

          &.ellipsis {
            display: inline-block;
            width: 80px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            vertical-align: bottom;
            cursor: default;
          }
        }
      }

      .terminate-btn {
        position: absolute;
        top: 14px;
        right: 12px;
        width: 50px;
        height: 50px;
        border-radius: 4px;
        font-size: 36px;
        border: 0;
        background-color: transparent;
        color: #FFFFFF;
        cursor: pointer;

        &:active {
          background-color: #55DBDB;
        }
      }
    }
  }

  .workflow-section {
    background-color: #FFFFFF;
  }

  ::v-deep(.el-tabs__nav-scroll) {
    line-height: 46px;
  }

  ::v-deep(.el-drawer) {
    .el-drawer__header {
      > span::before {
        font-family: 'jm-icon-input';
        content: '\e803';
        margin-right: 10px;
        color: #6B7B8D;
        font-size: 20px;
        vertical-align: bottom;
      }
    }
  }
}
</style>

