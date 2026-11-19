const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const { parse } = require(path.join(root, 'admin-web/node_modules/@babel/parser'));
const { compile } = require(path.join(root, 'admin-web/node_modules/@vue/compiler-dom'));

function files(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? files(file) : /\.(?:js|cjs)$/.test(file) ? [file] : [];
  });
}
function walk(node, visit) {
  if (!node || typeof node !== 'object') return;
  visit(node);
  for (const [key, value] of Object.entries(node)) {
    if (['loc', 'extra', 'comments', 'tokens'].includes(key)) continue;
    if (Array.isArray(value)) value.forEach((child) => walk(child, visit));
    else if (value && typeof value === 'object') walk(value, visit);
  }
}
const sources = [
  'admin-web/src',
  'miniprogram/pages',
  'desktop/scripts',
  'scripts',
  'tests',
].flatMap((dir) => files(path.join(root, dir)));
sources.push(
  ...[
    'miniprogram/app.js',
    'miniprogram/api.js',
    'admin-web/vite.config.js',
    'desktop/main.cjs',
    'desktop/preload.cjs',
  ].map((file) => path.join(root, file)),
);
const failures = [];
const handlers = new Map();
function registerHandlers(properties, file) {
  for (const property of properties) {
    if (property.type !== 'ObjectMethod') continue;
    const name = property.key.name;
    if (handlers.has(name)) failures.push(`小程序重复方法 ${name}`);
    handlers.set(name, file);
  }
}
for (const file of sources) {
  try {
    const ast = parse(fs.readFileSync(file, 'utf8'), { sourceType: 'unambiguous' });
    if (file === path.join(root, 'admin-web/src/App.js')) {
      const source = fs.readFileSync(file, 'utf8');
      const app = ast.program.body.find((node) => node.type === 'ExportDefaultDeclaration');
      const setup = app.declaration.properties.find((property) => property.key.name === 'setup');
      const returned = setup.body.body.find((node) => node.type === 'ReturnStatement');
      const body = source.slice(0, returned.start);
      const template = fs.readFileSync(path.join(root, 'admin-web/src/App.html'), 'utf8');
      compile(template);
      for (const property of returned.argument.properties) {
        const name = property.key.name;
        const pattern = new RegExp('\\b' + name + '\\b', 'g');
        if ((body.match(pattern) || []).length <= 1 && !template.match(pattern)) {
          failures.push(`admin-web/src/App.js: setup 导出了未使用的 ${name}`);
        }
      }
    }
    walk(ast, (node) => {
      if (file.startsWith(path.join(root, 'miniprogram/pages'))) {
        if (node.type === 'CallExpression' && node.callee.name === 'Page') {
          registerHandlers(node.arguments[0].properties, file);
        } else if (
          node.type === 'VariableDeclarator' &&
          node.id.name === 'methods' &&
          node.init?.type === 'ObjectExpression'
        ) {
          registerHandlers(node.init.properties, file);
        }
      }
      if (node.type !== 'ObjectExpression') return;
      const keys = new Set();
      for (const property of node.properties) {
        if (property.type === 'SpreadElement' || property.computed) continue;
        const key = property.key?.name ?? property.key?.value;
        if (keys.has(key))
          failures.push(`${path.relative(root, file)}:${property.loc.start.line} 重复属性 ${key}`);
        keys.add(key);
      }
    });
  } catch (error) {
    failures.push(`${path.relative(root, file)}: ${error.message}`);
  }
}
function checkTemplates(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      checkTemplates(file);
      continue;
    }
    if (!file.endsWith('.wxml')) continue;
    const template = fs.readFileSync(file, 'utf8');
    for (const [, handler] of template.matchAll(/(?:bind|catch)(?::)?[\w-]+="([\w]+)"/g)) {
      if (!handlers.has(handler))
        failures.push(`${path.relative(root, file)}: 未定义的事件方法 ${handler}`);
    }
    for (const [, include] of template.matchAll(/<include\s+src="([^"]+)"/g)) {
      const target = path.resolve(path.dirname(file), include);
      if (!target.startsWith(path.join(root, 'miniprogram') + path.sep) || !fs.existsSync(target)) {
        failures.push(`${path.relative(root, file)}: 无效的模板引用 ${include}`);
      }
    }
  }
}
checkTemplates(path.join(root, 'miniprogram/pages'));
if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`${sources.length} 个 JavaScript 源文件、桌面模板及小程序事件引用检查通过`);
}
