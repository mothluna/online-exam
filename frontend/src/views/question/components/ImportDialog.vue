<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, type UploadFile, type UploadInstance } from 'element-plus'
import { downloadTemplate, importQuestions, type ImportResult } from '@/api/question'
import { saveBlob } from '@/utils/download'

const props = defineProps<{ modelValue: boolean }>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const uploadRef = ref<UploadInstance>()
const file = ref<File | null>(null)
const uploading = ref(false)
const result = ref<ImportResult | null>(null)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      file.value = null
      result.value = null
      uploadRef.value?.clearFiles()
    }
  },
)

function onFileChange(uploadFile: UploadFile) {
  file.value = uploadFile.raw ?? null
  result.value = null
}

function onFileRemove() {
  file.value = null
}

async function startImport() {
  if (!file.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  uploading.value = true
  try {
    const data = await importQuestions(file.value)
    result.value = data
    if (data.success > 0) {
      emit('saved')
    }
    if (data.failed > 0) {
      ElMessage.warning(`导入完成：成功 ${data.success} 条，失败 ${data.failed} 条`)
    } else {
      ElMessage.success(`导入成功 ${data.success} 条`)
    }
  } catch {
    // 文件格式错误等由拦截器提示
  } finally {
    uploading.value = false
  }
}

async function handleTemplate() {
  const { blob, filename } = await downloadTemplate()
  saveBlob(blob, filename)
}
</script>

<template>
  <el-dialog v-model="visible" title="批量导入题目" width="640px">
    <el-alert type="info" :closable="false" show-icon class="tip">
      <template #title>
        表格列顺序：分类路径、题型、题干、选项A~F、正确答案、解析、难度、分值。
        分类路径写成「一级/二级」，需与系统内分类一致。
      </template>
    </el-alert>

    <div class="toolbar">
      <el-button text type="primary" @click="handleTemplate">
        <el-icon><Download /></el-icon>
        下载模板
      </el-button>
    </div>

    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :limit="1"
      accept=".xlsx,.xls"
      :on-change="onFileChange"
      :on-remove="onFileRemove"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">把 Excel 拖到这里，或 <em>点击选择</em></div>
      <template #tip>
        <div class="el-upload__tip">单文件不超过 10MB；重复题干（含仅标点差异）会被拦截并写入失败明细</div>
      </template>
    </el-upload>

    <div v-if="result" class="result">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="总行数">{{ result.total }}</el-descriptions-item>
        <el-descriptions-item label="成功">
          <span class="ok">{{ result.success }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="失败">
          <span :class="{ bad: result.failed > 0 }">{{ result.failed }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ result.elapsedMs }} ms</el-descriptions-item>
      </el-descriptions>

      <div v-if="result.failed > 0" class="errors">
        <div class="errors-head">
          <span>失败明细（前 {{ result.errors.length }} 条）</span>
          <el-link v-if="result.errorFileUrl" type="primary" :href="result.errorFileUrl" target="_blank">
            下载完整失败清单
          </el-link>
        </div>
        <ul class="error-list">
          <li v-for="(item, index) in result.errors" :key="index">{{ item }}</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!file" @click="startImport">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip {
  margin-bottom: 8px;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}

.result {
  margin-top: 16px;
}

.ok {
  color: var(--el-color-success);
  font-weight: 600;
}

.bad {
  color: var(--el-color-danger);
  font-weight: 600;
}

.errors {
  margin-top: 12px;
}

.errors-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.error-list {
  margin: 0;
  padding: 10px 12px 10px 28px;
  max-height: 180px;
  overflow-y: auto;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}
</style>
