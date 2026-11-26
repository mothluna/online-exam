<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deletePaper, fetchPapers, type PaperItem } from '@/api/paper'
import { fetchCategories, type CategoryNode } from '@/api/question'
import PaperFormDialog from './components/PaperFormDialog.vue'
import PaperDetailDrawer from './components/PaperDetailDrawer.vue'

const categories = ref<CategoryNode[]>([])
const list = ref<PaperItem[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const detailVisible = ref(false)
const detailId = ref<number | null>(null)

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadList() {
  loading.value = true
  try {
    const result = await fetchPapers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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
  query.status = ''
  query.page = 1
  loadList()
}

function openCreate() {
  editingId.value = null
  formVisible.value = true
}

function openEdit(row: PaperItem) {
  editingId.value = row.id
  formVisible.value = true
}

function openDetail(row: PaperItem) {
  detailId.value = row.id
  detailVisible.value = true
}

async function remove(row: PaperItem) {
  const confirmed = await ElMessageBox.confirm(`确定删除试卷「${row.title}」？`, '删除试卷', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await deletePaper(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    // 已发布的试卷不可删除，提示由拦截器给出
  }
}

async function onSaved() {
  await loadList()
  // 新建后直接打开详情，省一步点击
  if (list.value.length && !editingId.value) {
    openDetail(list.value[0])
  }
}

async function onChanged() {
  await loadList()
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadList()])
})
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="试卷标题"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建试卷
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="title" label="试卷标题" min-width="220" show-overflow-tooltip />
      <el-table-column label="组卷方式" width="110">
        <template #default="{ row }">
          <el-tag :type="row.composeType === 'RANDOM' ? 'warning' : 'info'" effect="plain" size="small">
            {{ row.composeType === 'RANDOM' ? '规则抽题' : '手工选题' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="题数 / 总分" width="130">
        <template #default="{ row }">{{ row.totalCount }} 题 / {{ Number(row.totalScore).toFixed(2) }} 分</template>
      </el-table-column>
      <el-table-column label="及格分" width="90">
        <template #default="{ row }">{{ Number(row.passScore).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="时长" width="90">
        <template #default="{ row }">{{ Math.round(row.durationSec / 60) }} 分钟</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDetail(row)">预览</el-button>
          <el-button v-if="row.status === 'DRAFT'" text type="primary" size="small" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button v-if="row.status === 'DRAFT'" text type="danger" size="small" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="search"
      />
    </div>
  </el-card>

  <PaperFormDialog
    v-model="formVisible"
    :paper-id="editingId"
    :categories="categories"
    @saved="onSaved"
  />

  <PaperDetailDrawer
    v-model="detailVisible"
    :paper-id="detailId"
    :categories="categories"
    @changed="onChanged"
  />
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
