<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';

const folders = ref([]);
const latestJob = ref(null);
const sourcePath = ref('/Users/example/Documents/LocalDocs');
const includeSubfolders = ref(true);
const sourceJob = ref(null);
const isStartingSourceJob = ref(false);
const sourceError = ref('');
const processingStatusResponse = ref(null);
const isRefreshingStatus = ref(false);
const isPollingStatus = ref(false);
const pollingTimerId = ref(null);
const lastRefreshedAt = ref('');
const refreshError = ref('');
const question = ref('What changed in the latest mock project notes?');
const answer = ref(null);
const isAsking = ref(false);
const questionError = ref('');
const error = ref('');

const displayedProcessingJob = computed(() => processingStatusResponse.value);
const statusBadgeLabel = computed(() => displayedProcessingJob.value?.status ?? sourceJob.value?.status);
const isProcessingComplete = computed(() => (
  displayedProcessingJob.value?.status?.startsWith('COMPLETED') ?? false
));
const isQuestionBlank = computed(() => question.value.trim().length === 0);

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

async function loadDashboard() {
  error.value = '';

  try {
    const [foldersResponse, jobResponse] = await Promise.all([
      fetch('/api/folders'),
      fetch('/api/processing-jobs/latest')
    ]);

    if (!foldersResponse.ok || !jobResponse.ok) {
      throw new Error('Mock API request failed');
    }

    folders.value = await foldersResponse.json();
    latestJob.value = await jobResponse.json();
  } catch (requestError) {
    error.value = requestError.message;
  }
}

async function askQuestion() {
  questionError.value = '';
  isAsking.value = true;
  const payload = { question: question.value };
  console.log('Mock question request', payload);

  try {
    const response = await fetch('/api/questions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const responseBody = await response.json();

    if (!response.ok) {
      throw new Error(responseBody.message ?? 'Mock question request failed');
    }

    answer.value = responseBody;
    console.log('Mock question response', answer.value);
  } catch (requestError) {
    questionError.value = requestError.message;
  } finally {
    isAsking.value = false;
  }
}

async function useMockSourceFolder() {
  const payload = {
    path: sourcePath.value,
    includeSubfolders: includeSubfolders.value
  };

  sourceError.value = '';
  sourceJob.value = null;
  processingStatusResponse.value = null;
  stopPollingStatus();
  lastRefreshedAt.value = '';
  refreshError.value = '';
  isStartingSourceJob.value = true;
  console.log('Starting mock processing job request', payload);

  try {
    const response = await fetch('/api/processing-jobs', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error('Mock processing job request failed');
    }

    sourceJob.value = await response.json();
    console.log('Starting mock processing job response', sourceJob.value);
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
  console.log('Refreshing mock processing status request', {
    jobId: sourceJob.value.jobId,
    pollUrl: sourceJob.value.pollUrl
  });

  try {
    const response = await fetch(sourceJob.value.pollUrl);

    if (!response.ok) {
      throw new Error('Mock processing status request failed');
    }

    processingStatusResponse.value = await response.json();
    lastRefreshedAt.value = new Date().toLocaleTimeString();
    console.log('Refreshing mock processing status response', processingStatusResponse.value);

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

onMounted(loadDashboard);
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

    <section class="card source-card">
      <div class="section-heading">
        <p class="eyebrow">Step 1</p>
        <h2>Configure source</h2>
        <p>Enter the folder path the real app will eventually index. This prototype sends the path as a mock request only.</p>
      </div>

      <form class="source-form" @submit.prevent="useMockSourceFolder">
        <label for="source-path">Folder path</label>
        <div class="source-path-row">
          <input
            id="source-path"
            v-model="sourcePath"
            name="sourcePath"
            autocomplete="off"
          />
          <button type="submit" :disabled="isStartingSourceJob">
            {{ isStartingSourceJob ? 'Starting...' : 'Use this folder' }}
          </button>
        </div>

        <label class="checkbox-row">
          <input v-model="includeSubfolders" type="checkbox" />
          <span>Include subfolders</span>
        </label>

        <p v-if="sourceError" class="source-error">{{ sourceError }}</p>

        <article v-if="sourceJob" class="source-job-result">
          <div>
            <span class="label">Job ID</span>
            <strong>{{ sourceJob.jobId }}</strong>
          </div>
          <div>
            <span class="label">Status</span>
            <strong>{{ sourceJob.status }}</strong>
          </div>
          <p>{{ sourceJob.message }}</p>
          <span>Ready for Step 2: {{ sourceJob.pollUrl }}</span>
        </article>
      </form>

      <div class="folder-list">
        <article v-for="folder in folders" :key="folder.id" class="folder-row">
          <span class="folder-icon" aria-hidden="true"></span>
          <div class="folder-copy">
            <strong>{{ folder.path }}</strong>
            <span>{{ folder.documentCount }} mocked documents</span>
          </div>
          <span class="folder-state">Selected</span>
        </article>
      </div>
    </section>

    <section class="card">
      <div class="section-heading split-heading">
        <div>
          <p class="eyebrow">Step 2</p>
          <h2>Process documents</h2>
          <p>Refresh once, or start polling every 3 seconds, to watch the in-memory mock job advance.</p>
        </div>
        <span v-if="statusBadgeLabel" class="status-badge" :class="{ complete: isProcessingComplete }">
          {{ statusBadgeLabel }}
        </span>
      </div>

      <div class="refresh-panel">
        <div>
          <span class="label">Current job ID</span>
          <strong>{{ sourceJob?.jobId ?? 'No started job yet' }}</strong>
        </div>
        <div>
          <span class="label">Poll URL</span>
          <strong>{{ sourceJob?.pollUrl ?? 'Start a mocked job to get a poll URL' }}</strong>
        </div>
        <div class="refresh-actions">
          <button type="button" :disabled="!sourceJob || isRefreshingStatus" @click="refreshProcessingStatus">
            {{ isRefreshingStatus ? 'Refreshing...' : 'Refresh status' }}
          </button>
          <button
            type="button"
            :disabled="!sourceJob || isProcessingComplete"
            class="secondary-action"
            @click="isPollingStatus ? stopPollingStatus() : startPollingStatus()"
          >
            {{ isPollingStatus ? 'Stop polling' : 'Start polling' }}
          </button>
        </div>
      </div>

      <p class="last-refreshed">
        Last refreshed: {{ lastRefreshedAt || 'Not refreshed yet' }}
      </p>

      <p v-if="refreshError" class="source-error">{{ refreshError }}</p>

      <div v-if="!sourceJob" class="empty-state">
        Configure a source in Step 1 to create a mock processing job before refreshing status.
      </div>

      <div v-else-if="!displayedProcessingJob" class="empty-state">
        Mock job created. Click Refresh status or Start polling to load the first status response.
      </div>

      <template v-if="displayedProcessingJob && processingSummary">
        <div class="job-summary">
          <div>
            <span class="label">Latest response ID</span>
            <strong>{{ displayedProcessingJob.id }}</strong>
          </div>
          <div>
            <span class="label">Job</span>
            <strong>{{ displayedProcessingJob.name }}</strong>
          </div>
          <div>
            <span class="label">Current step</span>
            <strong>{{ processingSummary.currentStep }}</strong>
          </div>
        </div>

        <div class="progress-header">
          <span>{{ displayedProcessingJob.progressPercent }}% complete</span>
          <strong>{{ displayedProcessingJob.processedFiles }} / {{ displayedProcessingJob.totalFiles }} files</strong>
        </div>

        <div class="progress-track" aria-label="Processing progress">
          <div class="progress-fill" :style="{ width: `${displayedProcessingJob.progressPercent}%` }"></div>
        </div>

        <p v-if="isProcessingComplete" class="completed-message">
          Mock processing completed. Polling has stopped; you can still refresh manually.
        </p>

        <div class="status-grid">
          <div>
            <span class="label">Successful</span>
            <strong>{{ processingSummary.successful }}</strong>
          </div>
          <div>
            <span class="label">Failed</span>
            <strong>{{ processingSummary.failed }}</strong>
          </div>
          <div>
            <span class="label">Skipped</span>
            <strong>{{ processingSummary.skipped }}</strong>
          </div>
        </div>
      </template>
    </section>

    <section class="card ask-section">
      <div class="section-heading">
        <p class="eyebrow">Step 3</p>
        <h2>Ask questions</h2>
        <p>Ask a question and review the mocked answer with mocked source cards.</p>
      </div>

      <form class="chat-panel" @submit.prevent="askQuestion">
        <label for="question">Question</label>
        <p v-if="!isProcessingComplete" class="helper-message">
          This is currently mocked. In the real app, answers will use indexed documents after processing completes.
        </p>
        <p v-else class="ready-message">
          Mock processing is complete. Answers are still mocked, but this is the intended point to ask.
        </p>
        <textarea
          id="question"
          v-model="question"
          name="question"
          rows="4"
          autocomplete="off"
        ></textarea>
        <button type="submit" :disabled="isAsking || isQuestionBlank">
          {{ isAsking ? 'Asking...' : 'Ask question' }}
        </button>
      </form>

      <p v-if="isQuestionBlank" class="last-refreshed">Enter a question to enable the Ask button.</p>

      <p v-if="questionError" class="source-error">{{ questionError }}</p>

      <article v-if="answer" class="answer">
        <span class="assistant-label">Mocked answer</span>
        <p>{{ answer.answer }}</p>
        <div class="source-grid">
          <article v-for="source in answer.sources" :key="`${source.filePath}-${source.chunkNumber}`" class="source-card-item">
            <strong>{{ source.fileName }}</strong>
            <span>{{ source.filePath }}</span>
            <small>Chunk {{ source.chunkNumber }}</small>
            <p>{{ source.text }}</p>
          </article>
        </div>
      </article>
    </section>
  </main>
</template>
