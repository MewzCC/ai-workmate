module.exports = {
  apps: [
    {
      name: 'fronted-main',
      // Vite 静态预览服务器（需先 npm run build 产出 dist/）
      script: './node_modules/vite/bin/vite.js',
      args: 'preview --port 3000 --host 0.0.0.0',
      cwd: __dirname,
      exec_mode: 'fork',
      instances: 1,
      max_restarts: 5,
      out_file: './pm2-out.log',
      error_file: './pm2-error.log',
      merge_logs: true,
      env: {
        NODE_ENV: 'production',
      },
    },
  ],
};
