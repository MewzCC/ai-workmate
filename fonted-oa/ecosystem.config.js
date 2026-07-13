module.exports = {
  apps: [
    {
      name: 'fonted-oa',
      script: './server.js',
      cwd: __dirname,
      exec_mode: 'fork',
      instances: 1,
      max_restarts: 5,
      out_file: './pm2-out.log',
      error_file: './pm2-error.log',
      merge_logs: true,
      env: {
        NODE_ENV: 'production',
        HOSTNAME: '0.0.0.0',
        PORT: '3001',
      },
    },
  ],
};
