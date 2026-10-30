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
    showPassword: "显示密码",
    hidePassword: "隐藏密码",
    fill: "填入"
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
  home: {
    eyebrow: "TEMPLATE LIBRARY",
    title: "选一个模板，开始制作",
    description: "挑一个版式，上传照片，剩下的交给三步流水线。",
    placeholderTitle: "模板库正在准备",
    placeholderDescription: "模板库正在准备，请稍后刷新。",
    signIn: "登录后开始"
  },
  account: {
    title: "账号",
    signOut: "退出登录",
    roleUser: "演示用户",
    roleAdmin: "管理员",
    joinedLabel: "加入于",
    scope: "数据范围：仅你的任务与素材"
  },
  history: {
    eyebrow: "ARCHIVE",
    title: "我的作品",
    description: "所有制作记录，按创建时间倒序。进行中的任务会自动更新。",
    placeholderTitle: "还没有作品",
    placeholderDescription: "从模板库开始制作后，记录会出现在这里。"
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
