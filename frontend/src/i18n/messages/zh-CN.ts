export const zhCN = {
  app: {
    name: "FrameLab",
    skipToContent: "跳到主要内容"
  },
  nav: {
    label: "主导航",
    templates: "模板库",
    myWork: "我的作品",
    login: "登录",
    openAccount: "打开账号菜单"
  },
  language: {
    label: "切换语言 / Switch language",
    zh: "中文",
    en: "English"
  },
  common: {
    close: "关闭",
    cancel: "取消",
    retry: "重试",
    loading: "加载中",
    back: "返回",
    refresh: "刷新",
    remove: "移除",
    replace: "替换",
    clearFilters: "清除筛选",
    showPassword: "显示密码",
    hidePassword: "隐藏密码",
    fill: "填入"
  },
  time: {
    today: "今天",
    yesterday: "昨天"
  },
  auth: {
    eyebrow: "SIGN IN",
    title: "欢迎回来",
    description: "登录后上传素材、创建任务并查看个人记录。",
    email: "邮箱",
    emailPlaceholder: "you@example.com",
    password: "密码",
    passwordPlaceholder: "输入密码",
    submit: "登录",
    submitting: "正在登录…",
    demoTitle: "使用演示账号",
    demoHint: "只填充表单，不会自动提交。",
    demoUser: "普通用户",
    demoIsolation: "隔离演示",
    invalidEmail: "请输入有效的邮箱地址",
    passwordRequired: "请输入密码",
    invalidCredentials: "邮箱或密码不正确",
    rateLimited: "尝试次数过多，请稍后再试",
    networkError: "网络连接失败，请检查后重试",
    sessionExpired: "登录已过期，请重新登录",
    backToTemplates: "← 返回模板库",
    coverLabel: "模板封面拼贴",
    coverHeadline: "把照片放进更好的版式里",
    coverDescription: "深色暗房，橄榄微光。三步流水线替你完成排版与出图。"
  },
  templates: {
    eyebrow: "TEMPLATE LIBRARY / 01",
    title: "选一个模板，开始制作",
    description: "挑一个版式，上传照片，剩下的交给三步流水线。",
    total: "{count} 个模板",
    totalOne: "{count} 个模板",
    all: "全部",
    categoriesLabel: "模板分类",
    images: "{count} 张素材",
    imagesOne: "{count} 张素材",
    coverAlt: "{title} 模板封面",
    emptyTitle: "这个分类还没有模板",
    emptyDescription: "换一个分类，或查看全部模板。",
    viewAll: "查看全部",
    errorTitle: "模板暂时加载失败",
    errorDescription: "请检查网络后重新加载。",
    loading: "正在加载模板",
    arrow: "打开模板",
    badgeImage: "图片",
    badgeVideo: "视频",
    videoDuration: "{seconds} 秒",
    effectsLabel: "模板效果",
    outputVideo: "视频 · {seconds} 秒 · {width}×{height}",
    outputImage: "图片 · {width}×{height}",
    musicIncluded: "含背景音乐",
    effects: {
      travel: "旅行",
      warm: "暖调",
      film: "胶片",
      clear: "清透",
      cool: "冷调",
      festival: "节日",
      bright: "明亮",
      portrait: "人像",
      soft: "柔光",
      clean: "洁净",
      rich: "浓郁",
      cinematic: "电影感",
      golden: "黄金时刻"
    }
  },
  account: {
    title: "账号",
    signOut: "退出登录",
    signOutAll: "退出所有设备",
    roleUser: "演示用户",
    roleAdmin: "管理员",
    joinedLabel: "加入于",
    scope: "数据范围：仅你的任务与素材",
    languageLabel: "语言 / Language",
    languageHint: "界面、模板与任务消息将切换语言。",
    demoNote: "演示环境账号，数据保存在当前部署的 MySQL 中。"
  },
  history: {
    eyebrow: "ARCHIVE",
    title: "我的作品",
    description: "所有制作记录，按创建时间倒序。进行中的任务会自动更新。",
    total: "{count} 个任务",
    totalOne: "{count} 个任务",
    filtersLabel: "按状态筛选",
    filters: {
      all: "全部",
      active: "进行中",
      succeeded: "已完成",
      failed: "失败"
    },
    emptyTitle: "还没有作品",
    emptyDescription: "从模板库开始制作后，记录会出现在这里。",
    emptyFilteredTitle: "这个筛选下没有任务",
    emptyFilteredDescription: "清除筛选，查看全部制作记录。",
    errorTitle: "历史记录加载失败",
    errorDescription: "请检查网络后重试。",
    stale: "任务状态暂时无法更新，已保留最后一次结果",
    prev: "上一页",
    next: "下一页",
    page: "第 {page} 页",
    pagination: "任务分页",
    goTemplates: "去模板库"
  },
  tasks: {
    queuedHint: "等待工作线程",
    resumingHint: "服务重启，将从断点继续",
    runningHint: "当前阶段",
    retryWait: "等待重试",
    retryEta: "约 {seconds} 秒后",
    retryAttempts: "已尝试 {current}/{max} 次",
    nextRetryAt: "下次重试 {time}",
    attempt: "第 {n} 次尝试",
    attemptCount: "尝试 {count} 次",
    failureTitle: "任务未能完成",
    completedAt: "已完成 · {time}",
    createdAt: "创建于 {time}",
    viewDetails: "查看详情",
    viewResult: "查看结果",
    detailTitle: "任务详情",
    steps: "步骤",
    materials: "素材",
    attempts: "尝试历史",
    noAttempts: "暂无尝试记录",
    taskCode: "任务",
    loadError: "任务暂时加载失败",
    retry: "重新加载",
    resultTitle: "制作结果",
    resultAlt: "{title} 制作结果",
    resultMeta: "{width} × {height} · {size}",
    videoMeta: "{width} × {height} · {duration} · {size}",
    durationSeconds: "{seconds} 秒",
    videoError: "视频暂时无法播放",
    videoRetry: "重新加载视频",
    playVideo: "播放视频",
    imageLoading: "正在加载图片",
    imageError: "图片加载失败",
    imageRetry: "重新加载图片",
    statusLabel: "状态",
    stageLabel: "阶段",
    shortCode: "任务短码",
    simulated: "模拟",
    materialAlt: "{title} 素材 {index}",
    stepStatus: {
      PENDING: "待开始",
      RUNNING: "进行中",
      SUCCEEDED: "成功",
      FAILED: "失败",
      SKIPPED: "已跳过"
    },
    attemptStatus: {
      RUNNING: "进行中",
      SUCCEEDED: "成功",
      FAILED: "失败",
      INTERRUPTED: "已中断"
    }
  },
  studio: {
    back: "← 模板库",
    requirementsPhotos: "{count} 张图片",
    requirementsMime: "JPG / PNG",
    requirementsSize: "每张 ≤ {size}",
    uploadTitle: "上传素材",
    uploadCount: "{filled} / {total}",
    slotLabel: "第 {index} 张素材，{state}",
    slotEmpty: "点击或拖入图片",
    slotHint: "JPG/PNG · ≤{size}",
    slotLogin: "登录后上传",
    slotUploading: "上传中",
    slotUploaded: "已上传",
    slotError: "上传失败，请重试",
    slotStateEmpty: "未选择",
    slotStateError: "上传失败",
    replaceSlot: "替换第 {index} 张图片",
    removeSlot: "移除第 {index} 张图片",
    retrySlot: "重试上传第 {index} 张图片",
    chooseFile: "选择第 {index} 张图片",
    missing: "还差 {count} 张图片",
    missingOne: "还差 {count} 张图片",
    start: "开始制作",
    createVideo: "生成视频",
    videoOutput: "{seconds} 秒视频 · {width}×{height}",
    videoOutputMusic: "{seconds} 秒视频 · {width}×{height} · 含背景音乐",
    loginToStart: "登录后开始制作",
    submitting: "正在提交…",
    retrySubmit: "重试提交",
    loginHint: "登录后才能上传素材并创建任务。",
    errorType: "第 {index} 张图片格式不支持，请选择 {types}。",
    errorSize: "第 {index} 张图片超过 {size}，请压缩后重试。",
    errorUpload: "上传失败，请重试。",
    submitError: "提交失败，请稍后重试。",
    toastCreated: "任务已创建，正在排队",
    leaveConfirm: "离开后本次选择会丢失。",
    historyTitle: "制作记录",
    historyCount: "{count} 个任务",
    historyCountOne: "{count} 个任务",
    historyEmptyTitle: "还没有制作记录",
    historyEmptyDescription: "上传素材后开始第一个任务。",
    historyError: "制作记录暂时加载失败",
    refresh: "刷新制作记录",
    anonymousHistory: "登录后查看制作记录",
    anonymousHistoryDescription: "你的任务与素材仅对当前账号可见。",
    templateMissing: "模板不存在或已下架",
    templateError: "模板暂时加载失败",
    backToTemplates: "返回模板库"
  },
  errors: {
    generic: "出现了一点问题，请稍后重试。",
    network: "网络连接失败，请检查后重试。",
    unauthorized: "没有权限执行此操作。",
    notFound: "页面不存在或已被移动。",
    reload: "重新加载"
  },
  status: {
    QUEUED: "排队中",
    RESUMING: "正在恢复",
    RUNNING: "处理中",
    RETRYING: "等待重试",
    SUCCEEDED: "已完成",
    FAILED: "失败"
  },
  stage: {
    MATERIAL_CHECK: "素材检查",
    GENERATE: "模拟生成",
    FINALIZE: "结果整理"
  }
} as const;

type DeepStringify<T> = {
  [K in keyof T]: T[K] extends string ? string : DeepStringify<T[K]>;
};

export type Messages = DeepStringify<typeof zhCN>;

type Join<K, P extends string> = K extends string
  ? P extends ""
    ? K
    : `${P}.${K}`
  : never;

export type MessageKey<T = typeof zhCN, P extends string = ""> = {
  [K in keyof T & string]: T[K] extends string
    ? Join<K, P>
    : MessageKey<T[K], Join<K, P>>;
}[keyof T & string];
