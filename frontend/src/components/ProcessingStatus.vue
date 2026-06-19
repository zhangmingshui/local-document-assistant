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
  }
});

const emit = defineEmits(['refresh', 'start-polling', 'stop-polling']);
</script>

<template>
  <section class="card">
    <div class="section-heading split-heading">
      <div>
        <p class="eyebrow">Step 2</p>
        <h2>Process documents</h2>
        <p>Refresh once, or start polling every 3 seconds, to watch the in-memory mock job advance.</p>
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
          type="button"
          :disabled="!sourceJob || isComplete"
          class="secondary-action"
          @click="isPolling ? emit('stop-polling') : emit('start-polling')"
        >
          {{ isPolling ? 'Stop polling' : 'Start polling' }}
        </button>
      </div>
    </div>

    <p class="last-refreshed">
      Last refreshed: {{ lastRefreshedAt || 'Not refreshed yet' }}
    </p>

    <p v-if="error" class="source-error">{{ error }}</p>

    <div v-if="!sourceJob" class="empty-state">
      Configure a source in Step 1 to create a mock processing job before refreshing status.
    </div>

    <div v-else-if="!displayedJob" class="empty-state">
      Mock job created. Click Refresh status or Start polling to load the first status response.
    </div>

    <template v-if="displayedJob && summary">
      <div class="job-summary">
        <div>
          <span class="label">Latest response ID</span>
          <strong>{{ displayedJob.id }}</strong>
        </div>
        <div>
          <span class="label">Job</span>
          <strong>{{ displayedJob.name }}</strong>
        </div>
        <div>
          <span class="label">Current step</span>
          <strong>{{ summary.currentStep }}</strong>
        </div>
      </div>

      <div class="progress-header">
        <span>{{ displayedJob.progressPercent }}% complete</span>
        <strong>{{ displayedJob.processedFiles }} / {{ displayedJob.totalFiles }} files</strong>
      </div>

      <ProgressBar :value="displayedJob.progressPercent" />

      <p v-if="isComplete" class="completed-message">
        Mock processing completed. Polling has stopped; you can still refresh manually.
      </p>

      <div class="status-grid">
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
