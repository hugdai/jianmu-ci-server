<template>
  <div class="secret-key-sk-manager" v-loading="loading">
    <div class="right-top-btn">
      <router-link :to="{name: 'secret-key'}">
        <jm-button type="primary" class="jm-icon-button-cancel" size="small">关闭</jm-button>
      </router-link>
    </div>
    <div class="namespace">
      <div class="name">{{ ns }}</div>
      <div class="desc" v-html="description"></div>
    </div>
    <div class="keys">
      <div class="title">
        <span>密钥列表</span>
        <span class="desc">（共有 {{ keys.length }} 个密钥）</span>
      </div>
      <div class="menu-bar">
        <button class="add" @click="creationActivated = true">
          <div class="label">新增密钥</div>
        </button>
      </div>
      <div class="content">
        <div class="item" v-for="{id, name} of keys" :key="id">
          <div class="wrapper">
            <div class="name ellipsis">{{ name }}</div>
          </div>
          <div class="operation">
            <button :class="{del: true, doing: deletings[name]}" @click="del(name)"></button>
          </div>
        </div>
      </div>
    </div>
    <sk-editor v-if="creationActivated" @closed="creationActivated = false" :ns="ns" @completed="handleKeyAdd"/>
  </div>
</template>

<script lang="ts">
import { defineComponent, getCurrentInstance, onBeforeMount, ref } from 'vue';
import SkEditor from './sk-editor.vue';
import { deleteSecretKey } from '@/api/secret-key';
import { fetchNamespaceDetail, listSecretKey } from '@/api/view-no-auth';
import { v4 as uuidv4 } from 'uuid';

interface IKeyType {
  id: string;
  name: string;
}

export default defineComponent({
  components: {
    SkEditor,
  },
  props: {
    ns: {
      type: String,
      required: true,
    },
  },
  setup(props: any) {
    const { proxy } = getCurrentInstance() as any;
    const description = ref<string>('无');
    const keys = ref<IKeyType[]>([]);
    const loading = ref<boolean>(false);
    const creationActivated = ref<boolean>(false);
    const deletings = ref<{ [name: string]: boolean }>({});

    // 初始化密钥key列表
    onBeforeMount(async () => {
      try {
        loading.value = true;

        const { description: desc } = await fetchNamespaceDetail(props.ns);
        description.value = (desc || '无').replaceAll('\n', '<br/>');

        keys.value = (await listSecretKey(props.ns)).map(item => ({ id: uuidv4(), name: item }));
      } catch (err) {
        proxy.$throw(err, proxy);
      } finally {
        loading.value = false;
      }
    });

    return {
      description,
      keys,
      loading,
      creationActivated,
      deletings,

      handleKeyAdd: (namespace: string, name: string) => {
        keys.value.push({ id: uuidv4(), name });
      },
      del: (name: string) => {
        if (deletings.value[name]) {
          return;
        }

        let msg = '<div>确定要删除密钥吗?</div>';
        msg += `<div style="margin-top: 5px; font-size: 12px; line-height: normal;">名称：${name}</div>`;

        proxy.$confirm(msg, '删除密钥', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true,
        }).then(() => {
          deletings.value[name] = true;

          deleteSecretKey(props.ns, name).then(() => {
            proxy.$success('删除成功');

            delete deletings.value[name];

            const index = keys.value.findIndex(item => item.name === name);
            keys.value.splice(index, 1);
          }).catch((err: Error) => {
            proxy.$throw(err, proxy);

            delete deletings.value[name];
          });
        }).catch(() => {
        });
      },
    };
  },
});
</script>

<style scoped lang="less">
.secret-key-sk-manager {
  font-size: 14px;
  color: #333333;
  margin-bottom: 25px;

  .right-top-btn {
    position: fixed;
    right: 20px;
    top: 78px;

    .jm-icon-button-cancel::before {
      font-weight: bold;
    }
  }

  .namespace {
    margin-bottom: 20px;
    padding: 25px 0 25px 115px;
    min-height: 64px;
    background-color: #FFFFFF;
    box-shadow: 0 0 8px 0 #9EB1C5;
    background-image: url('@/assets/svgs/secret-key/key-icon.svg');
    background-repeat: no-repeat;
    background-position: 25px 25px;

    .name {
      font-size: 24px;
      font-weight: bold;
      color: #082340;
      margin-bottom: 5px;
    }

    .desc {
      font-size: 14px;
      color: #6B7B8D;
    }
  }

  .keys {
    padding: 15px;
    background-color: #FFFFFF;

    .title {
      font-size: 18px;
      font-weight: bold;
      color: #082340;
      margin-left: 0.5%;
      margin-bottom: 25px;

      .desc {
        font-weight: normal;
        margin-left: 12px;
        font-size: 14px;
        color: #082340;
        opacity: 0.46;
      }
    }

    .menu-bar {
      margin-bottom: 25px;

      button {
        position: relative;

        .label {
          position: absolute;
          left: 0;
          bottom: 40px;
          width: 100%;
          text-align: center;
          font-size: 18px;
          color: #B5BDC6;
        }

        &.add {
          margin: 0.5%;
          width: 19%;
          min-width: 260px;
          height: 170px;
          background-color: #FFFFFF;
          border: 1px dashed #B5BDC6;
          background-image: url('@/assets/svgs/btn/add.svg');
          background-position: center 45px;
          background-repeat: no-repeat;
          cursor: pointer;
        }
      }
    }

    .content {
      display: flex;
      flex-wrap: wrap;

      .item {
        position: relative;
        margin: 0.5%;
        width: 19%;
        min-width: 260px;
        height: 170px;
        background-image: url('@/assets/svgs/secret-key/key-icon.svg');
        background-repeat: no-repeat;
        background-position: center 40px;
        background-color: #FFFFFF;
        box-shadow: 0 0 8px 0 #9EB1C5;

        &:hover {
          box-shadow: 0 0 12px 0 #9EB1C5;

          .operation {
            display: block;
          }
        }

        .wrapper {
          padding: 15px;
          border: 1px solid transparent;
          height: 138px;

          &:hover {
            border-color: #096DD9;
          }

          .name {
            margin-top: 100px;
            font-size: 20px;
            font-weight: bold;
            color: #082340;
            text-align: center;
          }

          .ellipsis {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }

        .operation {
          display: none;
          position: absolute;
          right: 6px;
          top: 8px;

          button {
            width: 22px;
            height: 22px;
            background-color: #FFFFFF;
            border: 0;
            background-position: center center;
            background-repeat: no-repeat;
            background-size: contain;
            cursor: pointer;

            &:active {
              background-color: #EFF7FF;
              border-radius: 4px;
            }

            &.del {
              background-image: url('@/assets/svgs/btn/del.svg');
            }

            &.doing {
              opacity: 0.5;
              cursor: not-allowed;

              &:active {
                background-color: transparent;
              }
            }
          }
        }
      }
    }
  }
}
</style>
