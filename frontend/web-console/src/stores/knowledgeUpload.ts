import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useKnowledgeUploadStore = defineStore('knowledgeUpload', () => {
  const uploading = ref(false);
  const parseStatus = ref<'idle' | 'pending' | 'processing' | 'processed' | 'failed'>('idle');
  const error = ref<string | null>(null);

  const markUploading = () => {
    uploading.value = true;
    error.value = null;
  };

  const markFinished = (status: 'pending' | 'processing' | 'processed' | 'failed') => {
    uploading.value = false;
    parseStatus.value = status;
  };

  const fail = (message: string) => {
    uploading.value = false;
    parseStatus.value = 'failed';
    error.value = message;
  };

  return { uploading, parseStatus, error, markUploading, markFinished, fail };
});
