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
  },
  activeFolderId: {
    type: Number,
    default: null
  },
  documents: {
    type: Array,
    default: () => []
  },
  documentsTotal: {
    type: Number,
    default: 0
  },
  documentsHasMore: {
    type: Boolean,
    default: false
  },
  documentsVisible: {
    type: Boolean,
    default: false
  },
  isLoadingDocuments: {
    type: Boolean,
    default: false
  },
  documentsError: {
    type: String,
    default: ''
  }
});

const emit = defineEmits([
  'refresh',
  'stop-polling',
  'view-documents',
  'load-more-documents'
]);

function formatFileSize(bytes) {
  if (bytes === null || bytes === undefined) {
    return 'Unknown';
  }

  if (bytes < 1024) {
    return `${bytes} B`;
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
</script>

<template>
  <section class="card">
    <div class="section-heading split-heading">
      <div>
        <p class="eyebrow">Step 2</p>
        <h2>Process documents</h2>
        <p>Status refreshes automatically every 3 seconds after a processing job starts.</p>
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
        <strong>{{ sourceJob?.pollUrl ?? 'Start a processing job to get a poll URL' }}</strong>
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
      Configure a source in Step 1 to create a processing job before refreshing status.
    </div>

    <div v-else-if="!displayedJob" class="empty-state">
      Processing job created. Status will load automatically; you can also refresh once manually.
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
        Processing completed. Polling has stopped; you can still refresh manually.
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

      <div class="documents-panel">
        <p
          v-if="['PENDING', 'RUNNING', 'SCANNING'].includes(displayedJob.status)"
          class="helper-message"
        >
          Documents will be available when processing completes.
        </p>

        <template v-else>
          <p v-if="displayedJob.status === 'COMPLETED_WITH_ERRORS'" class="documents-warning">
            Processing completed with errors. The successfully discovered documents are still available.
          </p>

          <button
            v-if="!documentsVisible"
            type="button"
            class="documents-action"
            :disabled="!activeFolderId || isLoadingDocuments"
            @click="emit('view-documents')"
          >
            {{ isLoadingDocuments ? 'Loading...' : 'View documents' }}
          </button>

          <p v-if="documentsError" class="source-error">{{ documentsError }}</p>

          <template v-if="documentsVisible">
            <div class="documents-heading">
              <h3>Discovered documents</h3>
              <span>{{ documentsTotal }} total</span>
            </div>

            <div class="documents-table-wrap">
              <table class="documents-table">
                <thead>
                  <tr>
                    <th>File name</th>
                    <th>Type</th>
                    <th>Size</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="document in documents" :key="document.id">
                    <td>
                      <strong>{{ document.fileName }}</strong>
                      <span>{{ document.filePath }}</span>
                    </td>
                    <td>{{ document.fileType || 'Unknown' }}</td>
                    <td>{{ formatFileSize(document.fileSize) }}</td>
                    <td>{{ document.processingStatus }}</td>
                  </tr>
                  <tr v-if="documents.length === 0">
                    <td colspan="4">No .doc or .txt documents were discovered.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <button
              v-if="documentsHasMore"
              type="button"
              class="documents-action"
              :disabled="isLoadingDocuments"
              @click="emit('load-more-documents')"
            >
              {{ isLoadingDocuments ? 'Loading...' : 'Load more' }}
            </button>
          </template>
        </template>
      </div>
    </template>
  </section>
</template>
