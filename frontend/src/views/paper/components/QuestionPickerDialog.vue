<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchQuestions, type CategoryNode, type QuestionItem } from '@/api/question'
import { addPaperQuestions } from '@/api/paper'

const props = defineProps<{
  modelValue: boolean
  paperId: number
  categories: CategoryNode[]
  existingIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  added: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const list = ref<QuestionItem[]>([])
const total = ref(0)
const selection = ref<QuestionItem[]>([])
const tableRef = ref()

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  categoryId: undefined as number | undefined,
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      query.page = 1
      query.keyword = ''
      query.categoryId = undefined
      selection.value = []
      loadList()
    }
  },
)

async function loadList() {
  loading.value = true
  try {
    const result = await fetchQuestions({
      page: query.page,
      size: query.size,
      status: 1,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId ?? undefined,
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadList()
}

/** 已在试卷中的题目不允许重复勾选 */
function selectable(row: QuestionItem) {
  return !props.existingIds.includes(row.id)
}

function onSelectionChange(rows: QuestionItem[]) {
  selection.value = rows
}

async function submit() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选题目')
    return
  }
  saving.value = true
  try {
    await addPaperQuestions(
      props.paperId,
      selection.value.map((row) => ({ questionId: row.id })),
    )
    ElMessage.success(`已添加 ${selection.value.length} 道题`)
    visible.value = false
    emit('added')
  } catch {
    // 题目已停用等错误由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="添加题目" width="820px" top="8vh">
    <div class="filters">
      <el-input
        v-model="query.keyword"
        placeholder="题干关键字"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-tree-select
        v-model="query.categoryId"
        :data="categories"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        check-strictly
        clearable
        placeholder="全部分类"
        style="width: 200px"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <span class="tip">已加入本卷的题目不可重复选择</span>
    </div>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="list"
      border
      height="380"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" :selectable="selectable" />
      <el-table-column prop="stem" label="题干" min-width="320" show-overflow-tooltip />
      <el-table-column prop="typeLabel" label="题型" width="90" />
      <el-table-column prop="score" label="默认分" width="90" />
      <el-table-column prop="categoryName" label="分类" width="120" />
    </el-table>

    <div class="pager">
      <span class="tip">已选 {{ selection.length }} 题</span>
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!selection.length" @click="submit">
        加入试卷
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}
</style>
