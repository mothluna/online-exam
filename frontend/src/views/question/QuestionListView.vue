<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DIFFICULTY_OPTIONS,
  TYPE_OPTIONS,
  changeQuestionStatus,
  deleteCategory,
  deleteQuestion,
  fetchCategories,
  fetchQuestions,
  type CategoryNode,
  type QuestionItem,
  type QuestionType,
} from '@/api/question'
import CategoryDialog from './components/CategoryDialog.vue'
import QuestionFormDialog from './components/QuestionFormDialog.vue'
import ImportDialog from './components/ImportDialog.vue'

// el-table 插槽 row 为宽类型，在边界收窄一次，保持处理函数签名严格
function asQuestion(row: unknown): QuestionItem {
  return row as QuestionItem
}

const categories = ref<CategoryNode[]>([])
const treeLoading = ref(false)
const selectedCategoryId = ref<number | null>(null)
const selectedCategoryName = ref('')

const list = ref<QuestionItem[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  type: '' as '' | QuestionType,
  difficulty: undefined as number | undefined,
  status: undefined as number | undefined,
})

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const importVisible = ref(false)

const categoryDialogVisible = ref(false)
const categoryMode = ref<'create' | 'edit'>('create')
const categoryParentId = ref(0)
const categoryParentName = ref('')
const editingCategory = ref<CategoryNode | null>(null)

function typeTagType(type: string) {
  switch (type) {
    case 'SINGLE':
      return 'primary'
    case 'MULTIPLE':
      return 'success'
    case 'JUDGE':
      return 'warning'
    case 'BLANK':
      return 'info'
    default:
      return 'danger'
  }
}

function difficultyLabel(value: number) {
  return DIFFICULTY_OPTIONS.find((item) => item.value === value)?.label ?? '-'
}

const currentCategoryHint = computed(() => (selectedCategoryId.value ? selectedCategoryName.value : '全部分类'))

async function loadTree() {
  treeLoading.value = true
  try {
    categories.value = await fetchCategories()
  } finally {
    treeLoading.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const result = await fetchQuestions({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      type: query.type || undefined,
      difficulty: query.difficulty,
      status: query.status,
      categoryId: selectedCategoryId.value ?? undefined,
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

function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.difficulty = undefined
  query.status = undefined
  query.page = 1
  loadList()
}

function onNodeClick(node: CategoryNode) {
  selectedCategoryId.value = node.id
  selectedCategoryName.value = node.name
  query.page = 1
  loadList()
}

function clearCategory() {
  selectedCategoryId.value = null
  selectedCategoryName.value = ''
  query.page = 1
  loadList()
}

// ---------------- 分类 ----------------

function openCreateRoot() {
  categoryMode.value = 'create'
  categoryParentId.value = 0
  categoryParentName.value = ''
  editingCategory.value = null
  categoryDialogVisible.value = true
}

function openCreateChild(node: CategoryNode) {
  categoryMode.value = 'create'
  categoryParentId.value = node.id
  categoryParentName.value = node.name
  editingCategory.value = null
  categoryDialogVisible.value = true
}

function openEditNode(node: CategoryNode) {
  categoryMode.value = 'edit'
  editingCategory.value = node
  categoryDialogVisible.value = true
}

async function removeNode(node: CategoryNode) {
  const confirmed = await ElMessageBox.confirm(
    `确定删除分类「${node.name}」？存在子分类或题目时会被拒绝。`,
    '删除分类',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await deleteCategory(node.id)
    ElMessage.success('分类已删除')
    if (selectedCategoryId.value === node.id) {
      selectedCategoryId.value = null
      selectedCategoryName.value = ''
    }
    await Promise.all([loadTree(), loadList()])
  } catch {
    // 拒绝原因由拦截器提示
  }
}

async function onCategorySaved() {
  await Promise.all([loadTree(), loadList()])
}

// ---------------- 题目 ----------------

function openCreate() {
  editingId.value = null
  formVisible.value = true
}

function openEdit(row: QuestionItem) {
  editingId.value = row.id
  formVisible.value = true
}

async function removeQuestion(row: QuestionItem) {
  const confirmed = await ElMessageBox.confirm(
    `确定删除该题？\n${row.stem.slice(0, 40)}`,
    '删除题目',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await deleteQuestion(row.id)
    ElMessage.success('题目已删除')
    await Promise.all([loadList(), loadTree()])
  } catch {
    // 忽略
  }
}

async function toggleStatus(row: QuestionItem) {
  const target = row.status
  try {
    await changeQuestionStatus(row.id, target)
    ElMessage.success(target === 1 ? '已启用' : '已停用')
    await loadTree()
  } catch {
    // 失败时回滚开关，避免界面与库不一致
    row.status = target === 1 ? 0 : 1
  }
}

async function onQuestionSaved() {
  await Promise.all([loadList(), loadTree()])
}

onMounted(async () => {
  await loadTree()
  await loadList()
})
</script>

<template>
  <el-row :gutter="16" class="page">
    <el-col :span="5">
      <el-card v-loading="treeLoading" shadow="never" class="tree-card">
        <template #header>
          <div class="card-head">
            <span>题目分类</span>
            <el-button text type="primary" size="small" @click="openCreateRoot">
              <el-icon><Plus /></el-icon>根分类
            </el-button>
          </div>
        </template>

        <div class="all-node" :class="{ active: selectedCategoryId === null }" @click="clearCategory">
          全部分类
        </div>

        <el-tree
          :data="categories"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          :expand-on-click-node="false"
          default-expand-all
          @node-click="onNodeClick"
        >
          <template #default="{ data }">
            <div class="tree-node" :class="{ active: selectedCategoryId === data.id }">
              <span class="node-name">{{ data.name }}</span>
              <span class="node-count">{{ data.questionCount }}</span>
              <span class="node-actions">
                <el-icon title="新增子分类" @click.stop="openCreateChild(data)"><Plus /></el-icon>
                <el-icon title="重命名" @click.stop="openEditNode(data)"><EditPen /></el-icon>
                <el-icon title="删除" @click.stop="removeNode(data)"><Delete /></el-icon>
              </span>
            </div>
          </template>
        </el-tree>
      </el-card>
    </el-col>

    <el-col :span="19">
      <el-card shadow="never">
        <div class="toolbar">
          <div class="filters">
            <el-input
              v-model="query.keyword"
              placeholder="题干关键字"
              clearable
              style="width: 200px"
              @keyup.enter="search"
            />
            <el-select v-model="query.type" placeholder="题型" clearable style="width: 120px">
              <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="query.difficulty" placeholder="难度" clearable style="width: 100px">
              <el-option v-for="item in DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 100px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </div>
          <div class="actions">
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon>新增题目
            </el-button>
            <el-button @click="importVisible = true">批量导入</el-button>
          </div>
        </div>

        <div class="scope">当前范围：{{ currentCategoryHint }}，共 {{ total }} 题</div>

        <el-table v-loading="loading" :data="list" border stripe height="calc(100vh - 300px)">
          <el-table-column prop="stem" label="题干" min-width="320" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类" width="120" />
          <el-table-column label="题型" width="92">
            <template #default="{ row }">
              <el-tag :type="typeTagType(row.type)" effect="plain" size="small">{{ row.typeLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="80">
            <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column prop="score" label="分值" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                @change="toggleStatus(asQuestion(row))"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openEdit(asQuestion(row))">编辑</el-button>
              <el-button text type="danger" size="small" @click="removeQuestion(asQuestion(row))">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadList"
            @size-change="search"
          />
        </div>
      </el-card>
    </el-col>
  </el-row>

  <CategoryDialog
    v-model="categoryDialogVisible"
    :mode="categoryMode"
    :parent-id="categoryParentId"
    :parent-name="categoryParentName"
    :node="editingCategory"
    @saved="onCategorySaved"
  />

  <QuestionFormDialog
    v-model="formVisible"
    :categories="categories"
    :question-id="editingId"
    :default-category-id="selectedCategoryId"
    @saved="onQuestionSaved"
  />

  <ImportDialog v-model="importVisible" @saved="onQuestionSaved" />
</template>

<style scoped>
.page {
  height: 100%;
}

.tree-card {
  height: calc(100vh - 120px);
  overflow: auto;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.all-node {
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  margin-bottom: 6px;
}

.all-node:hover,
.all-node.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 8px;
  font-size: 14px;
}

.tree-node.active .node-name {
  color: var(--el-color-primary);
  font-weight: 600;
}

.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.node-actions {
  display: none;
  gap: 6px;
  color: var(--el-text-color-secondary);
}

.node-actions .el-icon:hover {
  color: var(--el-color-primary);
}

.tree-node:hover .node-actions {
  display: inline-flex;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.filters,
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
