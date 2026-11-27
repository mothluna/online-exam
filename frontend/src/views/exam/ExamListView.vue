<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteExam, fetchExams, publishExam, type ExamItem } from '@/api/exam'
import ExamFormDialog from './components/ExamFormDialog.vue'

// el-table 插槽的 row 是 DefaultRow（Record<PropertyKey, any>），
// 在边界显式收窄，保证下面的处理函数仍按 ExamItem 严格校验
function asExam(row: unknown): ExamItem {
  return row as ExamItem
}

const list = ref<ExamItem[]>([])
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

async function loadList() {
  loading.value = true
  try {
    const result = await fetchExams({
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

function openEdit(row: ExamItem) {
  editingId.value = row.id
  formVisible.value = true
}

async function publish(row: ExamItem) {
  const confirmed = await ElMessageBox.confirm(
    `发布后考生即可在开放时间内参加，确定发布「${row.title}」？`,
    '发布考试',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await publishExam(row.id)
    ElMessage.success('考试已发布')
    await loadList()
  } catch {
    // 试卷无题目等情况由拦截器提示
  }
}

async function remove(row: ExamItem) {
  const confirmed = await ElMessageBox.confirm(`确定删除考试「${row.title}」？`, '删除考试', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await deleteExam(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    // 已发布不可删除
  }
}

onMounted(loadList)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="考试名称"
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
        <el-icon><Plus /></el-icon>新建考试
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="title" label="考试名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="paperTitle" label="试卷" min-width="160" show-overflow-tooltip />
      <el-table-column label="题数 / 总分" width="130">
        <template #default="{ row }">{{ row.totalCount }} 题 / {{ Number(row.totalScore).toFixed(2) }} 分</template>
      </el-table-column>
      <el-table-column label="开放时间" width="330">
        <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
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
      <el-table-column label="参考人数" width="100">
        <template #default="{ row }">{{ row.attemptCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" text type="primary" size="small" @click="openEdit(asExam(row))">
            编辑
          </el-button>
          <el-button v-if="row.status === 'DRAFT'" text type="success" size="small" @click="publish(asExam(row))">
            发布
          </el-button>
          <el-button v-if="row.status === 'DRAFT'" text type="danger" size="small" @click="remove(asExam(row))">
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

  <ExamFormDialog v-model="formVisible" :exam-id="editingId" @saved="loadList" />
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
