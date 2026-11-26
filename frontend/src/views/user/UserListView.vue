<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ROLE_OPTIONS,
  changeUserStatus,
  deleteUser,
  fetchUsers,
  kickUser,
  type UserItem,
} from '@/api/user'
import UserFormDialog from './components/UserFormDialog.vue'
import ResetPasswordDialog from './components/ResetPasswordDialog.vue'

const list = ref<UserItem[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  role: '',
  status: undefined as number | undefined,
})

const formVisible = ref(false)
const editingUser = ref<UserItem | null>(null)
const passwordVisible = ref(false)
const passwordUser = ref<UserItem | null>(null)

function roleTagType(role: string) {
  switch (role) {
    case 'ADMIN':
      return 'danger'
    case 'TEACHER':
      return 'primary'
    default:
      return 'info'
  }
}

async function loadList() {
  loading.value = true
  try {
    const result = await fetchUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status,
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
  query.role = ''
  query.status = undefined
  query.page = 1
  loadList()
}

function openCreate() {
  editingUser.value = null
  formVisible.value = true
}

function openEdit(row: UserItem) {
  editingUser.value = row
  formVisible.value = true
}

function openResetPassword(row: UserItem) {
  passwordUser.value = row
  passwordVisible.value = true
}

async function toggleStatus(row: UserItem) {
  const target = row.status
  try {
    await changeUserStatus(row.id, target)
    ElMessage.success(target === 1 ? '已启用' : '已停用，该用户已被强制下线')
  } catch {
    // 不能停用自己、最后一个管理员等限制由后端拒绝，界面回滚
    row.status = target === 1 ? 0 : 1
  }
}

async function kick(row: UserItem) {
  const confirmed = await ElMessageBox.confirm(
    `强制下线后「${row.realName}」需要重新登录，确定执行？`,
    '强制下线',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await kickUser(row.id)
    ElMessage.success('已强制下线')
  } catch {
    // 忽略
  }
}

async function remove(row: UserItem) {
  const confirmed = await ElMessageBox.confirm(
    `确定删除用户「${row.realName}（${row.username}）」？有答卷记录的用户无法删除，请改为停用。`,
    '删除用户',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    // 有答卷/最后一个管理员等限制由后端拒绝
  }
}

async function onSaved() {
  await loadList()
}

onMounted(loadList)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="用户名或姓名"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
        <el-select v-model="query.role" placeholder="角色" clearable style="width: 120px">
          <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新增用户
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="realName" label="姓名" min-width="120" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" effect="plain" size="small">{{ row.roleLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="toggleStatus(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="最后登录" width="180">
        <template #default="{ row }">{{ row.lastLoginAt ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button text type="primary" size="small" @click="openResetPassword(row)">重置密码</el-button>
          <el-button text type="warning" size="small" @click="kick(row)">强制下线</el-button>
          <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
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

  <UserFormDialog v-model="formVisible" :user="editingUser" @saved="onSaved" />
  <ResetPasswordDialog v-model="passwordVisible" :user="passwordUser" @saved="onSaved" />
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
