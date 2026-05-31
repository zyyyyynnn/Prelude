/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'markdown-it' {
  export default class MarkdownIt {
    constructor(options?: Record<string, unknown>)
    render(content: string): string
  }
}
