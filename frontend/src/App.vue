<script setup>
import { computed, onMounted, ref } from 'vue';

const folders = ref([]);
const latestJob = ref(null);
const sourcePath = ref('/Users/example/Documents/LocalDocs');
const includeSubfolders = ref(true);
const sourceJob = ref(null);
const isStartingSourceJob = ref(false);
const sourceError = ref('');
const processingStatusResponse = ref(null);
const isRefreshingStatus = ref(false);
const refreshError = ref('');
const question = ref('What changed in the latest mock project notes?');
const answer = ref(null);
const isAsking = ref(false);
const error = ref('');

const displayedProcessingJob = computed(() => processingStatusResponse.value ?? latestJob.value);

const processingSummary = computed(() => {
  if (!displayedProcessingJob.value) {
    return null;
  }

  const processed = displayedProcessingJob.value.processedDocuments;
  const failed = 2;
  const skipped = 3;
  const successful = Math.max(processed - failed - skipped, 0);

  return {
    successful,
    failed,
    skipped,
    currentStep: 'Reading mocked metadata and preparing preview records'
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
  error.value = '';
  isAsking.value = true;

  try {
    const response = await fetch('/api/questions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ question: question.value })
    });

    if (!response.ok) {
      throw new Error('Mock question request failed');
    }

    answer.value = await response.json();
  } catch (requestError) {
    error.value = requestError.message;
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
    console.log('Refreshing mock processing status response', processingStatusResponse.value);
  } catch (requestError) {
    refreshError.value = requestError.message;
  } finally {
    isRefreshingStatus.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <main class="app-shell">
    <header class="app-header">
      <div>
        <p class="eyebrow">Mac-first mock prototype</p>
        <h1>Local Document Assistant</h1>
      </div>
      <span class="header-pill">Mock data only</span>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="card source-card">
      <div class="section-heading">
        <p class="eyebrow">Setup</p>
        <h2>Document source</h2>
        <p>Choose the local folders this assistant will eventually understand. These entries are mocked.</p>
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
          <span>{{ sourceJob.pollUrl }}</span>
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
          <p class="eyebrow">Processing</p>
          <h2>Status</h2>
          <p>Manual refresh for the current mocked processing job.</p>
        </div>
        <span v-if="displayedProcessingJob" class="status-badge">{{ displayedProcessingJob.status }}</span>
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
        <button type="button" :disabled="!sourceJob || isRefreshingStatus" @click="refreshProcessingStatus">
          {{ isRefreshingStatus ? 'Refreshing...' : 'Refresh status' }}
        </button>
      </div>

      <p v-if="refreshError" class="source-error">{{ refreshError }}</p>

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
          <strong>{{ displayedProcessingJob.processedDocuments }} / {{ displayedProcessingJob.totalDocuments }} files</strong>
        </div>

        <div class="progress-track" aria-label="Processing progress">
          <div class="progress-fill" :style="{ width: `${displayedProcessingJob.progressPercent}%` }"></div>
        </div>

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
        <p class="eyebrow">Ask</p>
        <h2>Question panel</h2>
        <p>Ask a question and show a mocked answer with mocked source cards.</p>
      </div>

      <form class="chat-panel" @submit.prevent="askQuestion">
        <label for="question">Question</label>
        <textarea
          id="question"
          v-model="question"
          name="question"
          rows="4"
          autocomplete="off"
        ></textarea>
        <button type="submit" :disabled="isAsking">
          {{ isAsking ? 'Asking...' : 'Ask assistant' }}
        </button>
      </form>

      <article v-if="answer" class="answer">
        <span class="assistant-label">Mocked answer</span>
        <p>{{ answer.answer }}</p>
        <div class="source-grid">
          <article v-for="citation in answer.citations" :key="citation.path" class="source-card-item">
            <strong>{{ citation.title }}</strong>
            <span>{{ citation.path }}</span>
          </article>
        </div>
      </article>
    </section>
  </main>
</template>
