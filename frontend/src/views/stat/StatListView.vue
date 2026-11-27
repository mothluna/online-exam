<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  exportExam,
  fetchAttemptStats,
  fetchDistribution,
  fetchExamStats,
  fetchExportTask,
  type AttemptStat,
  type ExamStat,
  type ScoreBucket,
} from '@/api/stat'
import { saveBlob } from '@/utils/download'
import ScoreDistChart from './components/ScoreDistChart.vue'
import AttemptReviewDrawer from './components/AttemptReviewDrawer.vue'

// 表格插槽 row 为宽类型，在边界收窄一次
function asAttemptStat(row: unknown): AttemptStat {
  return row as AttemptStat
}

const list = ref<ExamStat[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

const selected = ref<ExamStat | null>(null)
const buckets = ref<ScoreBucket[]>([])
const attempts = ref<AttemptStat[]>([])
const attemptTotal = ref(0)
const attemptPage = ref(1)
const attemptSize = 10
const detailLoading = ref(false)
const exporting = ref(false)
const exportTip = ref('')
// 后台导出完成后不自动打开：window.open 在轮询回调里执行会脱离用户点击栈，可能被浏览器拦截。
// 改成把链接存下来，由用户点「下载」触发，这个 open 就发生在点击回调内。
const exportUrl = ref('')
const reviewAttemptId = ref<number | null>(null)
const reviewVisible = ref(false)

const statusText: Record<string, string> = {
  GRADED: '已判分',
  SUBMITTED: '待判分',
  TIMEOUT: '超时交卷',
  IN_PROGRESS: '进行中',
}

const avgScore = computed(() => {
  const value = selected.value?.avgScore
  return value == null ? '-' : Number(value).toFixed(2)
})

const submitRate = computed(() => {
  const item = selected.value
  if (!item || !item.joinCount) {
    return 0
  }
  return Math.round((item.submitCount / item.joinCount) * 1000) / 10
})

async function loadList() {
  loading.value = true
  try {
    const result = await fetchExamStats({
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

async function selectExam(row: ExamStat) {
  selected.value = row
  exportUrl.value = ''
  exportTip.value = ''
  buckets.value = []
  attempts.value = []
  attemptTotal.value = 0
  attemptPage.value = 1
  detailLoading.value = true
  try {
    const [dist, page] = await Promise.all([
      fetchDistribution(row.examId),
      fetchAttemptStats(row.examId, 1, attemptSize),
    ])
    buckets.value = dist
    attempts.value = page.records
    attemptTotal.value = page.total
  } finally {
    detailLoading.value = false
  }
}

async function loadAttempts(page: number) {
  if (!selected.value) {
    return
  }
  attemptPage.value = page
  detailLoading.value = true
  try {
    const result = await fetchAttemptStats(selected.value.examId, page, attemptSize)
    attempts.value = result.records
    attemptTotal.value = result.total
  } finally {
    detailLoading.value = false
  }
}

function openReview(row: AttemptStat) {
  reviewAttemptId.value = row.attemptId
  reviewVisible.value = true
}

async function onReviewed() {
  // 复核改的是总分与及格状态，刷当前页即可（排序会因此变化，翻页时自然归位）
  await loadAttempts(attemptPage.value)
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function pollTask(taskId: string) {
  for (let i = 0; i < 40; i++) {
    await sleep(1000)
    const task = await fetchExportTask(taskId)
    if (task.status === 'DONE' && task.url) {
      exportUrl.value = task.url
      exportTip.value = `已生成 ${task.rowCount} 行，点击「下载」保存`
      ElMessage.success(`导出完成：${task.rowCount} 行`)
      return
    }
    if (task.status === 'FAILED') {
      exportTip.value = ''
      exportUrl.value = ''
      ElMessage.error(task.message || '导出失败')
      return
    }
    exportTip.value = `正在后台生成（${task.rowCount} 行）…`
  }
  exportTip.value = ''
  ElMessage.warning('导出仍在处理中，请稍后重试')
}

function openDownload() {
  if (exportUrl.value) {
    window.open(exportUrl.value, '_blank')
  }
}

async function doExport() {
  if (!selected.value) {
    return
  }
  exporting.value = true
  exportUrl.value = ''
  exportTip.value = ''
  try {
    const result = await exportExam(selected.value.examId)
    if (result.mode === 'file') {
      saveBlob(result.blob, result.filename)
      ElMessage.success('已开始下载')
      return
    }
    // 超过 5000 行转后台：轮询任务状态，完成后再打开预签名链接
    exportTip.value = `数据较多（${result.rowCount} 行），已转后台生成…`
    await pollTask(result.taskId)
  } finally {
    exporting.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <el-card shadow="never" class="list-card">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="考试名称"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <span class="tip">点击任意一行查看分数分布与成绩明细</span>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      border
      stripe
      highlight-current-row
      @row-click="selectExam"
    >
      <el-table-column prop="title" label="考试" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="joinCount" label="开考" width="80" />
      <el-table-column prop="submitCount" label="提交" width="80" />
      <el-table-column label="平均分" width="90">
        <template #default="{ row }">{{ row.avgScore == null ? '-' : Number(row.avgScore).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="最高 / 最低" width="130">
        <template #default="{ row }">
          {{ row.maxScore == null ? '-' : Number(row.maxScore).toFixed(2) }} /
          {{ row.minScore == null ? '-' : Number(row.minScore).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="及格率" min-width="160">
        <template #default="{ row }">
          <!-- 无人提交时及格率没有意义：0% 会和"全员不及格"混淆 -->
          <span v-if="!row.submitCount" class="no-data">—</span>
          <el-progress
            v-else
            :percentage="Number(row.passRate)"
            :stroke-width="10"
            :color="Number(row.passRate) >= 60 ? '#67c23a' : '#e6a23c'"
          />
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

  <el-card v-if="selected" v-loading="detailLoading" shadow="never" class="detail-card">
    <template #header>
      <div class="detail-head">
        <span class="detail-title">{{ selected.title }}</span>
        <div class="detail-actions">
          <span v-if="exportTip" class="export-tip">{{ exportTip }}</span>
          <el-button v-if="exportUrl" type="success" plain @click="openDownload">
            <el-icon><Download /></el-icon>下载
          </el-button>
          <el-button type="primary" :loading="exporting" @click="doExport">
            <el-icon><Download /></el-icon>导出成绩单
          </el-button>
        </div>
      </div>
    </template>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <div class="kpi">
          <div class="kpi-label">开考人数</div>
          <div class="kpi-value">{{ selected.joinCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi">
          <div class="kpi-label">提交人数</div>
          <div class="kpi-value">
            {{ selected.submitCount }}
            <span class="kpi-sub">（{{ selected.joinCount ? submitRate + '%' : '—' }}）</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi">
          <div class="kpi-label">平均分</div>
          <div class="kpi-value">{{ avgScore }}<span class="kpi-sub">/ {{ Number(selected.paperTotalScore).toFixed(0) }}</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi">
          <div class="kpi-label">及格率</div>
          <div class="kpi-value" :class="{ danger: selected.submitCount > 0 && Number(selected.passRate) < 60 }">
            <template v-if="selected.submitCount">
              {{ Number(selected.passRate).toFixed(1) }}<span class="kpi-sub">%（{{ selected.passCount }} 人）</span>
            </template>
            <span v-else class="kpi-sub">暂无提交</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="section-title">分数分布（按满分百分比）</div>
    <ScoreDistChart v-if="buckets.length" :buckets="buckets" />
    <el-empty v-else description="暂无提交数据" :image-size="80" />

    <div class="section-title">成绩明细</div>
    <el-table :data="attempts" border size="small">
      <el-table-column prop="attemptId" label="答卷" width="100" />
      <el-table-column prop="realName" label="考生" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusText[row.status] ?? row.status }}</template>
      </el-table-column>
      <el-table-column label="得分" width="90">
        <template #default="{ row }">{{ Number(row.totalScore).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="是否及格" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isPass === 1 ? 'success' : 'info'" size="small">
            {{ row.isPass === 1 ? '及格' : '不及格' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="交卷时间" width="180" />
      <el-table-column label="用时" width="100">
        <template #default="{ row }">{{ Math.round((row.durationSec ?? 0) / 60) }} 分钟</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openReview(asAttemptStat(row))">复核</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无数据</template>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="attemptPage"
        :page-size="attemptSize"
        :total="attemptTotal"
        layout="total, prev, pager, next"
        @current-change="loadAttempts"
      />
    </div>
  </el-card>

  <AttemptReviewDrawer v-model="reviewVisible" :attempt-id="reviewAttemptId" @reviewed="onReviewed" />
</template>

<style scoped>
.list-card {
  margin-bottom: 16px;
}

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

.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title {
  font-weight: 600;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.export-tip {
  font-size: 12px;
  color: var(--el-color-primary);
}

.kpi-row {
  margin-bottom: 8px;
}

.kpi {
  padding: 12px 16px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.kpi-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.kpi-value.danger {
  color: var(--el-color-danger);
}

.kpi-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

/* 无数据占位：与"真实的 0"区分开 */
.no-data {
  color: var(--el-text-color-placeholder);
}

.section-title {
  font-weight: 600;
  font-size: 14px;
  margin: 18px 0 10px;
}
</style>
