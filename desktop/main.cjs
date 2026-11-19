const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const fs = require('fs'),
  path = require('path'),
  http = require('http'),
  { spawn } = require('child_process');
let win, server, backend, settings;
const configPath = () => path.join(app.getPath('userData'), 'connection.json');
function config() {
  try {
    return JSON.parse(fs.readFileSync(configPath(), 'utf8'));
  } catch {
    return { apiUrl: 'http://127.0.0.1:8080' };
  }
}
function validate(value) {
  const u = new URL(value);
  if (u.username || u.password || u.search || u.hash || u.pathname !== '/')
    throw Error('请填写不含路径和密码的服务器地址');
  if (
    u.protocol !== 'https:' &&
    !(u.protocol === 'http:' && ['127.0.0.1', 'localhost'].includes(u.hostname))
  )
    throw Error('正式服务器需要HTTPS地址');
  return u.origin;
}
async function health(url) {
  try {
    const r = await fetch(url + '/api/health', { signal: AbortSignal.timeout(2000) });
    return r.ok && (await r.json()).status === 'UP';
  } catch {
    return false;
  }
}
async function ensureBackend() {
  const cfg = config();
  if (await health(cfg.apiUrl)) return;
  if (cfg.apiUrl !== 'http://127.0.0.1:8080') return;
  let local;
  try {
    local = JSON.parse(fs.readFileSync(path.join(app.getPath('userData'), 'backend.json'), 'utf8'));
  } catch {
    return;
  }
  const cwd = path.join(app.getPath('userData'), 'backend');
  fs.mkdirSync(cwd, { recursive: true });
  const jar = app.isPackaged
    ? path.join(process.resourcesPath, 'backend/server.jar')
    : path.resolve(__dirname, '../backend/target/billiard-server-0.1.0.jar');
  const log = fs.openSync(path.join(cwd, 'server.log'), 'a');
  backend = spawn(local.javaPath || 'java', ['-jar', jar], {
    cwd,
    windowsHide: true,
    stdio: ['ignore', log, log],
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: 'mysql',
      DB_URL: local.dbUrl,
      DB_USER: local.dbUser,
      DB_PASSWORD: local.dbPassword,
      ADMIN_PASSWORD: local.adminPassword || 'Admin123!',
      DEMO_ENABLED: String(local.demoEnabled === true),
    },
  });
  backend.on('error', () => {});
  for (let n = 0; n < 30; n++) {
    if (await health(cfg.apiUrl)) return;
    await new Promise((r) => setTimeout(r, 500));
  }
}
function settingsWindow() {
  if (settings) {
    settings.focus();
    return;
  }
  settings = new BrowserWindow({
    width: 600,
    height: 430,
    parent: win,
    modal: true,
    resizable: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  settings.loadFile('settings.html');
  settings.on('closed', () => (settings = null));
}
function startServer() {
  return new Promise((resolve) => {
    server = http.createServer(async (req, res) => {
      try {
        if (req.headers.host !== `127.0.0.1:${server.address().port}`) {
          res.writeHead(403);
          return res.end();
        }
        const url = new URL(req.url, 'http://localhost');
        if (url.pathname.startsWith('/api/')) {
          const origin = req.headers.origin;
          if (origin && origin !== `http://127.0.0.1:${server.address().port}`) {
            res.writeHead(403);
            return res.end();
          }
          let chunks = [],
            size = 0;
          for await (const chunk of req) {
            size += chunk.length;
            if (size > 6291456) {
              res.writeHead(413);
              return res.end();
            }
            chunks.push(chunk);
          }
          const headers = { 'Content-Type': req.headers['content-type'] || 'application/json' };
          if (req.headers.authorization) headers.Authorization = req.headers.authorization;
          const r = await fetch(config().apiUrl + url.pathname + url.search, {
            method: req.method,
            headers,
            body: ['GET', 'HEAD'].includes(req.method) ? undefined : Buffer.concat(chunks),
            signal: AbortSignal.timeout(15000),
          });
          res.writeHead(r.status, {
            'Content-Type': r.headers.get('Content-Type') || 'application/json',
          });
          return res.end(Buffer.from(await r.arrayBuffer()));
        }
        if (url.pathname.startsWith('/assets/uploads/')) {
          const r = await fetch(config().apiUrl + url.pathname, {
            signal: AbortSignal.timeout(15000),
          });
          res.writeHead(r.status, { 'Content-Type': r.headers.get('content-type') || 'image/png' });
          return res.end(Buffer.from(await r.arrayBuffer()));
        }
        let name =
          decodeURIComponent(url.pathname) === '/'
            ? 'index.html'
            : decodeURIComponent(url.pathname).slice(1);
        const base = path.join(__dirname, 'web');
        const file = path.resolve(base, name);
        if (!file.startsWith(base + path.sep)) {
          res.writeHead(403);
          return res.end();
        }
        if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
          res.writeHead(404);
          return res.end();
        }
        const types = {
          '.html': 'text/html; charset=utf-8',
          '.js': 'text/javascript',
          '.css': 'text/css',
          '.png': 'image/png',
          '.svg': 'image/svg+xml',
        };
        res.writeHead(200, {
          'Content-Type': types[path.extname(file)] || 'application/octet-stream',
          'Content-Security-Policy':
            "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-src https://www.amap.com",
        });
        fs.createReadStream(file).pipe(res);
      } catch (e) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: '无法连接业务服务器，请从应用菜单检查服务器设置' }));
      }
    });
    server.listen(0, '127.0.0.1', resolve);
  });
}
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.focus();
    }
  });
  app
    .whenReady()
    .then(async () => {
      await ensureBackend();
      await startServer();
      win = new BrowserWindow({
        width: 1440,
        height: 960,
        minWidth: 1000,
        minHeight: 720,
        title: '永州圣泰台球俱乐部',
        backgroundColor: '#f4f6f3',
        webPreferences: {
          preload: path.join(__dirname, 'preload.cjs'),
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true,
        },
      });
      const origin = `http://127.0.0.1:${server.address().port}`;
      win.webContents.on('will-navigate', (event, url) => {
        if (!url.startsWith(origin + '/')) event.preventDefault();
      });
      win.webContents.setWindowOpenHandler(({ url }) => {
        const target = new URL(url);
        if (target.origin === 'https://uri.amap.com' && target.pathname === '/search')
          require('electron')
            .shell.openExternal(url)
            .catch(() => {});
        return { action: 'deny' };
      });
      win.webContents.on('page-title-updated', (event) => event.preventDefault());
      win.loadURL(origin);
      Menu.setApplicationMenu(
        Menu.buildFromTemplate([
          {
            label: '应用',
            submenu: [
              { label: '服务器设置', click: settingsWindow },
              { label: '重新加载', role: 'reload' },
              { type: 'separator' },
              { label: '退出', role: 'quit' },
            ],
          },
          {
            label: '视图',
            submenu: [
              { role: 'resetZoom' },
              { role: 'zoomIn' },
              { role: 'zoomOut' },
              { role: 'togglefullscreen' },
            ],
          },
          {
            label: '帮助',
            submenu: [
              {
                label: '关于',
                click: () =>
                  dialog.showMessageBox(win, {
                    message: '永州圣泰台球俱乐部 0.10',
                    detail:
                      'Windows前台客户端 + 微信小程序，共用Spring Boot服务与MySQL。当前为开发演示版本。',
                  }),
              },
            ],
          },
        ]),
      );
      if (!(await health(config().apiUrl))) settingsWindow();
    })
    .catch((e) => dialog.showErrorBox('启动失败', e.message));
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', () => {
    server?.close();
    if (backend) backend.kill();
  });
}
ipcMain.handle('get-connection', (e) => {
  if (e.sender !== settings?.webContents) throw Error('Invalid sender');
  return config().apiUrl;
});
ipcMain.handle('save-connection', async (e, value) => {
  if (e.sender !== settings?.webContents) throw Error('Invalid sender');
  const apiUrl = validate(value);
  if (!(await health(apiUrl))) throw Error('服务器无法访问或不是台球服务，请先启动后端');
  fs.mkdirSync(app.getPath('userData'), { recursive: true });
  fs.writeFileSync(configPath(), JSON.stringify({ apiUrl }));
  settings.close();
  win.reload();
  return true;
});

const skinPath = () => path.join(app.getPath('userData'), 'skin.json');
ipcMain.handle('get-desk-skin', (event) => {
  if (event.sender !== win?.webContents) throw Error('Invalid sender');
  try {
    return JSON.parse(fs.readFileSync(skinPath(), 'utf8'));
  } catch {
    return 0;
  }
});
ipcMain.handle('save-desk-skin', (event, index) => {
  if (event.sender !== win?.webContents || !Number.isInteger(index) || index < 0 || index > 5)
    throw Error('无效的皮肤设置');
  fs.mkdirSync(app.getPath('userData'), { recursive: true });
  fs.writeFileSync(skinPath(), JSON.stringify(index));
});
