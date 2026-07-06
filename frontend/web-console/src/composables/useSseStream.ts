import { ref } from 'vue';
import type { UnifiedSseEvent } from '../api/types';

export const useSseStream = () => {
  const streaming = ref(false);
  const error = ref<string | null>(null);
  let controller: AbortController | null = null;

  const connect = async (
    url: string,
    handlers: {
      onToken: (token: string) => void;
      onDone: (payload?: UnifiedSseEvent) => void;
      onStage?: (payload: UnifiedSseEvent) => void;
      onReferences?: (payload: UnifiedSseEvent) => void;
      onError?: (payload: UnifiedSseEvent) => void;
    }
  ) => {
    controller = new AbortController();
    streaming.value = true;
    error.value = null;
    try {
      const response = await fetch(url, { signal: controller.signal, credentials: 'include' });
      const reader = response.body?.getReader();
      if (!reader) throw new Error('流式响应不可读');
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value);
        for (const line of chunk.split('\n')) {
          if (!line.startsWith('data: ')) continue;
          const data = JSON.parse(line.slice(6)) as UnifiedSseEvent;
          if (data.type === 'stage') handlers.onStage?.(data);
          if (data.type === 'delta') handlers.onToken(data.content);
          if (data.type === 'references') handlers.onReferences?.(data);
          if (data.type === 'error') {
            error.value = data.content || data.errorCode || '流式响应错误';
            handlers.onError?.(data);
          }
          if (data.type === 'done') handlers.onDone(data);
        }
      }
    } catch (err) {
      if ((err as Error).name !== 'AbortError') error.value = (err as Error).message || '流式响应中断';
    } finally {
      streaming.value = false;
    }
  };

  const disconnect = () => controller?.abort();

  return { streaming, error, connect, disconnect };
};
