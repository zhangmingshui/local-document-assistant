<script setup>
defineProps({
  label: {
    type: String,
    default: ''
  },
  complete: {
    type: Boolean,
    default: false
  }
});
</script>

<template>
  <span v-if="label" class="status-badge" :class="{ complete }">
    {{ label }}
  </span>
</template>
