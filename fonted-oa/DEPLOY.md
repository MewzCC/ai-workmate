# fonted-oa deploy

Start with PM2:

```bash
bash start-pm2.sh
```

Auto deploy both frontend packages:

```bash
# Put fronted-main.zip, fonted-oa.zip and deploy-frontends-server.sh in the same server directory.
bash deploy-frontends-server.sh
```

Debug:

```bash
pm2 logs fonted-oa --lines 100
cat pm2-error.log
ss -lntp | grep 3001
curl -I http://127.0.0.1:3001/oa
```

This package is a Next.js standalone build. Do not run `npm install` on the server.
