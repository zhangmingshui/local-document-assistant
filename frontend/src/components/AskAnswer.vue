<script setup>
import { computed, ref } from 'vue';
import SourceCard from './SourceCard.vue';

defineProps({
  answer: {
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
  isProcessingComplete: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['ask']);

const question = ref('What changed in the latest mock project notes?');
const isQuestionBlank = computed(() => question.value.trim().length === 0);

function submitQuestion() {
  if (isQuestionBlank.value) {
    return;
  }

  emit('ask', question.value);
}
</script>

<template>
  <section class="card ask-section">
    <div class="section-heading">
      <p class="eyebrow">Step 3</p>
      <h2>Ask questions</h2>
      <p>Ask a question and review the mocked answer with mocked source cards.</p>
    </div>

    <form class="chat-panel" @submit.prevent="submitQuestion">
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
      <button type="submit" :disabled="loading || isQuestionBlank">
        {{ loading ? 'Asking...' : 'Ask question' }}
      </button>
    </form>

    <p v-if="isQuestionBlank" class="last-refreshed">Enter a question to enable the Ask button.</p>

    <p v-if="error" class="source-error">{{ error }}</p>

    <article v-if="answer" class="answer">
      <span class="assistant-label">Mocked answer</span>
      <p>{{ answer.answer }}</p>
      <div class="source-grid">
        <SourceCard
          v-for="source in answer.sources"
          :key="`${source.filePath}-${source.chunkNumber}`"
          :source="source"
        />
      </div>
    </article>
  </section>
</template>
