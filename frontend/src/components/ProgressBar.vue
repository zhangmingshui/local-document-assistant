<script setup>
defineProps({
  value: {
    type: Number,
    default: 0
  }
});
</script>

<template>
  <div class="progress-track" aria-label="Processing progress">
    <div class="progress-fill" :style="{ width: `${value}%` }"></div>
  </div>
</template>
