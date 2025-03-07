<template>
  <div class="jm-workflow-viewer">
    <div v-if="!readonly && graph && tasks.length > 0" class="task-states">
      <task-state v-for="{status, count} in taskStates"
                  :key="status" :status="status" :count="count"/>
    </div>
    <toolbar v-if="!readonly && graph" :zoom-value="zoom" @click-process-log="clickProcessLog" @on-zoom="handleZoom"/>
    <node-toolbar v-if="!readonly && nodeEvent"
                  :task-instance-id="taskInstanceId" :node-event="nodeEvent" :zoom="zoom"
                  @node-click="clickNode"
                  @mouseout="handleNodeBarMouseout"/>
    <div class="canvas" ref="container"/>
  </div>
</template>

<script lang="ts">
import {
  computed,
  defineComponent,
  getCurrentInstance,
  onBeforeUpdate,
  onUnmounted,
  PropType,
  ref,
  SetupContext,
} from 'vue';
import G6, { Graph } from '@antv/g6';
import TaskState from './task-state.vue';
import Toolbar from './toolbar.vue';
import NodeToolbar from './node-toolbar.vue';
import { configNodeAction, init, updateNodeStates } from './utils/graph';
import { ITaskExecutionRecordVo } from '@/api/dto/workflow-execution-record';
import { TaskStatusEnum, TriggerTypeEnum } from '@/api/dto/enumeration';
import { parse } from './utils/dsl';
import { NodeToolbarTabTypeEnum, NodeTypeEnum } from './utils/enumeration';
import { INodeMouseoverEvent } from '@/components/workflow/workflow-viewer/utils/model';

// 注册自定义g6元素
Object.values(import.meta.globEager('./shapes/**')).forEach(({ default: register }) => register(G6));

export default defineComponent({
  name: 'jm-workflow-viewer',
  components: { TaskState, Toolbar, NodeToolbar },
  props: {
    dsl: String,
    readonly: {
      type: Boolean,
      default: false,
    },
    triggerType: String as PropType<TriggerTypeEnum>,
    nodeInfos: {
      type: Array,
      default: () => [],
    },
    tasks: {
      type: Array as PropType<ITaskExecutionRecordVo[]>,
      default: () => [],
    },
  },
  emits: ['click-task-node', 'click-webhook-node', 'click-process-log'],
  setup(props: any, { emit }: SetupContext) {
    const { proxy } = getCurrentInstance() as any;
    const container = ref<HTMLElement>();
    const graph = ref<Graph>();
    const nodeActionConfigured = ref<boolean>(false);
    const taskInstanceId = ref<string>();
    const nodeEvent = ref<INodeMouseoverEvent>();
    const destroyNodeToolbar = () => {
      taskInstanceId.value = undefined;
      nodeEvent.value = undefined;
    };
    const mouseoverNode = (evt: INodeMouseoverEvent) => {
      if (nodeEvent.value) {
        // 上一个事件尚未释放时，保证先释放完，再触发
        destroyNodeToolbar();
        proxy.$nextTick(() => mouseoverNode(evt));
        return;
      }

      switch (evt.type) {
        case NodeTypeEnum.ASYNC_TASK: {
          const task = (props.tasks as ITaskExecutionRecordVo[]).find(item => item.nodeName === evt.id);
          if (task) {
            taskInstanceId.value = task.instanceId;
          }
          break;
        }
      }

      nodeEvent.value = evt;
    };
    const handleNodeBarMouseout = (evt: any) => {
      let isOut = true;
      let tempObj = evt.relatedTarget || evt.toElement;
      // 10级以内可定位
      for (let i = 0; i < 10; i++) {
        if (!tempObj) {
          break;
        }

        if (tempObj.className === 'jm-workflow-viewer-node-toolbar') {
          isOut = false;
          break;
        }

        tempObj = tempObj.parentElement;
      }

      if (isOut) {
        destroyNodeToolbar();
      }
    };
    const zoom = ref<number>();
    const updateZoom = () => {
      setTimeout(() => {
        if (!graph.value) {
          return;
        }

        zoom.value = Math.round(graph.value.getZoom() * 100);
      });
    };

    const allTaskNodes = computed(() => {
      const { nodes } = parse(props.dsl, props.triggerType);

      return nodes.filter(node => node.type === NodeTypeEnum.ASYNC_TASK);
    });

    proxy.$nextTick(() => {
      // 保证整个视图都渲染完毕，才能确定图的宽高
      graph.value = init(props.dsl, props.readonly, props.triggerType, props.nodeInfos, container.value as HTMLElement);

      updateZoom();

      // 配置节点行为
      nodeActionConfigured.value = configNodeAction(graph.value, mouseoverNode);
    });

    onBeforeUpdate(() => {
      if (!graph.value) {
        graph.value = init(props.dsl, props.readonly, props.triggerType, props.nodeInfos, container.value as HTMLElement);

        updateZoom();
      }

      if (!nodeActionConfigured.value) {
        // 禁止多次配置
        // 配置节点行为
        nodeActionConfigured.value = configNodeAction(graph.value, mouseoverNode);
      }

      if (graph.value) {
        // 更新状态
        updateNodeStates(props.tasks, graph.value);
      }
    });

    // 监控容器大小变化
    const interval = ref<any>(setInterval(() => {
      if (!container.value || !graph.value) {
        return;
      }

      const parentElement = container.value.parentElement as HTMLElement;
      graph.value.changeSize(parentElement.clientWidth, parentElement.clientHeight);
    }, 500));

    onUnmounted(() => {
      // 销毁监控容器大小变化
      clearInterval(interval.value);

      // 销毁画布
      graph.value?.destroy();
    });

    return {
      container,
      graph,
      taskInstanceId,
      nodeEvent,
      clickProcessLog: () => {
        emit('click-process-log');
      },
      clickNode: (id: string, nodeType: NodeTypeEnum, tabType: NodeToolbarTabTypeEnum) => {
        switch (nodeType) {
          case NodeTypeEnum.ASYNC_TASK:
            // id为taskInstanceId
            emit('click-task-node', id, tabType);
            break;
          case NodeTypeEnum.WEBHOOK:
            // id为eb目标唯一标识
            emit('click-webhook-node', id, tabType);
        }
      },
      handleNodeBarMouseout,
      zoom,
      handleZoom: (val?: number) => {
        if (!graph.value) {
          return;
        }

        const g = graph.value as Graph;

        if (val === undefined) {
          // fitCanvas(g);
          g.fitView();
        } else {
          g.zoomTo(val / 100, g.getGraphCenterPoint());
        }

        updateZoom();
      },
      taskStates: computed(() => {
        const sArr: {
          status: string;
          count: number;
        }[] = [];

        Object.keys(TaskStatusEnum).forEach(status => sArr.push({
          status,
          count: status === TaskStatusEnum.INIT ? (allTaskNodes.value.length - props.tasks.length) : 0,
        }));

        props.tasks.forEach(({ status }: ITaskExecutionRecordVo) => {
          const s = sArr.find(item => item.status === status);
          if (s) {
            s.count += 1;
          }
        });

        return sArr;
      }),
    };
  },
});
</script>

<style lang="less">
.jm-workflow-viewer {
  position: relative;

  .task-states {
    position: absolute;
    z-index: 1;
    bottom: 22px;
    left: 44px;
    background-color: rgba(255, 255, 255, 0.6);

    > div + div {
      margin-left: 16px;
    }
  }

  .canvas {
    position: relative;
    min-height: 300px;

    .g6-tooltip {
      padding: 5px;
      font-size: 14px;
      font-weight: 400;
      color: #FFFFFF;
      line-height: 22px;

      background-color: rgba(51, 51, 51, 0.75);
      box-shadow: 0 9px 28px 8px rgba(51, 51, 51, 0.06), 0 6px 16px 0 rgba(51, 51, 51, 0.08);
      border-radius: 2px;
    }
  }
}
</style>