import { defineStore } from 'pinia';
import { ref } from 'vue';
import { reportApi } from '../api/reportApi';

type Stage = 'idle' | 'retrieving' | 'analyzing' | 'generating' | 'done' | 'failed';

export const useReportGenerationStore = defineStore('reportGeneration', () => {
  const taskId = ref<string | null>(null);
  const stage = ref<Stage>('idle');
  const streamingText = ref('');
  const error = ref<string | null>(null);

  const start = async (payload: { topic: string; focus?: string }) => {
    error.value = null;
    const task = await reportApi.createTask(payload);
    taskId.value = task.taskId;
    stage.value = 'retrieving';
  };

  const appendToken = (content: string) => {
    streamingText.value += content;
  };

  const fail = (message: string) => {
    stage.value = 'failed';
    error.value = message;
  };

  const reset = () => {
    taskId.value = null;
    stage.value = 'idle';
    streamingText.value = '';
    error.value = null;
  };

  return { taskId, stage, streamingText, error, start, appendToken, fail, reset };
});
