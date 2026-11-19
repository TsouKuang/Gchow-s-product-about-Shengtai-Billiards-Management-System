const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const mini = path.join(root, 'miniprogram');
const compilerDirectory =
  process.env.WECHAT_WCC_DIR ||
  path.join(
    process.env['ProgramFiles(x86)'] || 'C:/Program Files (x86)',
    'Tencent/微信web开发者工具/resources/app.asar.unpacked/node_modules/wcc-exec',
  );

function files(directory, extension) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? files(file, extension) : file.endsWith(extension) ? [file] : [];
  });
}

for (const [compiler, extension] of [
  ['wcc.exe', '.wxml'],
  ['wcsc.exe', '.wxss'],
]) {
  const executable = path.join(compilerDirectory, compiler);
  if (!fs.existsSync(executable)) {
    throw new Error('找不到微信编译器，请安装微信开发者工具，或设置 WECHAT_WCC_DIR');
  }
  const inputs = files(path.join(mini, 'pages'), extension);
  if (extension === '.wxss') inputs.unshift(path.join(mini, 'app.wxss'));
  const result = spawnSync(
    executable,
    inputs.map((file) => path.relative(mini, file).split(path.sep).join('/')),
    {
      cwd: mini,
      windowsHide: true,
      encoding: 'utf8',
      maxBuffer: 16 * 1024 * 1024,
    },
  );
  if (result.error || result.status !== 0) throw result.error || new Error(result.stderr);
  console.log(`${inputs.length} 个 ${extension} 文件及引用编译通过`);
}
