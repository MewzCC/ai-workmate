/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_OA_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
