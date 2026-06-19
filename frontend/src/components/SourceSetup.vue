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
  }
});

const emit = defineEmits(['start-processing']);

const sourcePath = ref('/Users/example/Documents/LocalDocs');
const includeSubfolders = ref(true);

function submitSource() {
  emit('start-processing', {
    path: sourcePath.value,
    includeSubfolders: includeSubfolders.value
  });
}
</script>

<template>
  <section class="card source-card">
    <div class="section-heading">
      <p class="eyebrow">Step 1</p>
      <h2>Configure source</h2>
      <p>Enter the folder path the real app will eventually index. This prototype sends the path as a mock request only.</p>
    </div>

    <form class="source-form" @submit.prevent="submitSource">
      <label for="source-path">Folder path</label>
      <div class="source-path-row">
        <input
          id="source-path"
          v-model="sourcePath"
          name="sourcePath"
          autocomplete="off"
        />
        <button type="submit" :disabled="loading">
          {{ loading ? 'Starting...' : 'Use this folder' }}
        </button>
      </div>

      <label class="checkbox-row">
        <input v-model="includeSubfolders" type="checkbox" />
        <span>Include subfolders</span>
      </label>

      <p v-if="error" class="source-error">{{ error }}</p>

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
</template>
