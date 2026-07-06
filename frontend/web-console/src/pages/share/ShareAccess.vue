<template>
  <section class="share-page">
    <div class="share-panel" v-if="!report">
      <h2>报告分享访问</h2>
      <el-form label-position="top">
        <el-form-item label="访问密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item v-if="challengeRequired" label="访问验证">
          <el-input v-model="challengeAnswer" aria-label="访问验证" placeholder="REPORT" />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="access">访问报告</el-button>
      </el-form>
      <el-alert v-if="message" :title="message" type="error" :closable="false" />
    </div>

    <article v-else class="report-view">
      <div class="external-watermark" data-testid="external-share-watermark" aria-hidden="true">
        <span v-for="index in 12" :key="index">{{ watermarkText }}</span>
      </div>
      <header class="report-header">
        <div>
          <p class="eyebrow">外部只读报告</p>
          <h1>{{ report.title }}</h1>
        </div>
        <el-tag type="success">{{ report.status }}</el-tag>
      </header>

      <section v-if="report.allowDownload && report.exports.length" class="download-band">
        <div>
          <h2>可下载文件</h2>
          <p>下载链接会在访问时重新校验分享密码并生成短期 URL。</p>
        </div>
        <div class="download-list">
          <el-button
            v-for="item in report.exports"
            :key="String(item.exportFileId)"
            :loading="downloadingId === String(item.exportFileId)"
            @click="download(item)"
          >
            下载 {{ item.fileName }}
          </el-button>
        </div>
      </section>

      <section v-for="(section, index) in report.sections" :key="index" class="report-section">
        <h2>{{ section.heading || `章节 ${index + 1}` }}</h2>
        <p>{{ section.content }}</p>
        <div v-if="section.citations?.length" class="citations">
          <span v-for="citation in section.citations" :key="String(citation)">{{ citation }}</span>
        </div>
      </section>
      <el-empty v-if="!report.sections.length" description="暂无报告正文" />
      <el-alert v-if="message" :title="message" type="error" :closable="false" />
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { shareApi, type SharedExportSummary, type SharedReportDetail } from '../../api/shareApi';

const route = useRoute();
const password = ref('');
const challengeAnswer = ref('');
const challengeRequired = ref(false);
const message = ref('');
const loading = ref(false);
const downloadingId = ref('');
const report = ref<SharedReportDetail | null>(null);
const watermarkText = computed(() => {
  if (!report.value) return '';
  return `外部只读 · 报告 ${report.value.reportId} · ${report.value.shareToken}`;
});

const access = async () => {
  loading.value = true;
  message.value = '';
  try {
    report.value = await shareApi.getSharedReport(String(route.params.token), {
      password: password.value,
      challengeAnswer: challengeRequired.value ? challengeAnswer.value : undefined
    });
    challengeRequired.value = false;
  } catch (error) {
    report.value = null;
    if ((error as Error).message?.toLowerCase().includes('share access challenge required')) {
      challengeRequired.value = true;
      message.value = '需要完成访问验证，请输入 REPORT 后继续';
      return;
    }
    if ((error as Error).message?.toLowerCase().includes('share access rate limited')) {
      message.value = '尝试次数过多，请稍后再试';
      return;
    }
    message.value = (error as Error).message || '拒绝访问';
  } finally {
    loading.value = false;
  }
};

const download = async (item: SharedExportSummary) => {
  downloadingId.value = String(item.exportFileId);
  message.value = '';
  try {
    const result = await shareApi.getSharedExportDownloadUrl(String(route.params.token), String(item.exportFileId), {
      password: password.value,
      challengeAnswer: challengeRequired.value ? challengeAnswer.value : undefined
    });
    window.location.assign(result.downloadUrl);
  } catch (error) {
    if ((error as Error).message?.toLowerCase().includes('share access challenge required')) {
      challengeRequired.value = true;
      message.value = '需要完成访问验证，请输入 REPORT 后继续';
      return;
    }
    if ((error as Error).message?.toLowerCase().includes('share access rate limited')) {
      message.value = '尝试次数过多，请稍后再试';
      return;
    }
    message.value = (error as Error).message || '下载失败';
  } finally {
    downloadingId.value = '';
  }
};
</script>

<style scoped>
.share-page {
  min-height: 100%;
  padding: 48px 24px;
  background: #f7f8fa;
}

.share-panel {
  max-width: 420px;
  margin: 8vh auto;
  display: grid;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.share-panel h2 {
  margin: 0;
  font-size: 22px;
}

.report-view {
  position: relative;
  max-width: 920px;
  margin: 0 auto;
  display: grid;
  gap: 20px;
}

.external-watermark {
  pointer-events: none;
  position: fixed;
  inset: 0;
  z-index: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  align-content: center;
  gap: 64px 24px;
  padding: 56px;
  color: rgba(49, 80, 120, 0.11);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  overflow: hidden;
  user-select: none;
}

.external-watermark span {
  transform: rotate(-24deg);
  white-space: nowrap;
}

.report-header,
.download-band,
.report-section,
.report-view :deep(.el-empty),
.report-view :deep(.el-alert) {
  position: relative;
  z-index: 1;
}

.report-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #d8dde5;
}

.report-header h1 {
  margin: 4px 0 0;
  font-size: 28px;
  line-height: 1.25;
}

.eyebrow {
  margin: 0;
  color: #5f6b7a;
  font-size: 13px;
}

.download-band {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #e5e7eb;
}

.download-band h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.download-band p {
  margin: 0;
  color: #5f6b7a;
  line-height: 1.6;
}

.download-list {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.report-section {
  padding: 20px 0;
  border-bottom: 1px solid #e5e7eb;
}

.report-section h2 {
  margin: 0 0 10px;
  font-size: 20px;
}

.report-section p {
  margin: 0;
  color: #303846;
  line-height: 1.8;
  white-space: pre-wrap;
}

.citations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.citations span {
  padding: 4px 8px;
  color: #315078;
  background: #edf4ff;
  border-radius: 4px;
  font-size: 12px;
}

@media (max-width: 720px) {
  .download-band,
  .report-header {
    display: grid;
  }

  .download-list {
    justify-content: flex-start;
  }
}
</style>
