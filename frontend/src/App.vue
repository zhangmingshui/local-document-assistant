<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { askQuestion as askQuestionApi } from './api/questionsApi';
import {
  getFolders,
  getProcessingJobStatus,
  startProcessingJob
} from './api/processingJobsApi';
import AskAnswer from './components/AskAnswer.vue';
import ProcessingStatus from './components/ProcessingStatus.vue';
import SourceSetup from './components/SourceSetup.vue';

const folders = ref([]);
const sourceJob = ref(null);
const isStartingSourceJob = ref(false);
const sourceError = ref('');

const processingStatusResponse = ref(null);
const isRefreshingStatus = ref(false);
const isPollingStatus = ref(false);
const pollingTimerId = ref(null);
const lastRefreshedAt = ref('');
const refreshError = ref('');

const answer = ref(null);
const isAsking = ref(false);
const questionError = ref('');
const error = ref('');

const displayedProcessingJob = computed(() => processingStatusResponse.value);
const statusBadgeLabel = computed(() => displayedProcessingJob.value?.status ?? sourceJob.value?.status);
const isProcessingComplete = computed(() => (
  displayedProcessingJob.value?.status?.startsWith('COMPLETED') ?? false
));

const processingSummary = computed(() => {
  if (!displayedProcessingJob.value) {
    return null;
  }

  return {
    successful: displayedProcessingJob.value.successfulFiles,
    failed: displayedProcessingJob.value.failedFiles,
    skipped: displayedProcessingJob.value.skippedFiles,
    currentStep: displayedProcessingJob.value.currentStep
  };
});

async function loadFolders() {
  error.value = '';

  try {
    folders.value = await getFolders();
  } catch (requestError) {
    error.value = requestError.message;
  }
}

async function useMockSourceFolder(payload) {
  sourceError.value = '';
  sourceJob.value = null;
  processingStatusResponse.value = null;
  stopPollingStatus();
  lastRefreshedAt.value = '';
  refreshError.value = '';
  isStartingSourceJob.value = true;

  try {
    sourceJob.value = await startProcessingJob(payload);
    await loadFolders();
  } catch (requestError) {
    sourceError.value = requestError.message;
  } finally {
    isStartingSourceJob.value = false;
  }
}

async function refreshProcessingStatus() {
  if (!sourceJob.value?.pollUrl) {
    refreshError.value = 'Start a mock processing job before refreshing status.';
    return;
  }

  if (isRefreshingStatus.value) {
    return;
  }

  refreshError.value = '';
  isRefreshingStatus.value = true;

  try {
    processingStatusResponse.value = await getProcessingJobStatus(sourceJob.value);
    lastRefreshedAt.value = new Date().toLocaleTimeString();

    if (processingStatusResponse.value.status?.startsWith('COMPLETED')) {
      stopPollingStatus();
    }
  } catch (requestError) {
    refreshError.value = requestError.message;
    stopPollingStatus();
  } finally {
    isRefreshingStatus.value = false;
  }
}

function startPollingStatus() {
  if (!sourceJob.value?.pollUrl || pollingTimerId.value || isProcessingComplete.value) {
    return;
  }

  isPollingStatus.value = true;
  refreshProcessingStatus();
  pollingTimerId.value = window.setInterval(refreshProcessingStatus, 3000);
}

function stopPollingStatus() {
  if (pollingTimerId.value) {
    window.clearInterval(pollingTimerId.value);
    pollingTimerId.value = null;
  }

  isPollingStatus.value = false;
}

async function askQuestion(question) {
  questionError.value = '';
  isAsking.value = true;

  try {
    answer.value = await askQuestionApi({ question });
  } catch (requestError) {
    questionError.value = requestError.message;
  } finally {
    isAsking.value = false;
  }
}

onMounted(loadFolders);
onUnmounted(stopPollingStatus);
</script>

<template>
  <main class="app-shell">
    <header class="app-header">
      <div>
        <p class="eyebrow">Mac-first mock prototype</p>
        <h1>Local Document Assistant</h1>
        <p class="app-subtitle">A small mocked workflow for configuring a source, processing it, and asking questions.</p>
      </div>
      <span class="header-pill">Mock data only</span>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <SourceSetup
      :folders="folders"
      :source-job="sourceJob"
      :loading="isStartingSourceJob"
      :error="sourceError"
      @start-processing="useMockSourceFolder"
      @clear-error="sourceError = ''"
    />

    <ProcessingStatus
      :source-job="sourceJob"
      :displayed-job="displayedProcessingJob"
      :summary="processingSummary"
      :status-badge-label="statusBadgeLabel"
      :is-complete="isProcessingComplete"
      :is-refreshing="isRefreshingStatus"
      :is-polling="isPollingStatus"
      :last-refreshed-at="lastRefreshedAt"
      :error="refreshError"
      @refresh="refreshProcessingStatus"
      @start-polling="startPollingStatus"
      @stop-polling="stopPollingStatus"
    />

    <AskAnswer
      :answer="answer"
      :loading="isAsking"
      :error="questionError"
      :is-processing-complete="isProcessingComplete"
      @ask="askQuestion"
    />
  </main>
</template>
