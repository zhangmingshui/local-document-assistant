<script setup>
import ProgressBar from './ProgressBar.vue';
import StatusBadge from './StatusBadge.vue';

defineProps({
  sourceJob: {
    type: Object,
    default: null
  },
  displayedJob: {
    type: Object,
    default: null
  },
  summary: {
    type: Object,
    default: null
  },
  statusBadgeLabel: {
    type: String,
    default: ''
  },
  isComplete: {
    type: Boolean,
    default: false
  },
  isFailed: {
    type: Boolean,
    default: false
  },
  isRefreshing: {
    type: Boolean,
    default: false
  },
  isPolling: {
    type: Boolean,
    default: false
  },
  lastRefreshedAt: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
  failureMessage: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['refresh', 'stop-polling']);
</script>

<template>
  <section class="card">
    <div class="section-heading split-heading">
      <div>
        <p class="eyebrow">Step 2</p>
        <h2>Process documents</h2>
        <p>Status refreshes automatically every 3 seconds after a mock job starts.</p>
      </div>
      <StatusBadge :label="statusBadgeLabel" :complete="isComplete" />
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
        <button type="button" :disabled="!sourceJob || isRefreshing" @click="emit('refresh')">
          {{ isRefreshing ? 'Refreshing...' : 'Refresh status' }}
        </button>
        <button
          v-if="isPolling"
          type="button"
          class="secondary-action"
          @click="emit('stop-polling')"
        >
          Stop polling
        </button>
      </div>
    </div>

    <p class="last-refreshed">
      Last refreshed: {{ lastRefreshedAt || 'Not refreshed yet' }}
    </p>

    <p v-if="error" class="source-error">{{ error }}</p>
    <p v-if="isFailed && failureMessage" class="processing-error">
      {{ failureMessage }}
    </p>

    <div v-if="!sourceJob" class="empty-state">
      Configure a source in Step 1 to create a mock processing job before refreshing status.
    </div>

    <div v-else-if="!displayedJob" class="empty-state">
      Mock job created. Status will load automatically; you can also refresh once manually.
    </div>

    <template v-if="displayedJob && summary">
      <div v-if="!isFailed" class="job-summary">
        <div>
          <span class="label">Current step</span>
          <strong>{{ summary.currentStep }}</strong>
        </div>
      </div>

      <template v-if="!isFailed">
        <div class="progress-header">
          <span>{{ displayedJob.progressPercent }}% complete</span>
          <strong>{{ displayedJob.processedFiles }} / {{ displayedJob.totalFiles }} files</strong>
        </div>

        <ProgressBar :value="displayedJob.progressPercent" />
      </template>

      <p v-else class="empty-state">
        No files were processed.
      </p>

      <p v-if="isComplete" class="completed-message">
        Mock processing completed. Polling has stopped; you can still refresh manually.
      </p>

      <div v-if="!isFailed" class="status-grid">
        <div>
          <span class="label">Successful</span>
          <strong>{{ summary.successful }}</strong>
        </div>
        <div>
          <span class="label">Failed</span>
          <strong>{{ summary.failed }}</strong>
        </div>
        <div>
          <span class="label">Skipped</span>
          <strong>{{ summary.skipped }}</strong>
        </div>
      </div>
    </template>
  </section>
</template>
