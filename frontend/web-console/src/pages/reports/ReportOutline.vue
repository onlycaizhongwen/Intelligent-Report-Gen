<template>
  <section class="page">
    <header><h2>确认报告大纲</h2></header>
    <el-card shadow="never">
      <el-timeline>
        <el-timeline-item v-for="item in outline" :key="item">{{ item }}</el-timeline-item>
      </el-timeline>
      <el-button type="primary" :loading="submitting || streaming" @click="confirmAndStart">确认并生成正文</el-button>
    </el-card>
    <el-card v-if="confirmationText || nextStageText" shadow="never">
      <p>{{ confirmationText }}</p>
      <p>{{ nextStageText }}</p>
    </el-card>
    <el-card v-if="streamText || stageText" shadow="never">
      <p>{{ stageText }}</p>
      <p>{{ streamText }}</p>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { reportApi } from '../../api/reportApi';
import { useSseStream } from '../../composables/useSseStream';

const route = useRoute();
const outline = ['经营概览', '收入与成本分析', '风险与建议'];
const submitting = ref(false);
const confirmationText = ref('');
const nextStageText = ref('');
const stageText = ref('');
const streamText = ref('');
const { streaming, connect } = useSseStream();

const confirmAndStart = async () => {
  const taskId = String(route.params.id);
  submitting.value = true;
  confirmationText.value = '';
  nextStageText.value = '';
  try {
    const result = await reportApi.confirmOutline(taskId, { sections: outline });
    confirmationText.value = `大纲已确认：${result.taskId}`;
    nextStageText.value = `下一阶段：${result.nextStage}`;
  } finally {
    submitting.value = false;
  }
  await connect(`/api/v1/reports/generation-tasks/${taskId}/stream`, {
    onStage: (event) => {
      stageText.value = event.content;
    },
    onToken: (token) => {
      streamText.value += token;
    },
    onDone: (event) => {
      stageText.value = event?.content || '完成';
    }
  });
};
</script>
