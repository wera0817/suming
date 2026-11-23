<script setup lang="ts">
import type { Hex } from './api'
defineProps<{ hex: Hex; moving?: number; label: string }>()
</script>
<template>
  <figure class="hexagram">
    <figcaption>{{ label }}</figcaption>
    <div class="yao-stack" :aria-label="`${hex.name}卦，自下而上${hex.lines.join('')}`" role="img">
      <div v-for="(yang, i) in [...hex.lines].reverse()" :key="i" class="yao" :class="{ yin: !yang, moving: moving === 6 - i }"><span></span><span v-if="!yang"></span><i v-if="moving === 6 - i"></i></div>
    </div>
    <strong>{{ hex.name }}</strong><small>{{ hex.upper.nature }} · {{ hex.lower.nature }}</small>
  </figure>
</template>
