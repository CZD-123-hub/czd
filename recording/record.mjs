import { chromium } from 'playwright'
import path from 'path'
import { fileURLToPath } from 'url'
import fs from 'fs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUTPUT_DIR = path.join(__dirname, 'videos')

// ============================================================
// 配置
// ============================================================
const BASE_URL = 'http://localhost:5173'
const USERNAME = 'zhangsan'
const PASSWORD = '12345678'

// 操作间隔（毫秒）— 让录屏看起来自然
const PAUSE = 1500        // 普通停顿
const SHORT = 800         // 短停顿
const LONG = 3000         // 重点展示停顿
const TYPING_DELAY = 80   // 打字速度（每字符毫秒）

// ============================================================
// 工具函数
// ============================================================

/** 等待指定毫秒 */
function sleep(ms) {
  return new Promise(r => setTimeout(r, ms))
}

/** 缓慢输入文字（模拟人工打字） */
async function slowType(page, selector, text, delay = TYPING_DELAY) {
  await page.click(selector)
  await sleep(300)
  await page.type(selector, text, { delay })
}

/** 平滑滚动 */
async function smoothScroll(page, selector, distance, duration = 1000) {
  await page.evaluate(
    ({ sel, dist, dur }) => {
      const el = sel ? document.querySelector(sel) : window
      if (!el) return
      const target = sel ? el : document.documentElement
      const start = target.scrollTop
      const startTime = performance.now()
      function step(currentTime) {
        const elapsed = currentTime - startTime
        const progress = Math.min(elapsed / dur, 1)
        const ease = progress < 0.5
          ? 2 * progress * progress
          : -1 + (4 - 2 * progress) * progress
        target.scrollTop = start + dist * ease
        if (progress < 1) requestAnimationFrame(step)
      }
      requestAnimationFrame(step)
    },
    { sel: selector, dist: distance, dur: duration }
  )
  await sleep(duration + 200)
}

// ============================================================
// 录屏脚本主体
// ============================================================

async function main() {
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true })
  }

  console.log('🎬 启动浏览器...')

  const browser = await chromium.launch({
    headless: false,          // 有头模式，可以同时看到操作
    slowMo: 50,               // 每步操作稍微慢一点
    args: ['--window-size=1440,900'],
  })

  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: {
      dir: OUTPUT_DIR,
      size: { width: 1440, height: 900 },
    },
    locale: 'zh-CN',
  })

  const page = await context.newPage()

  try {
    // ==================================================================
    // 第1段：登录
    // ==================================================================
    console.log('📍 第1段：登录')

    await page.goto(BASE_URL, { waitUntil: 'networkidle' })
    await sleep(LONG)  // 展示登录页

    // 输入用户名
    await slowType(page, 'input[placeholder="请输入用户名"]', USERNAME)
    await sleep(SHORT)

    // 输入密码
    await slowType(page, 'input[placeholder="请输入密码"]', PASSWORD)
    await sleep(SHORT)

    // 点击登录
    await page.click('button:has-text("登 录")')
    await page.waitForURL('**/chat', { timeout: 10000 })
    await sleep(LONG)  // 展示聊天页加载完成

    // ==================================================================
    // 第2段：智能问答
    // ==================================================================
    console.log('📍 第2段：智能问答')

    // 2.1 展示欢迎页
    await sleep(PAUSE)

    // 点击历史对话
    const firstConv = page.locator('.conversation-item').first()
    if (await firstConv.isVisible({ timeout: 3000 }).catch(() => false)) {
      await firstConv.click()
      await sleep(LONG)

      // 滚动查看历史消息
      await smoothScroll(page, '.message-list', 500, 1500)
      await sleep(PAUSE)
      await smoothScroll(page, '.message-list', -500, 1000)
      await sleep(PAUSE)
    }

    // 2.2 新建对话
    await page.click('button:has-text("新建对话")')
    await sleep(PAUSE)

    // 点击快捷问题
    const quickBtn = page.locator('.quick-actions .el-button:has-text("解释 JavaScript 中的闭包")')
    if (await quickBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await quickBtn.click()
      await sleep(SHORT)
    } else {
      // 直接输入
      await slowType(page, '.el-textarea__inner', '解释 JavaScript 中的闭包')
      await sleep(SHORT)
    }

    // 发送消息
    await page.click('.send-btn')
    await sleep(1000)

    // 等待流式回答完成（最长等60秒）
    console.log('  ⏳ 等待 AI 回答...')
    try {
      // 等待 streaming 结束 — 发送按钮恢复可用
      await page.waitForFunction(
        () => {
          const btn = document.querySelector('.send-btn')
          return btn && !btn.classList.contains('is-loading') && !btn.disabled
        },
        { timeout: 60000, polling: 1000 }
      )
    } catch {
      console.log('  ⚠️ 等待超时，继续录制')
    }
    await sleep(LONG)

    // 滚动查看回复
    await smoothScroll(page, '.message-list', 300, 1000)
    await sleep(PAUSE)

    // 2.3 追问
    await slowType(page, '.el-textarea__inner', '用一个简单的例子说明')
    await sleep(SHORT)
    await page.click('.send-btn')

    console.log('  ⏳ 等待第二次回答...')
    try {
      await page.waitForFunction(
        () => {
          const btn = document.querySelector('.send-btn')
          return btn && !btn.classList.contains('is-loading') && !btn.disabled
        },
        { timeout: 60000, polling: 1000 }
      )
    } catch {
      console.log('  ⚠️ 等待超时，继续录制')
    }
    await sleep(LONG)

    // 点赞反馈
    const thumbsUp = page.locator('.feedback-btn').first()
    if (await thumbsUp.isVisible({ timeout: 2000 }).catch(() => false)) {
      await thumbsUp.click()
      await sleep(PAUSE)
    }

    // ==================================================================
    // 第3段：知识图谱
    // ==================================================================
    console.log('📍 第3段：知识图谱')

    await page.click('.el-menu-item:has-text("知识图谱")')
    await page.waitForLoadState('networkidle')
    await sleep(LONG)  // 展示图谱加载

    // 等待图谱渲染
    await sleep(LONG)

    // 点击分类标签
    const categoryTag = page.locator('.category-tag').first()
    if (await categoryTag.isVisible({ timeout: 3000 }).catch(() => false)) {
      await categoryTag.click()
      await sleep(PAUSE)
    }

    // 重置
    const resetBtn = page.locator('button:has-text("重置")')
    if (await resetBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await resetBtn.click()
      await sleep(LONG)
    }

    // 搜索
    await slowType(page, 'input[placeholder="搜索知识节点..."]', 'Java')
    await sleep(SHORT)
    await page.click('button:has-text("搜索")')
    await sleep(LONG)

    // 点击图谱中的节点（通过 canvas 点击中心区域）
    const graphContainer = page.locator('.graph-container')
    if (await graphContainer.isVisible({ timeout: 2000 }).catch(() => false)) {
      const box = await graphContainer.boundingBox()
      if (box) {
        await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2)
        await sleep(LONG)
      }
    }

    // 如果详情面板出现，展示并关闭
    const detailPanel = page.locator('.detail-panel')
    if (await detailPanel.isVisible({ timeout: 3000 }).catch(() => false)) {
      await sleep(LONG)  // 展示详情

      // 点击展开关联
      const expandBtn = page.locator('button:has-text("展开关联节点")')
      if (await expandBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expandBtn.click()
        await sleep(LONG)
      }

      // 关闭面板
      const closeBtn = detailPanel.locator('button').first()
      if (await closeBtn.isVisible()) {
        await closeBtn.click()
        await sleep(PAUSE)
      }
    }

    // ==================================================================
    // 第4段：学习路径
    // ==================================================================
    console.log('📍 第4段：学习路径')

    await page.click('.el-menu-item:has-text("学习路径")')
    await page.waitForLoadState('networkidle')
    await sleep(LONG)

    // 点击第一条路径
    const firstPath = page.locator('.path-card').first()
    if (await firstPath.isVisible({ timeout: 3000 }).catch(() => false)) {
      await firstPath.click()
      await sleep(LONG)

      // 滚动查看节点
      await smoothScroll(page, '.detail-body', 300, 1500)
      await sleep(PAUSE)
      await smoothScroll(page, '.detail-body', -300, 1000)
      await sleep(PAUSE)
    }

    // 生成新路径
    const genBtn = page.locator('button:has-text("生成路径")')
    if (await genBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await genBtn.click()
      await sleep(PAUSE)

      // 填写目标
      await slowType(page, 'input[placeholder*="掌握 React"]', '掌握 React 全家桶')
      await sleep(SHORT)

      // 填写已有知识
      await slowType(page, 'textarea[placeholder*="HTML"]', 'HTML, CSS, JavaScript 基础')
      await sleep(SHORT)

      // 点击生成
      const confirmGen = page.locator('.el-dialog__footer button:has-text("生成")')
      await confirmGen.click()

      console.log('  ⏳ 等待路径生成...')
      try {
        await page.waitForResponse(
          resp => resp.url().includes('/api/path/generate') && resp.status() === 200,
          { timeout: 30000 }
        )
      } catch {
        console.log('  ⚠️ 等待超时，继续录制')
      }
      await sleep(LONG)

      // 查看新路径
      const newPath = page.locator('.path-card').last()
      if (await newPath.isVisible({ timeout: 3000 }).catch(() => false)) {
        await newPath.click()
        await sleep(LONG)
      }
    }

    // ==================================================================
    // 第5段：代码片段
    // ==================================================================
    console.log('📍 第5段：代码片段')

    await page.click('.el-menu-item:has-text("代码片段")')
    await page.waitForLoadState('networkidle')
    await sleep(LONG)

    // 浏览片段
    await smoothScroll(page, '.snippet-list', 300, 1500)
    await sleep(PAUSE)
    await smoothScroll(page, '.snippet-list', -300, 1000)
    await sleep(PAUSE)

    // 搜索
    await slowType(page, 'input[placeholder="搜索代码片段..."]', 'Spring')
    await sleep(SHORT)
    await page.click('.toolbar-left button:has-text("搜索")')
    await sleep(LONG)

    // 清空搜索
    const clearIcon = page.locator('input[placeholder="搜索代码片段..."]').locator('..').locator('.el-input__clear')
    if (await clearIcon.isVisible({ timeout: 1000 }).catch(() => false)) {
      await clearIcon.click()
    } else {
      await page.fill('input[placeholder="搜索代码片段..."]', '')
    }
    await page.click('.toolbar-left button:has-text("搜索")')
    await sleep(PAUSE)

    // 新建代码片段
    await page.click('button:has-text("新建")')
    await sleep(PAUSE)

    // 填写表单
    await slowType(page, '.el-dialog input[placeholder="代码片段标题"]', 'Vue3 响应式数据')
    await sleep(SHORT)

    // 描述
    const descInput = page.locator('.el-dialog textarea[placeholder="简要描述..."]')
    if (await descInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await descInput.click()
      await descInput.type('Vue3 响应式 API 基本用法', { delay: TYPING_DELAY })
      await sleep(SHORT)
    }

    // 标签
    const tagInputEl = page.locator('.el-dialog .tag-editor input[placeholder="添加标签"]')
    if (await tagInputEl.isVisible({ timeout: 2000 }).catch(() => false)) {
      await tagInputEl.click()
      await tagInputEl.type('vue3', { delay: TYPING_DELAY })
      await tagInputEl.press('Enter')
      await sleep(SHORT)
      await tagInputEl.type('响应式', { delay: TYPING_DELAY })
      await tagInputEl.press('Enter')
      await sleep(SHORT)
    }

    await sleep(PAUSE)

    // 取消（避免保存失败影响录屏）
    await page.click('.el-dialog__footer button:has-text("取消")')
    await sleep(PAUSE)

    // 导出
    const exportBtn = page.locator('button:has-text("导出")')
    if (await exportBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await exportBtn.click()
      await sleep(PAUSE)
    }

    // ==================================================================
    // 第6段：学习仪表盘
    // ==================================================================
    console.log('📍 第6段：学习仪表盘')

    await page.click('.el-menu-item:has-text("学习仪表盘")')
    await page.waitForLoadState('networkidle')
    await sleep(LONG)

    // 展示统计卡片
    await sleep(LONG)

    // 滚动查看图表
    await smoothScroll(page, '.app-main', 400, 2000)
    await sleep(LONG)  // 展示热力图

    await smoothScroll(page, '.app-main', 300, 1500)
    await sleep(LONG)  // 展示雷达图

    // 滚回顶部
    await smoothScroll(page, '.app-main', -700, 1500)
    await sleep(PAUSE)

    // 导出报告
    const reportBtn = page.locator('button:has-text("导出学习报告")')
    if (await reportBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await reportBtn.click()
      await sleep(PAUSE)
    }

    // ==================================================================
    // 第7段：个人中心 + 头像上传
    // ==================================================================
    console.log('📍 第7段：个人中心')

    // 点击右上角头像
    await page.click('.user-info')
    await sleep(SHORT)

    // 点击个人中心
    await page.click('.el-dropdown-menu__item:has-text("个人中心")')
    await page.waitForLoadState('networkidle')
    await sleep(LONG)

    // 展示个人信息
    await sleep(LONG)

    // 鼠标移到头像上（展示悬浮效果）
    const avatarWrapper = page.locator('.avatar-wrapper')
    if (await avatarWrapper.isVisible({ timeout: 2000 }).catch(() => false)) {
      await avatarWrapper.hover()
      await sleep(LONG)  // 展示相机遮罩

      // 上传头像（如果有测试图片）
      const testAvatar = path.join(__dirname, 'test-avatar.jpg')
      if (fs.existsSync(testAvatar)) {
        const fileInput = page.locator('.avatar-wrapper input[type="file"]')
        await fileInput.setInputFiles(testAvatar)
        console.log('  📸 正在上传头像...')
        await sleep(LONG)
      } else {
        console.log('  ⚠️ 未找到 test-avatar.jpg，跳过头像上传')
        console.log('     请在 recording/ 目录下放置 test-avatar.jpg 文件')
      }
    }

    // ==================================================================
    // 第8段：验证头像 + 退出
    // ==================================================================
    console.log('📍 第8段：验证 + 退出')

    // 回到聊天页
    await page.click('.el-menu-item:has-text("智能问答")')
    await page.waitForLoadState('networkidle')
    await sleep(LONG)

    // 展示聊天消息中的头像
    await sleep(LONG)

    // 退出登录
    await page.click('.user-info')
    await sleep(SHORT)
    await page.click('.el-dropdown-menu__item:has-text("退出登录")')
    await sleep(LONG)

    console.log('✅ 录屏完成！')
  } catch (err) {
    console.error('❌ 录屏过程出错:', err.message)
  } finally {
    // 关闭上下文会自动保存视频
    await context.close()
    await browser.close()

    // 获取视频路径
    const videos = fs.readdirSync(OUTPUT_DIR).filter(f => f.endsWith('.webm'))
    if (videos.length > 0) {
      const latest = videos.sort().pop()
      console.log(`\n🎥 视频已保存至: ${path.join(OUTPUT_DIR, latest)}`)
      console.log('\n💡 转换为 MP4 格式:')
      console.log(`   ffmpeg -i "${path.join(OUTPUT_DIR, latest)}" -c:v libx264 -preset fast demo.mp4`)
    }
  }
}

main()