<script setup>
defineProps({
  source: {
    type: Object,
    required: true
  }
});
</script>

<template>
  <article class="source-card-item">
    <strong>{{ source.fileName }}</strong>
    <span>{{ source.filePath }}</span>
    <small>Chunk {{ source.chunkNumber }}</small>
    <p>{{ source.text }}</p>
  </article>
</template>
