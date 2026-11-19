const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const prettier = require(path.join(root, 'admin-web/node_modules/prettier'));
const config = require(path.join(root, 'prettier.config.cjs'));
const { formatMarkup } = require('./format-markup.cjs');

function sourceFiles(directory) {
  return fs.readdirSync(path.join(root, directory), { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory()
      ? sourceFiles(file)
      : /\.(?:js|cjs|css|wxss|wxml|html|json)$/.test(entry.name)
        ? [file]
        : [];
  });
}

async function main() {
  const files = [
    ...['admin-web/src', 'miniprogram/pages', 'desktop/scripts', 'scripts', 'tests'].flatMap(
      sourceFiles,
    ),
    'prettier.config.cjs',
    'admin-web/index.html',
    'admin-web/vite.config.js',
    'admin-web/package.json',
    'miniprogram/api.js',
    'miniprogram/app.js',
    'miniprogram/app.json',
    'miniprogram/app.wxss',
    'miniprogram/sitemap.json',
    'desktop/main.cjs',
    'desktop/preload.cjs',
    'desktop/settings.html',
    'desktop/package.json',
  ];
  const write = process.argv.includes('--write');
  const changed = [];
  for (const file of files) {
    const absolute = path.join(root, file);
    const before = fs.readFileSync(absolute, 'utf8');
    const options = await prettier.resolveConfig(absolute);
    const markup = file.endsWith('.wxml') || file === path.join('admin-web', 'src', 'App.html');
    const after = markup
      ? formatMarkup(before, config.printWidth)
      : await prettier.format(before, { ...config, ...options, filepath: absolute });
    if (before === after) continue;
    changed.push(file);
    if (write) fs.writeFileSync(absolute, after);
  }
  if (!write && changed.length) {
    console.error('以下源码需要格式化：\n' + changed.join('\n'));
    process.exitCode = 1;
  } else {
    console.log(`${files.length} 个源码文件格式${write ? '整理完成' : '检查通过'}`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
