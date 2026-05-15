<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Clock,
  CollectionTag,
  EditPen,
  Expand,
  Fold,
  Promotion,
  RefreshRight,
  Select,
  Star,
  Tickets,
} from '@element-plus/icons-vue'
import { listPaths } from '@/api/path'
import { practiceApi } from '@/api/practice'
import type { ExamResult, LearningPath, PracticeQuestion, PracticeSession } from '@/types'

type ShelfMode = 'practice' | 'exam'
type DrawerTab = 'records' | 'wrong' | 'favorite'

interface ShelfQuestion {
  key: string
  mode: ShelfMode
  sessionId: number
  questionId: number
  order: number
  knowledgeId?: string
  stem: string
  options: string[]
  userAnswer?: string
  correctAnswer?: string
  explanation?: string
  createdAt: string
  updatedAt: string
}

interface SessionRecord {
  key: string
  mode: ShelfMode
  sessionId: number
  pathId?: number | null
  totalQuestions: number
  answeredCount: number
  correctCount: number
  score?: number | null
  grade?: string | null
  status: string
  createdAt: string
  submittedAt?: string | null
  updatedAt: string
}

const STORAGE_KEYS = {
  wrongBook: 'practice_wrong_book_v1',
  favoriteBook: 'practice_favorite_book_v1',
  practiceRecords: 'practice_session_records_v1',
  examRecords: 'practice_exam_records_v1',
}

const loading = ref(false)
const activeMode = ref<ShelfMode>('practice')
const immersiveMode = ref(true)
const drawerVisible = ref(false)
const drawerTab = ref<DrawerTab>('records')

const paths = ref<LearningPath[]>([])
const selectedPathId = ref<number | undefined>(undefined)

const practiceSession = ref<PracticeSession | null>(null)
const practiceCount = ref(6)
const generatingPractice = ref(false)
const submittingPracticeQuestionId = ref<number | null>(null)
const practiceAnswers = reactive<Record<number, string>>({})

const examSession = ref<PracticeSession | null>(null)
const examCount = ref(10)
const examDuration = ref(20)
const generatingExam = ref(false)
const submittingExam = ref(false)
const examAnswers = reactive<Record<number, string>>({})
const examTimerSeconds = ref(0)
let examTimer: ReturnType<typeof setInterval> | null = null

const wrongBook = ref<ShelfQuestion[]>([])
const favoriteBook = ref<ShelfQuestion[]>([])
const practiceRecords = ref<SessionRecord[]>([])
const examRecords = ref<SessionRecord[]>([])

const currentSession = computed(() => (activeMode.value === 'practice' ? practiceSession.value : examSession.value))
const isImmersiveWorking = computed(() => {
  if (!immersiveMode.value) return false
  if (activeMode.value === 'practice') return !!practiceSession.value
  return !!examSession.value
})

const practiceCompletionText = computed(() => {
  const current = practiceSession.value
  if (!current) return '暂无练习'
  return `${current.answeredCount}/${current.totalQuestions}`
})

const examCompletionText = computed(() => {
  const current = examSession.value
  if (!current) return '暂无测评'
  return `${current.answeredCount}/${current.totalQuestions}`
})

const examRemainingText = computed(() => {
  const total = Math.max(0, examTimerSeconds.value)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

const recordList = computed(() => {
  const merged = [...practiceRecords.value, ...examRecords.value]
  return merged.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
})

function nowISO() {
  return new Date().toISOString()
}

function safeParseArray<T>(raw: string | null): T[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as T[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function loadLocalState() {
  wrongBook.value = safeParseArray<ShelfQuestion>(localStorage.getItem(STORAGE_KEYS.wrongBook))
  favoriteBook.value = safeParseArray<ShelfQuestion>(localStorage.getItem(STORAGE_KEYS.favoriteBook))
  practiceRecords.value = safeParseArray<SessionRecord>(localStorage.getItem(STORAGE_KEYS.practiceRecords))
  examRecords.value = safeParseArray<SessionRecord>(localStorage.getItem(STORAGE_KEYS.examRecords))
}

function saveLocalState() {
  localStorage.setItem(STORAGE_KEYS.wrongBook, JSON.stringify(wrongBook.value))
  localStorage.setItem(STORAGE_KEYS.favoriteBook, JSON.stringify(favoriteBook.value))
  localStorage.setItem(STORAGE_KEYS.practiceRecords, JSON.stringify(practiceRecords.value))
  localStorage.setItem(STORAGE_KEYS.examRecords, JSON.stringify(examRecords.value))
}

function resetAnswerMap(map: Record<number, string>) {
  for (const key of Object.keys(map)) {
    delete map[Number(key)]
  }
}

function hydrateAnswersFromSession(session: PracticeSession | null, map: Record<number, string>) {
  resetAnswerMap(map)
  for (const question of session?.questions || []) {
    if (question.userAnswer) {
      map[question.id] = question.userAnswer
    }
  }
}

function optionKey(index: number) {
  return String.fromCharCode(65 + index)
}

function optionLabel(index: number, value: string) {
  return `${optionKey(index)}. ${value}`
}

function shelfQuestionKey(mode: ShelfMode, sessionId: number, questionId: number) {
  return `${mode}-${sessionId}-${questionId}`
}

function toShelfQuestion(question: PracticeQuestion, mode: ShelfMode, sessionId: number): ShelfQuestion {
  const key = shelfQuestionKey(mode, sessionId, question.id)
  const now = nowISO()
  const old = [...wrongBook.value, ...favoriteBook.value].find((item) => item.key === key)
  return {
    key,
    mode,
    sessionId,
    questionId: question.id,
    order: question.order,
    knowledgeId: question.knowledgeId || undefined,
    stem: question.stem,
    options: question.options || [],
    userAnswer: question.userAnswer || undefined,
    correctAnswer: question.correctAnswer || undefined,
    explanation: question.explanation || undefined,
    createdAt: old?.createdAt || now,
    updatedAt: now,
  }
}

function upsertShelfItem(target: typeof wrongBook | typeof favoriteBook, item: ShelfQuestion, maxSize = 400) {
  const next = target.value.filter((it) => it.key !== item.key)
  next.unshift(item)
  if (next.length > maxSize) {
    next.length = maxSize
  }
  target.value = next
}

function removeShelfItem(target: typeof wrongBook | typeof favoriteBook, key: string) {
  target.value = target.value.filter((it) => it.key !== key)
}

function isFavoriteQuestion(mode: ShelfMode, sessionId: number, questionId: number) {
  const key = shelfQuestionKey(mode, sessionId, questionId)
  return favoriteBook.value.some((item) => item.key === key)
}

function isWrongQuestion(mode: ShelfMode, sessionId: number, questionId: number) {
  const key = shelfQuestionKey(mode, sessionId, questionId)
  return wrongBook.value.some((item) => item.key === key)
}

function toggleFavoriteQuestion(question: PracticeQuestion, mode: ShelfMode, sessionId: number) {
  const key = shelfQuestionKey(mode, sessionId, question.id)
  if (isFavoriteQuestion(mode, sessionId, question.id)) {
    removeShelfItem(favoriteBook, key)
    ElMessage.success('已从收藏题集移除')
  } else {
    upsertShelfItem(favoriteBook, toShelfQuestion(question, mode, sessionId))
    ElMessage.success('已加入收藏题集')
  }
  saveLocalState()
}

function addWrongQuestion(question: PracticeQuestion, mode: ShelfMode, sessionId: number) {
  upsertShelfItem(wrongBook, toShelfQuestion(question, mode, sessionId))
}

function syncWrongFromSession(session: PracticeSession | null, mode: ShelfMode) {
  if (!session) return
  for (const question of session.questions || []) {
    if (question.answered && question.correct === false) {
      addWrongQuestion(question, mode, session.id)
    }
  }
  saveLocalState()
}

function upsertSessionRecord(mode: ShelfMode, session: PracticeSession) {
  const key = `${mode}-${session.id}`
  const now = nowISO()
  const record: SessionRecord = {
    key,
    mode,
    sessionId: session.id,
    pathId: session.pathId ?? null,
    totalQuestions: session.totalQuestions,
    answeredCount: session.answeredCount,
    correctCount: session.correctCount,
    score: session.score ?? null,
    grade: session.grade ?? null,
    status: session.status,
    createdAt: session.createdAt || now,
    submittedAt: session.submittedAt || null,
    updatedAt: now,
  }
  if (mode === 'practice') {
    practiceRecords.value = [record, ...practiceRecords.value.filter((item) => item.key !== key)].slice(0, 200)
  } else {
    examRecords.value = [record, ...examRecords.value.filter((item) => item.key !== key)].slice(0, 200)
  }
  saveLocalState()
}

function removeWrongQuestion(item: ShelfQuestion) {
  removeShelfItem(wrongBook, item.key)
  saveLocalState()
  ElMessage.success('已移除错题')
}

function removeFavoriteQuestion(item: ShelfQuestion) {
  removeShelfItem(favoriteBook, item.key)
  saveLocalState()
  ElMessage.success('已移除收藏题目')
}

function openDrawer(tab: DrawerTab = 'records') {
  drawerTab.value = tab
  drawerVisible.value = true
}

async function openRecord(record: SessionRecord) {
  try {
    const res = await practiceApi.detail(record.sessionId)
    const session = res.data.data
    if (record.mode === 'practice') {
      activeMode.value = 'practice'
      practiceSession.value = session
      hydrateAnswersFromSession(practiceSession.value, practiceAnswers)
      upsertSessionRecord('practice', session)
      syncWrongFromSession(practiceSession.value, 'practice')
    } else {
      activeMode.value = 'exam'
      examSession.value = session
      hydrateAnswersFromSession(examSession.value, examAnswers)
      syncExamTimer()
      upsertSessionRecord('exam', session)
      if (session.status === 'completed') {
        syncWrongFromSession(examSession.value, 'exam')
      }
    }
    drawerVisible.value = false
    ElMessage.success('已打开历史会话')
  } catch {
    // handled by interceptor
  }
}

function statusText(status?: string) {
  return status === 'completed' ? '已完成' : '进行中'
}

async function loadPaths() {
  try {
    const res = await listPaths()
    const list = res.data.data || []
    paths.value = list
    if (list.length > 0 && !selectedPathId.value) {
      selectedPathId.value = list[0]?.id
    }
  } catch {
    paths.value = []
  }
}

async function loadLatestPractice() {
  try {
    const res = await practiceApi.latest()
    practiceSession.value = res.data.data || null
    hydrateAnswersFromSession(practiceSession.value, practiceAnswers)
    if (practiceSession.value) {
      upsertSessionRecord('practice', practiceSession.value)
      syncWrongFromSession(practiceSession.value, 'practice')
    }
  } catch {
    practiceSession.value = null
  }
}

async function loadLatestExam() {
  try {
    const res = await practiceApi.latestExam()
    examSession.value = res.data.data || null
    hydrateAnswersFromSession(examSession.value, examAnswers)
    syncExamTimer()
    if (examSession.value) {
      upsertSessionRecord('exam', examSession.value)
      if (examSession.value.status === 'completed') {
        syncWrongFromSession(examSession.value, 'exam')
      }
    }
  } catch {
    examSession.value = null
    stopExamTimer()
  }
}

async function handleGeneratePractice() {
  generatingPractice.value = true
  try {
    const res = await practiceApi.generate({
      pathId: selectedPathId.value,
      questionCount: practiceCount.value,
    })
    practiceSession.value = res.data.data
    hydrateAnswersFromSession(practiceSession.value, practiceAnswers)
    upsertSessionRecord('practice', practiceSession.value)
    ElMessage.success('练习题已生成，开始作答吧')
  } catch {
    // handled by interceptor
  } finally {
    generatingPractice.value = false
  }
}

async function submitPracticeAnswer(question: PracticeQuestion) {
  const current = practiceSession.value
  if (!current) return

  const picked = practiceAnswers[question.id]
  if (!picked) {
    ElMessage.warning('请先选择答案')
    return
  }

  submittingPracticeQuestionId.value = question.id
  try {
    const res = await practiceApi.answer(current.id, {
      questionId: question.id,
      answer: picked,
    })
    const result = res.data.data
    question.userAnswer = result.answer
    question.answered = true
    question.correct = result.correct
    question.correctAnswer = result.correctAnswer
    question.explanation = result.explanation
    current.answeredCount = result.answeredCount
    current.correctCount = result.correctCount
    if (!result.correct) {
      addWrongQuestion(question, 'practice', current.id)
      saveLocalState()
    }
    if (result.completed) {
      current.status = 'completed'
      ElMessage.success('本次练习已完成')
    } else {
      ElMessage.success(result.correct ? '回答正确' : '回答错误，已加入错题本')
    }
    upsertSessionRecord('practice', current)
  } catch {
    // handled by interceptor
  } finally {
    submittingPracticeQuestionId.value = null
  }
}

async function handleGenerateExam() {
  generatingExam.value = true
  try {
    const res = await practiceApi.generateExam({
      pathId: selectedPathId.value,
      questionCount: examCount.value,
      durationMinutes: examDuration.value,
    })
    examSession.value = res.data.data
    hydrateAnswersFromSession(examSession.value, examAnswers)
    syncExamTimer()
    upsertSessionRecord('exam', examSession.value)
    ElMessage.success('测评试卷已生成，请在倒计时结束前交卷')
  } catch {
    // handled by interceptor
  } finally {
    generatingExam.value = false
  }
}

function syncExamTimer() {
  stopExamTimer()
  const current = examSession.value
  if (!current || current.status === 'completed') {
    examTimerSeconds.value = 0
    return
  }

  const duration = Number(current.durationMinutes || 0)
  const createdAt = current.createdAt ? new Date(current.createdAt).getTime() : Date.now()
  if (!Number.isFinite(duration) || duration <= 0 || Number.isNaN(createdAt)) {
    examTimerSeconds.value = 0
    return
  }

  const endAt = createdAt + duration * 60 * 1000
  const tick = () => {
    const remaining = Math.max(0, Math.floor((endAt - Date.now()) / 1000))
    examTimerSeconds.value = remaining
    if (remaining <= 0 && examSession.value?.status !== 'completed') {
      void handleSubmitExam(true)
    }
  }

  tick()
  examTimer = setInterval(tick, 1000)
}

function stopExamTimer() {
  if (examTimer) {
    clearInterval(examTimer)
    examTimer = null
  }
}

async function handleSubmitExam(auto = false) {
  const current = examSession.value
  if (!current || current.status === 'completed') return

  submittingExam.value = true
  try {
    const answers = Object.entries(examAnswers)
      .filter(([, answer]) => Boolean(answer))
      .map(([questionId, answer]) => ({
        questionId: Number(questionId),
        answer,
      }))

    const res = await practiceApi.submitExam(current.id, { answers })
    const result: ExamResult = res.data.data
    current.status = 'completed'
    current.score = result.score
    current.grade = result.grade
    current.answeredCount = result.answeredCount
    current.correctCount = result.correctCount

    const detailRes = await practiceApi.detail(current.id)
    examSession.value = detailRes.data.data
    hydrateAnswersFromSession(examSession.value, examAnswers)
    stopExamTimer()

    if (examSession.value) {
      syncWrongFromSession(examSession.value, 'exam')
      upsertSessionRecord('exam', examSession.value)
    }

    if (auto) {
      ElMessage.warning(`考试时间结束，系统已自动交卷。得分 ${result.score}，等级 ${result.grade}`)
    } else {
      ElMessage.success(`交卷成功，得分 ${result.score}，等级 ${result.grade}`)
    }
  } catch {
    // handled by interceptor
  } finally {
    submittingExam.value = false
  }
}

function practiceStatusText(question: PracticeQuestion) {
  if (!question.answered) return '未作答'
  return question.correct ? '正确' : '错误'
}

function practiceStatusType(question: PracticeQuestion): 'info' | 'success' | 'danger' {
  if (!question.answered) return 'info'
  return question.correct ? 'success' : 'danger'
}

function questionModeBadgeText(question: PracticeQuestion, mode: ShelfMode, sessionId: number) {
  const fav = isFavoriteQuestion(mode, sessionId, question.id)
  const wrong = isWrongQuestion(mode, sessionId, question.id)
  if (fav && wrong) return '收藏+错题'
  if (fav) return '已收藏'
  if (wrong) return '错题'
  return '普通题'
}

async function bootstrap() {
  loading.value = true
  try {
    loadLocalState()
    await Promise.all([loadPaths(), loadLatestPractice(), loadLatestExam()])
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void bootstrap()
})

onBeforeUnmount(() => {
  stopExamTimer()
})
</script>

<template>
  <div class="practice-test-view page-shell" :class="{ immersive: isImmersiveWorking }" v-loading="loading">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">练与测</h2>
        <p v-if="!isImmersiveWorking" class="page-subtitle">沉浸答题 + 记录追踪 + 错题本 + 收藏题集</p>
      </div>
      <div class="head-actions">
        <div class="mode-switch soft-panel">
          <el-button :type="activeMode === 'practice' ? 'primary' : 'default'" @click="activeMode = 'practice'">练习模式</el-button>
          <el-button :type="activeMode === 'exam' ? 'primary' : 'default'" @click="activeMode = 'exam'">测评模式</el-button>
        </div>
        <el-button type="primary" class="submit-primary-btn" :icon="Tickets" @click="openDrawer('records')">
          记录与题集
        </el-button>
        <el-button
          type="primary"
          class="submit-primary-btn"
          :icon="isImmersiveWorking ? Fold : Expand"
          @click="immersiveMode = !immersiveMode"
        >
          {{ isImmersiveWorking ? '退出沉浸' : '进入沉浸' }}
        </el-button>
        <el-button v-if="!isImmersiveWorking" class="refresh-btn" :icon="RefreshRight" @click="bootstrap">刷新</el-button>
      </div>
    </div>

    <section v-if="activeMode === 'practice'" class="mode-panel">
      <div class="workbench" :class="{ immersive: isImmersiveWorking }">
        <section class="workspace-main soft-panel">
          <div class="main-head">
            <div>
              <h3>练习题区</h3>
              <p>提交后即时判定，错误自动加入错题本</p>
            </div>
            <div v-if="practiceSession" class="focus-stats">
              <el-tag type="info">会话 #{{ practiceSession.id }}</el-tag>
              <el-tag type="primary">进度 {{ practiceCompletionText }}</el-tag>
              <el-tag :type="practiceSession.status === 'completed' ? 'success' : 'warning'">{{ statusText(practiceSession.status) }}</el-tag>
              <el-tag type="success">正确 {{ practiceSession.correctCount }}</el-tag>
            </div>
          </div>

          <section v-if="practiceSession" class="question-list">
            <article v-for="question in practiceSession.questions" :key="question.id" class="soft-panel question-card">
              <header class="question-head">
                <h4>第 {{ question.order }} 题</h4>
                <div class="question-meta">
                  <el-tag size="small" effect="plain">{{ question.knowledgeId || '学习节点' }}</el-tag>
                  <el-tag size="small" :type="practiceStatusType(question)">{{ practiceStatusText(question) }}</el-tag>
                  <el-tag size="small" type="info">{{ questionModeBadgeText(question, 'practice', practiceSession.id) }}</el-tag>
                </div>
              </header>

              <p class="question-stem">{{ question.stem }}</p>
              <el-radio-group v-model="practiceAnswers[question.id]" :disabled="question.answered || practiceSession.status === 'completed'">
                <el-radio
                  v-for="(opt, index) in question.options"
                  :key="`${question.id}-${index}`"
                  :label="optionKey(index)"
                  class="question-option"
                >
                  {{ optionLabel(index, opt) }}
                </el-radio>
              </el-radio-group>

              <div class="question-actions">
                <div class="left-actions">
                  <el-button
                    type="primary"
                    class="submit-primary-btn"
                    :icon="Promotion"
                    :disabled="question.answered || practiceSession.status === 'completed'"
                    :loading="submittingPracticeQuestionId === question.id"
                    @click="submitPracticeAnswer(question)"
                  >
                    提交本题
                  </el-button>
                  <el-button
                    type="primary"
                    class="submit-primary-btn"
                    plain
                    :icon="Star"
                    @click="toggleFavoriteQuestion(question, 'practice', practiceSession.id)"
                  >
                    {{ isFavoriteQuestion('practice', practiceSession.id, question.id) ? '取消收藏' : '收藏题目' }}
                  </el-button>
                </div>
                <span v-if="question.answered" class="answer-tip">
                  你的答案：{{ question.userAnswer }} ｜ 正确答案：{{ question.correctAnswer }}
                </span>
              </div>

              <p v-if="question.answered && question.explanation" class="explanation">{{ question.explanation }}</p>
            </article>
          </section>

          <el-empty v-else description="暂无练习会话，点击右侧“生成练习题”开始" />
        </section>

        <aside v-if="!isImmersiveWorking" class="workspace-side">
          <div class="soft-panel control-panel">
            <div class="control-group">
              <span class="label">学习路径</span>
              <el-select v-model="selectedPathId" placeholder="选择路径" style="width: 100%">
                <el-option v-for="path in paths" :key="path.id" :label="path.target" :value="path.id" />
              </el-select>
            </div>
            <div class="control-group">
              <span class="label">题量</span>
              <el-input-number v-model="practiceCount" :min="3" :max="20" />
            </div>
            <div class="control-actions">
              <el-button type="primary" class="submit-primary-btn" :icon="EditPen" :loading="generatingPractice" @click="handleGeneratePractice">生成练习题</el-button>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <section v-else class="mode-panel">
      <div class="workbench" :class="{ immersive: isImmersiveWorking }">
        <section class="workspace-main soft-panel">
          <div class="main-head">
            <div>
              <h3>测评题区</h3>
              <p>倒计时交卷，系统自动评分并记录</p>
            </div>
            <div v-if="examSession" class="focus-stats">
              <el-tag type="info">试卷 #{{ examSession.id }}</el-tag>
              <el-tag type="primary">进度 {{ examCompletionText }}</el-tag>
              <el-tag :type="examSession.status === 'completed' ? 'success' : 'warning'">{{ statusText(examSession.status) }}</el-tag>
              <el-tag :type="examTimerSeconds <= 60 && examSession.status !== 'completed' ? 'danger' : 'info'">
                <el-icon><Clock /></el-icon>
                {{ examSession.status === 'completed' ? '已交卷' : examRemainingText }}
              </el-tag>
              <el-tag v-if="examSession.status === 'completed'" type="success">{{ examSession.score }}分 / {{ examSession.grade }}</el-tag>
            </div>
          </div>

          <section v-if="examSession" class="question-list">
            <article v-for="question in examSession.questions" :key="question.id" class="soft-panel question-card">
              <header class="question-head">
                <h4>第 {{ question.order }} 题</h4>
                <div class="question-meta">
                  <el-tag size="small" effect="plain">{{ question.knowledgeId || '学习节点' }}</el-tag>
                  <el-tag v-if="examSession.status === 'completed'" size="small" :type="question.correct ? 'success' : 'danger'">
                    {{ question.correct ? '正确' : '错误' }}
                  </el-tag>
                  <el-tag size="small" type="info">{{ questionModeBadgeText(question, 'exam', examSession.id) }}</el-tag>
                </div>
              </header>

              <p class="question-stem">{{ question.stem }}</p>
              <el-radio-group v-model="examAnswers[question.id]" :disabled="examSession.status === 'completed'">
                <el-radio
                  v-for="(opt, index) in question.options"
                  :key="`${question.id}-${index}`"
                  :label="optionKey(index)"
                  class="question-option"
                >
                  {{ optionLabel(index, opt) }}
                </el-radio>
              </el-radio-group>

              <div class="question-actions">
                <el-button
                  type="primary"
                  class="submit-primary-btn"
                  plain
                  :icon="Star"
                  @click="toggleFavoriteQuestion(question, 'exam', examSession.id)"
                >
                  {{ isFavoriteQuestion('exam', examSession.id, question.id) ? '取消收藏' : '收藏题目' }}
                </el-button>
                <span v-if="examSession.status === 'completed'" class="answer-tip">
                  你的答案：{{ question.userAnswer || '未作答' }} ｜ 正确答案：{{ question.correctAnswer || '-' }}
                </span>
              </div>

              <p v-if="examSession.status === 'completed'" class="explanation">
                {{ question.explanation || '—' }}
              </p>
            </article>
          </section>

          <el-empty v-else description="暂无测评会话，点击右侧“智能组卷”开始" />
        </section>

        <aside v-if="!isImmersiveWorking" class="workspace-side">
          <div class="soft-panel control-panel">
            <div class="control-group">
              <span class="label">学习路径</span>
              <el-select v-model="selectedPathId" placeholder="选择路径" style="width: 100%">
                <el-option v-for="path in paths" :key="path.id" :label="path.target" :value="path.id" />
              </el-select>
            </div>
            <div class="control-group">
              <span class="label">题量</span>
              <el-input-number v-model="examCount" :min="5" :max="30" />
            </div>
            <div class="control-group">
              <span class="label">时长（分钟）</span>
              <el-input-number v-model="examDuration" :min="5" :max="180" />
            </div>
            <div class="control-actions">
              <el-button type="primary" class="submit-primary-btn" :icon="EditPen" :loading="generatingExam" @click="handleGenerateExam">智能组卷</el-button>
            </div>
          </div>

          <div v-if="examSession" class="submit-bar">
            <el-button
              type="primary"
              class="submit-primary-btn"
              :icon="Select"
              :disabled="examSession.status === 'completed'"
              :loading="submittingExam"
              @click="handleSubmitExam(false)"
            >
              {{ examSession.status === 'completed' ? '已交卷' : '交卷并评分' }}
            </el-button>
          </div>
        </aside>
      </div>
    </section>

    <el-drawer
      v-model="drawerVisible"
      size="420px"
      title="练与测记录中心"
      direction="rtl"
      append-to-body
      class="practice-record-drawer"
    >
      <div class="drawer-tabs">
        <el-button type="primary" class="submit-primary-btn" :plain="drawerTab !== 'records'" @click="drawerTab = 'records'">练测记录</el-button>
        <el-button type="primary" class="submit-primary-btn" :plain="drawerTab !== 'wrong'" @click="drawerTab = 'wrong'">错题本</el-button>
        <el-button type="primary" class="submit-primary-btn" :plain="drawerTab !== 'favorite'" @click="drawerTab = 'favorite'">收藏题集</el-button>
      </div>

      <div v-if="drawerTab === 'records'" class="drawer-list">
        <div v-if="recordList.length === 0" class="drawer-empty">暂无练测记录</div>
        <article v-for="record in recordList" :key="record.key" class="drawer-card">
          <div class="drawer-card-head">
            <el-tag :type="record.mode === 'practice' ? 'primary' : 'success'">
              {{ record.mode === 'practice' ? '练习' : '考试' }}
            </el-tag>
            <el-tag :type="record.status === 'completed' ? 'success' : 'warning'">{{ statusText(record.status) }}</el-tag>
          </div>
          <p class="drawer-title">会话 #{{ record.sessionId }}</p>
          <p class="drawer-meta">进度 {{ record.answeredCount }}/{{ record.totalQuestions }} ｜ 正确 {{ record.correctCount }}</p>
          <p v-if="record.mode === 'exam' && record.score != null" class="drawer-meta">得分 {{ record.score }} ｜ 等级 {{ record.grade || '-' }}</p>
          <p class="drawer-meta">更新时间：{{ new Date(record.updatedAt).toLocaleString('zh-CN') }}</p>
          <el-button type="primary" class="submit-primary-btn" plain @click="openRecord(record)">打开会话</el-button>
        </article>
      </div>

      <div v-else-if="drawerTab === 'wrong'" class="drawer-list">
        <div v-if="wrongBook.length === 0" class="drawer-empty">暂无错题</div>
        <article v-for="item in wrongBook" :key="item.key" class="drawer-card">
          <div class="drawer-card-head">
            <el-tag type="danger">错题</el-tag>
            <el-tag type="info">{{ item.mode === 'practice' ? '练习' : '考试' }}</el-tag>
          </div>
          <p class="drawer-title">第 {{ item.order }} 题：{{ item.stem }}</p>
          <p class="drawer-meta">你的答案：{{ item.userAnswer || '未作答' }} ｜ 正确答案：{{ item.correctAnswer || '-' }}</p>
          <p v-if="item.explanation" class="drawer-meta">{{ item.explanation }}</p>
          <div class="drawer-actions">
            <el-button type="primary" class="submit-primary-btn" plain @click="removeWrongQuestion(item)">移出错题本</el-button>
          </div>
        </article>
      </div>

      <div v-else class="drawer-list">
        <div v-if="favoriteBook.length === 0" class="drawer-empty">暂无收藏题目</div>
        <article v-for="item in favoriteBook" :key="item.key" class="drawer-card">
          <div class="drawer-card-head">
            <el-tag type="warning">收藏题</el-tag>
            <el-tag type="info">{{ item.mode === 'practice' ? '练习' : '考试' }}</el-tag>
          </div>
          <p class="drawer-title">第 {{ item.order }} 题：{{ item.stem }}</p>
          <p class="drawer-meta">标准答案：{{ item.correctAnswer || '-' }}</p>
          <div class="drawer-actions">
            <el-button type="primary" class="submit-primary-btn" plain @click="removeFavoriteQuestion(item)">取消收藏</el-button>
          </div>
        </article>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.practice-test-view {
  min-height: 100%;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.practice-test-view.immersive {
  padding-top: 8px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
}

.refresh-btn {
  margin-left: 4px;
}

.mode-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 420px;
}

.workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.38fr) minmax(250px, 0.62fr);
  gap: 12px;
  min-height: 0;
  flex: 1;
}

.workbench.immersive {
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
}

.workspace-main {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 18px;
  gap: 12px;
  width: 100%;
}

.main-head {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;

  h3 {
    margin: 0 0 6px;
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
  }
}

.focus-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.workspace-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.control-panel {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  padding: 12px;
}

.control-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.control-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 2px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  min-height: 0;
  padding-right: 4px;
  flex: 1;
}

.question-card {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  h4 {
    margin: 0;
    font-size: 16px;
    color: var(--text-primary);
  }
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.question-stem {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f3763;
}

.question-option {
  display: block;
  margin: 6px 0;
}

.question-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.left-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.answer-tip {
  font-size: 12px;
  color: var(--text-secondary);
}

.explanation {
  margin: 0;
  font-size: 13px;
  color: #3f557f;
  background: #f5f9ff;
  border: 1px solid #dbe7ff;
  border-radius: 10px;
  padding: 8px 10px;
}

.submit-bar {
  display: flex;
  justify-content: stretch;
}

.drawer-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.drawer-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 160px);
  overflow-y: auto;
}

.drawer-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.drawer-card {
  border: 1px solid var(--border-light);
  border-radius: 10px;
  background: #fff;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drawer-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.drawer-title {
  margin: 0;
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 600;
}

.drawer-meta {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
}

.submit-primary-btn {
  --el-button-bg-color: #2f6dff;
  --el-button-border-color: #2f6dff;
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: #1f5df5;
  --el-button-hover-border-color: #1f5df5;
  --el-button-active-bg-color: #1c54de;
  --el-button-active-border-color: #1c54de;
  --el-button-disabled-bg-color: #2f6dff;
  --el-button-disabled-border-color: #2f6dff;
  --el-button-disabled-text-color: #ffffff;
  min-width: 116px;
}

.submit-primary-btn.is-disabled {
  opacity: 0.7;
}

@media (max-width: 1280px) {
  .workbench {
    grid-template-columns: minmax(0, 1.3fr) minmax(230px, 0.7fr);
  }
}

@media (max-width: 1080px) {
  .workbench {
    grid-template-columns: 1fr;
  }

  .workspace-side {
    order: 2;
  }
}
</style>
