const fs = require('node:fs');
const path = require('node:path');

const desktop = path.resolve(__dirname, '..');
const source = path.resolve(desktop, '../admin-web/dist');
const target = path.join(desktop, 'web');
if (!fs.existsSync(path.join(source, 'index.html'))) {
  throw new Error('请先构建 admin-web，再同步 PC 网页资源');
}
// Only generated files inside this desktop project may be replaced.
if (path.dirname(target) !== desktop || path.basename(target) !== 'web') {
  throw new Error('拒绝清理项目范围之外的目录');
}
fs.rmSync(target, { recursive: true, force: true });
function copyDirectory(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const input = path.join(from, entry.name);
    const output = path.join(to, entry.name);
    if (entry.isDirectory()) copyDirectory(input, output);
    else if (entry.isFile()) fs.copyFileSync(input, output);
    else throw new Error('网页构建中不允许链接或特殊文件');
  }
}
copyDirectory(source, target);
console.log('PC 网页资源已同步，旧构建文件已清除');
