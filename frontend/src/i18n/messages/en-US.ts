import type { Messages } from "./zh-CN";

export const enUS = {
  app: {
    name: "FrameLab",
    skipToContent: "Skip to main content"
  },
  nav: {
    label: "Primary navigation",
    templates: "Templates",
    myWork: "My work",
    login: "Sign in",
    openAccount: "Open account menu"
  },
  language: {
    label: "Switch language / 切换语言",
    zh: "中文",
    en: "English"
  },
  common: {
    close: "Close",
    cancel: "Cancel",
    retry: "Retry",
    loading: "Loading",
    back: "Back",
    showPassword: "Show password",
    hidePassword: "Hide password",
    fill: "Fill"
  },
  auth: {
    eyebrow: "SIGN IN",
    title: "Welcome back",
    description: "Sign in to upload photos, create tasks, and view your history.",
    email: "Email",
    emailPlaceholder: "you@example.com",
    password: "Password",
    passwordPlaceholder: "Enter your password",
    submit: "Sign in",
    submitting: "Signing in…",
    demoTitle: "Use a demo account",
    demoHint: "Fills the form only; it does not submit.",
    demoUser: "Standard user",
    demoIsolation: "Isolation demo",
    invalidEmail: "Enter a valid email address",
    passwordRequired: "Enter your password",
    invalidCredentials: "Incorrect email or password",
    rateLimited: "Too many attempts. Please try again later.",
    networkError: "Network unavailable. Check your connection and try again.",
    sessionExpired: "Your session expired. Please sign in again.",
    backToTemplates: "← Back to templates",
    coverLabel: "Template cover collage",
    coverHeadline: "Put your photos in a better layout",
    coverDescription: "A darkroom interface with an olive light. Three steps handle layout and rendering."
  },
  home: {
    eyebrow: "TEMPLATE LIBRARY",
    title: "Pick a template and start creating",
    description: "Choose a layout, upload your photos, and let the three-step pipeline do the rest.",
    placeholderTitle: "The template library is getting ready",
    placeholderDescription: "The template library is being prepared. Please check back shortly.",
    signIn: "Sign in to start"
  },
  account: {
    title: "Account",
    signOut: "Sign out",
    roleUser: "Demo user",
    roleAdmin: "Administrator",
    joinedLabel: "Joined",
    scope: "Data scope: only your tasks and photos"
  },
  history: {
    eyebrow: "ARCHIVE",
    title: "My work",
    description: "All creations, newest first. Active tasks update automatically.",
    placeholderTitle: "No creations yet",
    placeholderDescription: "Start from a template and your work will appear here."
  },
  errors: {
    generic: "Something went wrong. Please try again.",
    network: "Network unavailable. Check your connection and try again.",
    unauthorized: "You do not have permission to do that.",
    notFound: "This page does not exist or has moved.",
    reload: "Reload"
  },
  status: {
    QUEUED: "Queued",
    RESUMING: "Resuming",
    RUNNING: "Processing",
    RETRYING: "Retrying",
    SUCCEEDED: "Completed",
    FAILED: "Failed"
  },
  stage: {
    MATERIAL_CHECK: "Material check",
    GENERATE: "Generate",
    FINALIZE: "Finalize"
  }
} satisfies Messages;
