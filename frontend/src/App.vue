<script setup lang="ts">
import IcdAssistantFloating from './components/IcdAssistantFloating.vue'
</script>

<template>
  <router-view />
  <!-- 全局浮动 ICD 编码助手：跨路由保留状态与对话历史 -->
  <IcdAssistantFloating />
</template>

<style>
html, body, #app { height: 100%; margin: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
               "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}
</style>
