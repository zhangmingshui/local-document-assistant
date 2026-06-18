<script setup>
import { onMounted, ref } from 'vue';

const folders = ref([]);
const latestJob = ref(null);
const question = ref('What changed in the latest mock project notes?');
const answer = ref(null);
const isAsking = ref(false);
const error = ref('');

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

onMounted(loadDashboard);
</script>

<template>
  <main class="app-shell">
    <header class="masthead">
      <p class="eyebrow">Mac-first mock prototype</p>
      <h1>Local Document Assistant</h1>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="section">
      <div>
        <p class="eyebrow">Setup</p>
        <h2>Document source</h2>
      </div>

      <div class="folder-list">
        <article v-for="folder in folders" :key="folder.id" class="folder-row">
          <div>
            <strong>{{ folder.path }}</strong>
            <span>{{ folder.documentCount }} mocked documents</span>
          </div>
        </article>
      </div>
    </section>

    <section class="section">
      <div>
        <p class="eyebrow">Processing</p>
        <h2>Status</h2>
      </div>

      <div v-if="latestJob" class="status-grid">
        <div>
          <span class="label">Job</span>
          <strong>{{ latestJob.name }}</strong>
        </div>
        <div>
          <span class="label">State</span>
          <strong>{{ latestJob.status }}</strong>
        </div>
        <div>
          <span class="label">Documents</span>
          <strong>{{ latestJob.processedDocuments }} / {{ latestJob.totalDocuments }}</strong>
        </div>
      </div>

      <div v-if="latestJob" class="progress-track" aria-label="Processing progress">
        <div class="progress-fill" :style="{ width: `${latestJob.progressPercent}%` }"></div>
      </div>
    </section>

    <section class="section ask-section">
      <div>
        <p class="eyebrow">Ask</p>
        <h2>Answer</h2>
      </div>

      <form class="question-form" @submit.prevent="askQuestion">
        <input v-model="question" name="question" autocomplete="off" />
        <button type="submit" :disabled="isAsking">
          {{ isAsking ? 'Asking...' : 'Ask' }}
        </button>
      </form>

      <article v-if="answer" class="answer">
        <p>{{ answer.answer }}</p>
        <div class="citations">
          <span v-for="citation in answer.citations" :key="citation.path">
            {{ citation.title }} · {{ citation.path }}
          </span>
        </div>
      </article>
    </section>
  </main>
</template>
