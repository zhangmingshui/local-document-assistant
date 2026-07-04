<script setup>
import { ref } from 'vue';

defineProps({
  folders: {
    type: Array,
    default: () => []
  },
  sourceJob: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  rescanError: {
    type: String,
    default: ''
  },
  rescanningFolderId: {
    type: Number,
    default: null
  }
});

const emit = defineEmits([
  'start-processing',
  'process-existing-folder',
  'clear-error',
  'clear-rescan-error'
]);

const sourcePath = ref('/Users/ste/tempDocs');
const includeSubfolders = ref(true);

function submitSource() {
  emit('start-processing', {
    path: sourcePath.value,
    includeSubfolders: includeSubfolders.value
  });
}

function processExistingFolder(folderId) {
  emit('clear-rescan-error');
  emit('process-existing-folder', folderId);
}
</script>

<template>
  <section class="card source-card">
    <div class="section-heading">
      <p class="eyebrow">Step 1</p>
      <h2>Configure source</h2>
      <p>Enter the folder path to scan for supported documents.</p>
    </div>

    <form class="source-form" @submit.prevent="submitSource">
      <label for="source-path">Folder path</label>
      <div class="source-path-row">
        <input
          id="source-path"
          v-model="sourcePath"
          name="sourcePath"
          autocomplete="off"
          @input="emit('clear-error')"
        />
        <button type="submit" :disabled="loading">
          {{ loading ? 'Starting...' : 'Use this folder' }}
        </button>
      </div>

      <p v-if="error" class="source-error">{{ error }}</p>

      <label class="checkbox-row">
        <input v-model="includeSubfolders" type="checkbox" />
        <span>Include subfolders</span>
      </label>

      <p v-if="sourceJob" class="confirmation-message">
        Processing started. See Step 2 for progress.
      </p>
    </form>

    <div class="folder-list">
      <article v-for="folder in folders" :key="folder.id" class="folder-row">
        <span class="folder-icon" aria-hidden="true"></span>
        <div class="folder-copy">
          <strong>{{ folder.path }}</strong>
          <span>{{ folder.documentCount }} documents</span>
        </div>
        <span class="folder-state">Selected</span>
        <button
          type="button"
          class="folder-action"
          :disabled="rescanningFolderId === folder.id"
          @click="processExistingFolder(folder.id)"
        >
          {{ rescanningFolderId === folder.id ? 'Starting...' : 'Process again' }}
        </button>
      </article>
    </div>

    <p v-if="rescanError" class="source-error folder-action-error">
      {{ rescanError }}
    </p>
  </section>
</template>
