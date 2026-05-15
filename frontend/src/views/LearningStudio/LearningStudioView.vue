<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CollectionTag, Download, Refresh, Search, Upload, VideoPlay } from '@element-plus/icons-vue'
import {
  deleteLearningVideoHistory,
  getFavoriteLearningVideos,
  getLearningVideoDetail,
  getLearningVideoHistory,
  importOnlineLearningVideo,
  recordLearningVideoWatch,
  searchLearningVideos,
  searchOnlineLearningVideos,
  toggleLearningVideoFavorite,
  uploadLocalLearningVideo,
  type OnlineVideoSearchItem,
} from '@/api/learningVideo'
import type { LearningVideo } from '@/types'

const route = useRoute()

const loading = ref(false)
const localLoading = ref(false)
const onlineLoading = ref(false)
const keyword = ref('')
const searchPlatform = ref<'bilibili' | 'baidu'>('bilibili')
const onlyEmbeddable = ref(true)
const libraryDrawerVisible = ref(false)
const libraryDrawerTab = ref<'favorites' | 'history'>('favorites')

const videos = ref<LearningVideo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const localLibraryExpanded = ref(false)

const onlineResults = ref<OnlineVideoSearchItem[]>([])

const favorites = ref<LearningVideo[]>([])
const history = ref<LearningVideo[]>([])

const selectedMode = ref<'local' | 'online' | null>(null)
const selectedLocalVideo = ref<LearningVideo | null>(null)
const selectedOnlineVideo = ref<OnlineVideoSearchItem | null>(null)
const localUploadInputRef = ref<HTMLInputElement | null>(null)

let watchStartedAt = 0
let watchTimer: ReturnType<typeof setInterval> | null = null
const currentWatchedSeconds = ref(0)

const hasResults = computed(() => videos.value.length > 0)
const localLibraryLimit = 4
const displayedLocalVideos = computed(() => {
  if (localLibraryExpanded.value) {
    return videos.value
  }
  return videos.value.slice(0, localLibraryLimit)
})
const hasFavorites = computed(() => favorites.value.length > 0)
const hasHistory = computed(() => history.value.length > 0)
const isLocalSelected = computed(() => selectedMode.value === 'local' && !!selectedLocalVideo.value)
const isOnlineSelected = computed(() => selectedMode.value === 'online' && !!selectedOnlineVideo.value)
const onlinePlatformLabel = computed(() => (searchPlatform.value === 'baidu' ? '百度视频' : 'B站视频'))
const localPreferredVideos = computed(() => {
  return videos.value
    .filter((video) => canPlayInPage(toEmbeddedUrl(video.url, video.platform) || video.url))
    .slice(0, 3)
})
const hasLocalPreferredVideos = computed(() => localPreferredVideos.value.length > 0)
const isBilibiliSelected = computed(() => {
  if (isLocalSelected.value) {
    return String(selectedLocalVideo.value?.platform || '').toLowerCase().includes('bilibili')
  }
  if (isOnlineSelected.value) {
    return String(selectedOnlineVideo.value?.platform || '').toLowerCase().includes('bilibili')
  }
  return false
})

const selectedPlayerUrl = computed(() => {
  if (isLocalSelected.value) {
    const local = selectedLocalVideo.value!
    const embed = toEmbeddedUrl(local.url, local.platform)
    return embed || local.url
  }
  if (isOnlineSelected.value) {
    const online = selectedOnlineVideo.value!
    return online.embedUrl || toEmbeddedUrl(online.url, online.platform) || online.url
  }
  return ''
})

const selectedSourceUrl = computed(() => {
  if (isLocalSelected.value) return selectedLocalVideo.value?.url || ''
  if (isOnlineSelected.value) return selectedOnlineVideo.value?.url || ''
  return ''
})
const selectedCanPlayInPage = computed(() => canPlayInPage(selectedPlayerUrl.value))

function canPlayOnlineItem(video: OnlineVideoSearchItem) {
  const playerCandidate = video.embedUrl || toEmbeddedUrl(video.url, video.platform) || video.url
  return canPlayInPage(playerCandidate)
}

const displayedOnlineResults = computed(() => {
  const sorted = [...onlineResults.value].sort((a, b) => Number(canPlayOnlineItem(b)) - Number(canPlayOnlineItem(a)))
  if (onlyEmbeddable.value) {
    return sorted.filter((video) => canPlayOnlineItem(video))
  }
  return sorted
})

const hasOnlineResults = computed(() => displayedOnlineResults.value.length > 0)

function isDirectPlayableVideo(url: string) {
  const lower = String(url || '').toLowerCase()
  return lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.ogg')
}

function isEmbeddableUrl(url: string) {
  const lower = String(url || '').toLowerCase()
  return lower.includes('player.bilibili.com/player.html') || lower.includes('youtube.com/embed/')
}

function canPlayInPage(url: string) {
  if (!url) return false
  return isDirectPlayableVideo(url) || isEmbeddableUrl(url)
}

function toEmbeddedUrl(url: string, platform?: string) {
  const raw = String(url || '').trim()
  if (!raw) return ''

  const lowerPlatform = String(platform || '').toLowerCase()
  const bvidMatch = raw.match(/(?:\/video\/)(BV[0-9A-Za-z]+)/i)
  if (bvidMatch || lowerPlatform.includes('bilibili')) {
    const bvid = (bvidMatch?.[1] || '').toUpperCase()
    if (bvid) {
      return `https://player.bilibili.com/player.html?bvid=${bvid}&page=1`
    }
  }

  const youtubeMatch = raw.match(/[?&]v=([a-zA-Z0-9_-]{11})/) || raw.match(/youtu\.be\/([a-zA-Z0-9_-]{11})/)
  if (youtubeMatch) {
    return `https://www.youtube.com/embed/${youtubeMatch[1]}`
  }

  return raw
}

function formatDuration(seconds?: number) {
  const safe = Math.max(0, Number(seconds || 0))
  const hh = Math.floor(safe / 3600)
  const mm = Math.floor((safe % 3600) / 60)
  const ss = safe % 60
  if (hh > 0) {
    return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
  }
  return `${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
}

function formatPercent(value?: number) {
  return `${Math.round(Math.max(0, Number(value || 0)) * 100)}%`
}

function stopWatchTicker() {
  if (watchTimer) {
    clearInterval(watchTimer)
    watchTimer = null
  }
}

async function flushWatchRecord() {
  if (!isLocalSelected.value || !selectedLocalVideo.value || watchStartedAt <= 0) {
    stopWatchTicker()
    return
  }
  const elapsed = Math.floor((Date.now() - watchStartedAt) / 1000)
  if (elapsed <= 0 || !Number.isFinite(selectedLocalVideo.value.id)) {
    stopWatchTicker()
    return
  }

  const totalSeconds = Math.max(1, currentWatchedSeconds.value + elapsed)
  try {
    await recordLearningVideoWatch(selectedLocalVideo.value.id, totalSeconds)
  } catch {
    // request interceptor handles tip
  } finally {
    stopWatchTicker()
    watchStartedAt = 0
  }
}

function startWatchTicker() {
  stopWatchTicker()
  watchStartedAt = Date.now()
  watchTimer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - watchStartedAt) / 1000)
    currentWatchedSeconds.value = Math.max(currentWatchedSeconds.value, elapsed)
  }, 1000)
}

async function loadSearch() {
  localLoading.value = true
  try {
    const res = await searchLearningVideos(keyword.value.trim(), page.value, size.value, true)
    videos.value = res.data.data.records || []
    total.value = Number(res.data.data.total || 0)
  } finally {
    localLoading.value = false
  }
}

async function loadFavorites() {
  const res = await getFavoriteLearningVideos(40)
  favorites.value = res.data.data || []
}

async function loadHistory() {
  const res = await getLearningVideoHistory(40)
  history.value = res.data.data || []
}

async function refreshSideLists() {
  await Promise.all([loadFavorites(), loadHistory()])
}

async function handleSearchLocal() {
  page.value = 1
  await loadSearch()
}

function openLibraryDrawer(tab: 'favorites' | 'history' = 'favorites') {
  libraryDrawerTab.value = tab
  libraryDrawerVisible.value = true
}

async function handleSearchOnline() {
  const q = keyword.value.trim()
  if (!q) {
    ElMessage.warning('请先输入关键词再进行联网搜索')
    return
  }
  onlineLoading.value = true
  try {
    const res = await searchOnlineLearningVideos(q, 12, searchPlatform.value)
    onlineResults.value = res.data.data || []
    if (displayedOnlineResults.value.length === 0) {
      if (onlyEmbeddable.value) {
        ElMessage.info('已开启“仅站内可播”，当前关键词下没有可站内播放的视频')
      } else {
        ElMessage.info('联网搜索暂无结果，换一个关键词试试')
      }
    }
  } finally {
    onlineLoading.value = false
  }
}

async function handlePageChange(nextPage: number) {
  page.value = nextPage
  await loadSearch()
}

async function handleToggleFavorite(video: LearningVideo) {
  const target = !video.favorite
  await toggleLearningVideoFavorite(video.id, target)
  video.favorite = target
  if (selectedLocalVideo.value?.id === video.id) {
    selectedLocalVideo.value.favorite = target
  }
  ElMessage.success(target ? '已加入收藏' : '已取消收藏')
  await refreshSideLists()
}

async function handlePlayerFavorite() {
  if (isLocalSelected.value && selectedLocalVideo.value) {
    await handleToggleFavorite(selectedLocalVideo.value)
    return
  }
  if (isOnlineSelected.value && selectedOnlineVideo.value) {
    await handleImportOnline(selectedOnlineVideo.value)
  }
}

async function handleRemoveFavorite(video: LearningVideo) {
  await toggleLearningVideoFavorite(video.id, false)
  if (video.favorite) {
    video.favorite = false
  }
  const localTarget = videos.value.find((item) => item.id === video.id)
  if (localTarget) {
    localTarget.favorite = false
  }
  if (selectedLocalVideo.value?.id === video.id) {
    selectedLocalVideo.value.favorite = false
  }
  ElMessage.success('已从收藏中移除')
  await refreshSideLists()
}

async function handleRemoveHistory(video: LearningVideo) {
  await deleteLearningVideoHistory(video.id)
  if (selectedLocalVideo.value?.id === video.id) {
    selectedLocalVideo.value.watchedSeconds = 0
    selectedLocalVideo.value.completionRate = 0
    selectedLocalVideo.value.lastWatchedAt = null
  }
  const localTarget = videos.value.find((item) => item.id === video.id)
  if (localTarget) {
    localTarget.watchedSeconds = 0
    localTarget.completionRate = 0
    localTarget.lastWatchedAt = null
  }
  history.value = history.value.filter((item) => item.id !== video.id)
  ElMessage.success('已删除观看记录')
}

async function openLocalVideoById(videoId: number) {
  await flushWatchRecord()
  const detail = await getLearningVideoDetail(videoId)
  const playerCandidate = toEmbeddedUrl(detail.data.data.url, detail.data.data.platform) || detail.data.data.url
  selectedMode.value = 'local'
  selectedLocalVideo.value = detail.data.data
  selectedOnlineVideo.value = null
  currentWatchedSeconds.value = detail.data.data.watchedSeconds || 0
  if (!canPlayInPage(playerCandidate)) {
    ElMessage.warning('该视频不支持站内播放，已为你打开原链接')
    window.open(detail.data.data.url, '_blank')
    stopWatchTicker()
    watchStartedAt = 0
    return
  }
  startWatchTicker()
}

async function openLocalVideo(video: LearningVideo) {
  await openLocalVideoById(video.id)
}

async function openOnlineVideo(video: OnlineVideoSearchItem) {
  await flushWatchRecord()
  stopWatchTicker()
  watchStartedAt = 0
  const playerCandidate = video.embedUrl || toEmbeddedUrl(video.url, video.platform) || video.url
  if (!canPlayInPage(playerCandidate)) {
    ElMessage.info('该视频不支持站内播放，已打开原链接')
    window.open(video.url, '_blank')
    return
  }
  selectedMode.value = 'online'
  selectedOnlineVideo.value = video
  selectedLocalVideo.value = null
  currentWatchedSeconds.value = 0
}

async function handleImportOnline(video: OnlineVideoSearchItem) {
  const res = await importOnlineLearningVideo({
    title: video.title,
    description: video.description,
    platform: video.platform || 'bilibili',
    url: video.url,
    coverUrl: video.coverUrl,
    durationSeconds: video.durationSeconds || 0,
    embedUrl: video.embedUrl,
    tags: video.tags || ['online', 'bilibili'],
    favorite: true,
  })

  ElMessage.success('已保存到学习看台，并加入收藏')
  await Promise.all([loadSearch(), refreshSideLists()])

  const importedId = Number(res.data.data?.id || 0)
  if (importedId > 0) {
    await openLocalVideoById(importedId)
  }
}

function triggerLocalUpload() {
  localUploadInputRef.value?.click()
}

async function handleLocalFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const lowerName = file.name.toLowerCase()
  const allowedExt = ['.mp4', '.webm', '.ogg', '.m4v']
  if (!allowedExt.some((ext) => lowerName.endsWith(ext))) {
    ElMessage.warning('仅支持 MP4 / WebM / OGG / M4V 视频文件')
    target.value = ''
    return
  }

  try {
    loading.value = true
    const res = await uploadLocalLearningVideo(file)
    ElMessage.success('本地视频已上传到学习资源库')
    await Promise.all([loadSearch(), refreshSideLists()])

    const uploadedId = Number(res.data.data?.id || 0)
    if (uploadedId > 0) {
      await openLocalVideoById(uploadedId)
    }
  } finally {
    loading.value = false
    target.value = ''
  }
}

async function openFromRoute() {
  const raw = String(route.query.videoId || '').trim()
  const parsed = Number(raw)
  if (Number.isFinite(parsed) && parsed > 0) {
    await openLocalVideoById(parsed)
    return
  }
  if (!selectedMode.value) {
    const first = videos.value[0]
    if (first) {
      await openLocalVideo(first)
    }
  }
}

function openSelectedVideoSource() {
  const url = selectedSourceUrl.value
  if (!url) return
  window.open(url, '_blank')
}

function openExternalUrl(url?: string) {
  const target = String(url || '').trim()
  if (!target) return
  window.open(target, '_blank')
}

async function initialize() {
  loading.value = true
  try {
    await Promise.all([loadSearch(), refreshSideLists()])
    await openFromRoute()
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query.videoId,
  async () => {
    await openFromRoute()
  },
)

onMounted(() => {
  void initialize()
})

onBeforeUnmount(() => {
  void flushWatchRecord()
})
</script>

<template>
  <div class="learning-studio-view page-shell" v-loading="loading">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">学习看台</h2>
        <p class="page-subtitle">支持本地视频学习与联网视频搜索，形成收藏和观看记录</p>
      </div>
      <div class="page-toolbar">
        <input
          ref="localUploadInputRef"
          type="file"
          accept=".mp4,.webm,.ogg,.m4v,video/mp4,video/webm,video/ogg"
          style="display: none"
          @change="handleLocalFileChange"
        />
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          placeholder="输入技术关键词，例如：Spring Boot、Redis、Vue3"
          clearable
          style="width: 420px"
          @keyup.enter="handleSearchLocal"
        />
        <el-button type="primary" class="solid-action-btn" :icon="Search" :loading="localLoading" @click="handleSearchLocal">本地文件搜索</el-button>
        <el-button type="primary" class="solid-action-btn" :icon="Upload" @click="triggerLocalUpload">上传到本地资源库</el-button>
        <el-select v-model="searchPlatform" style="width: 120px">
          <el-option label="B站" value="bilibili" />
          <el-option label="百度视频" value="baidu" />
        </el-select>
        <el-switch v-model="onlyEmbeddable" inline-prompt active-text="仅站内可播" inactive-text="全部结果" />
        <el-button type="primary" class="solid-action-btn" :icon="VideoPlay" :loading="onlineLoading" @click="handleSearchOnline">联网搜索({{ onlinePlatformLabel }})</el-button>
        <el-button type="primary" class="solid-action-btn" :icon="CollectionTag" @click="openLibraryDrawer('favorites')">
          收藏夹({{ favorites.length }})
        </el-button>
        <el-button type="primary" class="solid-action-btn" :icon="VideoPlay" @click="openLibraryDrawer('history')">
          观看记录({{ history.length }})
        </el-button>
        <el-button type="primary" class="solid-action-btn" :icon="Refresh" @click="initialize">刷新</el-button>
      </div>
    </div>

    <div class="studio-layout">
      <section class="player-panel soft-panel">
        <div class="player-head">
          <span class="player-title">视频播放区（主视图）</span>
          <div class="player-head-actions">
            <el-button
              v-if="selectedMode"
              type="primary"
              class="solid-action-btn"
              :icon="CollectionTag"
              @click="handlePlayerFavorite"
            >
              {{ isLocalSelected ? (selectedLocalVideo?.favorite ? '取消收藏' : '收藏视频') : '保存并收藏' }}
            </el-button>
            <el-button v-if="selectedSourceUrl" type="primary" class="solid-action-btn" @click="openSelectedVideoSource">打开原链接</el-button>
          </div>
        </div>

        <div v-if="selectedMode && selectedPlayerUrl && selectedCanPlayInPage" class="player-content">
          <div class="player-wrap">
            <video
              v-if="isDirectPlayableVideo(selectedPlayerUrl)"
              controls
              :src="selectedPlayerUrl"
              class="video-player"
            />
            <iframe
              v-else
              :src="selectedPlayerUrl"
              class="video-player"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
              allowfullscreen
            />
          </div>
          <el-alert
            v-if="isBilibiliSelected"
            class="bilibili-tip"
            type="warning"
            :closable="false"
            title="部分B站视频不支持站内播放，若黑屏请打开原链接观看。"
          />

          <div class="player-info">
            <h3>{{ isLocalSelected ? selectedLocalVideo?.title : selectedOnlineVideo?.title }}</h3>
            <p>{{ isLocalSelected ? selectedLocalVideo?.description : selectedOnlineVideo?.description }}</p>
            <div class="player-info-meta">
              <span>来源：{{ isLocalSelected ? (selectedLocalVideo?.platform || '本地') : (selectedOnlineVideo?.platform || 'bilibili') }}</span>
              <span>时长：{{ formatDuration(isLocalSelected ? selectedLocalVideo?.durationSeconds : selectedOnlineVideo?.durationSeconds) }}</span>
              <span v-if="isLocalSelected">已观看：{{ formatDuration(selectedLocalVideo?.watchedSeconds) }}</span>
              <span v-if="isLocalSelected">完成度：{{ formatPercent(selectedLocalVideo?.completionRate) }}</span>
              <span v-if="isLocalSelected && selectedLocalVideo?.lastWatchedAt">
                最近观看：{{ new Date(selectedLocalVideo.lastWatchedAt).toLocaleString('zh-CN') }}
              </span>
            </div>
          </div>
        </div>

        <div v-else-if="selectedMode && selectedSourceUrl" class="player-empty">
          <el-icon :size="32"><VideoPlay /></el-icon>
          <p>该视频不支持站内播放，点击下方按钮前往原站观看</p>
          <el-button type="primary" class="solid-action-btn" @click="openSelectedVideoSource">打开原链接观看</el-button>
        </div>

        <div v-else class="player-empty">
          <el-icon :size="32"><VideoPlay /></el-icon>
          <p>选择右侧视频列表即可在页面内播放</p>
        </div>
      </section>

      <section class="video-list soft-panel">
        <div class="list-head">
          <span class="list-title">本地优先推荐（稳定站内播放）</span>
          <span class="list-count">最多 3 条</span>
        </div>
        <div v-if="!hasLocalPreferredVideos" class="list-empty small">当前关键词下暂无可站内播放的本地视频</div>
        <div v-else class="preferred-list">
          <button
            v-for="video in localPreferredVideos"
            :key="`preferred-${video.id}`"
            type="button"
            class="preferred-item"
            @click="openLocalVideo(video)"
          >
            <span class="preferred-title">{{ video.title }}</span>
            <span class="preferred-meta">{{ video.platform || 'web' }} · {{ formatDuration(video.durationSeconds) }}</span>
          </button>
        </div>

        <div class="online-head">
          <span class="list-title">联网搜索结果（{{ onlinePlatformLabel }}）</span>
          <span class="list-count">{{ displayedOnlineResults.length }} / {{ onlineResults.length }} 条</span>
        </div>
        <div v-if="!hasOnlineResults && !onlineLoading" class="list-empty small">
          {{ onlyEmbeddable ? '已开启仅站内可播：当前无可播放结果' : `点击“联网搜索（${onlinePlatformLabel}）”即可显示结果` }}
        </div>
        <div v-else class="online-grid">
          <article v-for="video in displayedOnlineResults" :key="video.externalId" class="video-card online-card">
            <div class="video-card-top">
              <h4 class="video-title">{{ video.title }}</h4>
              <el-tag size="small" type="info">联网</el-tag>
            </div>
            <p class="video-description">{{ video.description || '暂无描述' }}</p>
            <div class="video-meta">
              <el-tag size="small">{{ video.platform || 'bilibili' }}</el-tag>
              <el-tag size="small" type="info">{{ formatDuration(video.durationSeconds) }}</el-tag>
              <el-tag size="small" :type="canPlayOnlineItem(video) ? 'success' : 'warning'">
                {{ canPlayOnlineItem(video) ? '可站内播放' : '需跳转原站' }}
              </el-tag>
            </div>
            <div class="online-actions">
              <el-button
                v-if="canPlayOnlineItem(video)"
                size="small"
                type="primary"
                class="solid-action-btn"
                @click="openOnlineVideo(video)"
              >
                播放
              </el-button>
              <el-button
                v-else
                size="small"
                type="primary"
                class="solid-action-btn"
                @click="openExternalUrl(video.url)"
              >
                打开原链接
              </el-button>
              <el-button size="small" type="primary" class="solid-action-btn" :icon="Download" @click="handleImportOnline(video)">保存到看台</el-button>
            </div>
          </article>
        </div>

        <div class="list-head local-head">
          <span class="list-title">本地视频库（精简）</span>
          <div class="local-head-actions">
            <span class="list-count">共 {{ total }} 条</span>
            <el-button
              v-if="videos.length > localLibraryLimit"
              size="small"
              type="primary"
              class="solid-action-btn"
              @click="localLibraryExpanded = !localLibraryExpanded"
            >
              {{ localLibraryExpanded ? '收起' : '展开更多' }}
            </el-button>
          </div>
        </div>
        <div v-if="!hasResults" class="list-empty">暂无匹配视频，请尝试联网搜索后保存到看台</div>
        <div v-else class="list-grid">
          <article v-for="video in displayedLocalVideos" :key="video.id" class="video-card" @click="openLocalVideo(video)">
            <div class="video-card-top">
              <h4 class="video-title">{{ video.title }}</h4>
              <el-button
                type="primary"
                :icon="CollectionTag"
                size="small"
                class="solid-action-btn favorite-btn"
                @click.stop="handleToggleFavorite(video)"
              >
                {{ video.favorite ? '已收藏' : '收藏' }}
              </el-button>
            </div>
            <p class="video-description">{{ video.description || '暂无描述' }}</p>
            <div class="video-meta">
              <el-tag size="small">{{ video.platform || 'web' }}</el-tag>
              <el-tag size="small" type="info">{{ formatDuration(video.durationSeconds) }}</el-tag>
              <el-tag size="small" type="success">进度 {{ formatPercent(video.completionRate) }}</el-tag>
            </div>
            <div class="video-tags">
              <el-tag v-for="tag in video.tags || []" :key="`${video.id}-${tag}`" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </article>
        </div>

        <div class="pager">
          <el-pagination
            background
            layout="prev, pager, next"
            :total="total"
            :current-page="page"
            :page-size="size"
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </div>

    <el-drawer
      v-model="libraryDrawerVisible"
      size="360px"
      title="收藏与观看记录"
      direction="rtl"
      append-to-body
      class="library-drawer"
    >
      <div class="drawer-tabs">
        <el-button
          type="primary"
          class="solid-action-btn"
          :plain="libraryDrawerTab !== 'favorites'"
          @click="libraryDrawerTab = 'favorites'"
        >
          我的收藏
        </el-button>
        <el-button
          type="primary"
          class="solid-action-btn"
          :plain="libraryDrawerTab !== 'history'"
          @click="libraryDrawerTab = 'history'"
        >
          观看记录
        </el-button>
      </div>

      <div v-if="libraryDrawerTab === 'favorites'" class="drawer-block">
        <div v-if="!hasFavorites" class="side-empty">暂无收藏视频</div>
        <div v-else class="side-list">
          <div v-for="video in favorites" :key="`fav-${video.id}`" class="side-item">
            <button type="button" class="side-item-main" @click="openLocalVideo(video)">
              <span class="side-item-title">{{ video.title }}</span>
              <span class="side-item-meta">{{ formatDuration(video.durationSeconds) }}</span>
            </button>
            <el-button size="small" type="primary" class="solid-action-btn side-delete-btn" @click.stop="handleRemoveFavorite(video)">
              删除
            </el-button>
          </div>
        </div>
      </div>

      <div v-else class="drawer-block">
        <div v-if="!hasHistory" class="side-empty">暂无观看记录</div>
        <div v-else class="side-list">
          <div v-for="video in history" :key="`his-${video.id}`" class="side-item">
            <button type="button" class="side-item-main" @click="openLocalVideo(video)">
              <span class="side-item-title">{{ video.title }}</span>
              <span class="side-item-meta">{{ formatPercent(video.completionRate) }}</span>
            </button>
            <el-button size="small" type="primary" class="solid-action-btn side-delete-btn" @click.stop="handleRemoveHistory(video)">
              删除
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.learning-studio-view {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-bottom: 12px;
  scrollbar-gutter: stable;
}

.studio-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  min-height: 0;
  align-items: start;
}

.video-list,
.player-panel {
  min-height: 0;
  padding: 12px;
}

.video-list {
  max-height: calc(100vh - 190px);
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-gutter: stable;
}

.page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.online-head,
.list-head,
.player-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.player-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.local-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.local-head {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}

.list-title,
.player-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.list-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.list-empty,
.player-empty,
.side-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.list-empty.small {
  min-height: 64px;
}

.online-grid,
.list-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.video-card {
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 10px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.preferred-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.preferred-item {
  border: 1px solid var(--border-light);
  border-radius: 10px;
  background: #fff;
  padding: 8px;
  text-align: left;
  cursor: pointer;
}

.preferred-item:hover {
  border-color: var(--primary-color);
  background: #f7faff;
}

.preferred-title {
  display: block;
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.preferred-meta {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
}

.online-card {
  background: #fafcff;
}

.list-grid .video-card {
  cursor: pointer;
}

.list-grid .video-card:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}

.video-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.video-title {
  font-size: 14px;
  line-height: 1.4;
  color: var(--text-primary);
}

.favorite-btn {
  padding: 0;
}

.solid-action-btn {
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
}

.solid-action-btn.el-button:not(.is-text):not(.is-link):hover,
.solid-action-btn.el-button:not(.is-text):not(.is-link):active {
  transform: none !important;
}

.video-description {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 6px;
  min-height: 34px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 8;
  overflow: hidden;
}

.list-grid .video-description {
  -webkit-line-clamp: 4;
}

.video-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.online-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.video-tags {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}

.player-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bilibili-tip {
  margin-top: 2px;
}

.player-wrap {
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: 460px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-light);
  background: #0d1324;
}

.video-player {
  width: 100%;
  height: 100%;
  border: none;
}

.player-info h3 {
  font-size: 16px;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.player-info p {
  font-size: 13px;
  color: var(--text-secondary);
}

.player-info-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.drawer-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.drawer-block {
  min-height: 0;
}

.side-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.side-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.side-item {
  border: 1px solid var(--border-light);
  border-radius: 10px;
  background: #fff;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-item:hover {
  border-color: var(--primary-color);
  background: #f7faff;
}

.side-item-main {
  border: none;
  background: transparent;
  text-align: left;
  padding: 0;
  cursor: pointer;
}

.side-item-main:hover .side-item-title {
  color: var(--primary-color);
}

.side-item-title {
  display: block;
  font-size: 12px;
  color: var(--text-primary);
}

.side-item-meta {
  font-size: 11px;
  color: var(--text-secondary);
}

.side-delete-btn {
  align-self: flex-end;
}

@media (max-width: 1280px) {
  .studio-layout {
    grid-template-columns: 1fr;
  }

  .video-list {
    max-height: none;
    overflow: visible;
  }

  .player-wrap {
    min-height: 0;
  }

  .online-grid,
  .list-grid,
  .preferred-list {
    grid-template-columns: 1fr;
  }
}
</style>

