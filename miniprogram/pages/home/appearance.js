// Shared colors for pages, native navigation and overlays, including the wallet.
const palettes = {
  club: {
    bg: '#f3f7f4',
    surface: '#ffffff',
    raised: '#eaf1ec',
    line: '#d7e2db',
    text: '#263c31',
    muted: '#607568',
    accent: '#30644c',
    onAccent: '#ffffff',
    top: '#193e30',
    heroFrom: '#1d4334',
    heroTo: '#315c48',
  },
  bronze: {
    bg: '#f7f2ec',
    surface: '#fffdf9',
    raised: '#eee4d9',
    line: '#ddcfc1',
    text: '#493b31',
    muted: '#786554',
    accent: '#865b3d',
    onAccent: '#ffffff',
    top: '#594031',
    heroFrom: '#594031',
    heroTo: '#7c5942',
  },
  silver: {
    bg: '#f2f5f8',
    surface: '#fcfdff',
    raised: '#e5ecf2',
    line: '#cedae4',
    text: '#2c4051',
    muted: '#596e80',
    accent: '#476b88',
    onAccent: '#ffffff',
    top: '#344c61',
    heroFrom: '#344c61',
    heroTo: '#4a6980',
  },
  gold: {
    bg: '#f8f5ec',
    surface: '#fffdf7',
    raised: '#eee7d5',
    line: '#ded3b7',
    text: '#493d27',
    muted: '#796b4d',
    accent: '#806127',
    onAccent: '#ffffff',
    top: '#5b4829',
    heroFrom: '#5b4829',
    heroTo: '#79633a',
  },
  diamond: {
    bg: '#f1f5fa',
    surface: '#fcfdff',
    raised: '#e3ebf5',
    line: '#cfdaea',
    text: '#2c405c',
    muted: '#5b718e',
    accent: '#3e6597',
    onAccent: '#ffffff',
    top: '#2b4569',
    heroFrom: '#2b4569',
    heroTo: '#45658d',
  },
  'black-gold': {
    bg: '#f4f2ed',
    surface: '#fdfbf6',
    raised: '#e9e4d8',
    line: '#d6cebc',
    text: '#38352e',
    muted: '#706955',
    accent: '#73603f',
    onAccent: '#ffffff',
    top: '#33312b',
    heroFrom: '#33312b',
    heroTo: '#5a4f3b',
  },
  tech: {
    dark: true,
    bg: '#13212b',
    surface: '#1d2f3b',
    raised: '#263c49',
    line: '#354e5c',
    text: '#e0edf2',
    muted: '#9bb5c2',
    accent: '#9acbd0',
    onAccent: '#133138',
    top: '#13212b',
    radius: 24,
  },
  retro: {
    bg: '#eee7d9',
    surface: '#faf5e9',
    raised: '#e8decc',
    line: '#d3c2a4',
    text: '#44392c',
    muted: '#73634d',
    accent: '#805d38',
    onAccent: '#fffaf0',
    top: '#514131',
    heroFrom: '#514131',
    heroTo: '#6e573e',
    radius: 16,
  },
  minimal: {
    bg: '#f3f5f6',
    surface: '#ffffff',
    raised: '#e9eef0',
    line: '#dce3e6',
    text: '#283b43',
    muted: '#5d717b',
    accent: '#365560',
    onAccent: '#ffffff',
    top: '#30464e',
    heroFrom: '#30464e',
    heroTo: '#4d656d',
    radius: 26,
  },
  neon: {
    dark: true,
    bg: '#24212f',
    surface: '#302c3e',
    raised: '#3b354c',
    line: '#51495f',
    text: '#eee8f4',
    muted: '#b6a8c8',
    accent: '#c5b1df',
    onAccent: '#30213e',
    top: '#24212f',
    radius: 30,
  },
  ice: {
    bg: '#eff5f9',
    surface: '#fbfdff',
    raised: '#e3edf5',
    line: '#c9dce9',
    text: '#2c4a5e',
    muted: '#566f81',
    accent: '#3b7897',
    onAccent: '#ffffff',
    top: '#304d61',
    heroFrom: '#304d61',
    heroTo: '#4c7188',
    radius: 30,
  },
  forest: {
    bg: '#eff2e9',
    surface: '#fafbf6',
    raised: '#e5ebde',
    line: '#cfdac6',
    text: '#334a39',
    muted: '#5b705a',
    accent: '#496b4e',
    onAccent: '#ffffff',
    top: '#2e4837',
    heroFrom: '#2e4837',
    heroTo: '#4b674b',
    radius: 30,
  },
  ink: {
    bg: '#eeece3',
    surface: '#faf7ef',
    raised: '#e6e1d4',
    line: '#d3ccba',
    text: '#393b32',
    muted: '#686653',
    accent: '#924e3c',
    onAccent: '#fffaf0',
    top: '#373b32',
    heroFrom: '#373b32',
    heroTo: '#585a4a',
    radius: 12,
  },
  obsidian: {
    dark: true,
    bg: '#17191c',
    surface: '#23262b',
    raised: '#303238',
    line: '#45433b',
    text: '#ede6d7',
    muted: '#b5aa95',
    accent: '#d4bf95',
    onAccent: '#30291d',
    top: '#17191c',
    radius: 26,
  },
  film: {
    bg: '#f4e8dc',
    surface: '#fff6eb',
    raised: '#eedecb',
    line: '#dcbfa5',
    text: '#553e31',
    muted: '#7a5b48',
    accent: '#a15b3a',
    onAccent: '#ffffff',
    top: '#6b4c39',
    heroFrom: '#6b4c39',
    heroTo: '#876247',
    radius: 34,
  },
};

function blend(a, b, weight) {
  const rgb = (hex) =>
    hex
      .slice(1)
      .match(/../g)
      .map((x) => parseInt(x, 16));
  const x = rgb(a),
    y = rgb(b);
  return (
    '#' +
    x
      .map((v, i) =>
        Math.round(v * weight + y[i] * (1 - weight))
          .toString(16)
          .padStart(2, '0'),
      )
      .join('')
  );
}

function resolveAppearance(skinKey = 'club', exclusiveKey = 'none') {
  const key =
    exclusiveKey !== 'none' && palettes[exclusiveKey]
      ? exclusiveKey
      : palettes[skinKey]
        ? skinKey
        : 'club';
  const p = palettes[key];
  const heroFrom = p.heroFrom || p.surface,
    heroTo = p.heroTo || p.raised;
  const heroText = p.dark ? p.text : '#f8faf7';
  const heroAccent = p.dark ? p.accent : blend(p.accent, '#ffffff', 0.22);
  const values = {
    bg: p.bg,
    surface: p.surface,
    raised: p.raised,
    line: p.line,
    text: p.text,
    muted: p.muted,
    font: ['retro', 'ink'].includes(key)
      ? '"Songti SC","STSong","SimSun",serif'
      : '-apple-system,"PingFang SC","Microsoft YaHei",sans-serif',
    accent: p.accent,
    'on-accent': p.onAccent,
    soft: blend(p.accent, p.surface, p.dark ? 0.15 : 0.09),
    'hero-from': heroFrom,
    'hero-to': heroTo,
    'hero-text': heroText,
    'hero-muted': blend(heroText, heroTo, p.dark ? 0.78 : 0.94),
    'hero-accent': heroAccent,
    'hero-track': blend(heroText, heroTo, 0.22),
    overlay: p.dark ? '#050a10b8' : '#13231d88',
    shadow: p.dark ? '0 14rpx 40rpx #00000018' : '0 10rpx 32rpx #263c3109',
    radius: (p.radius || 28) + 'rpx',
    success: p.dark ? '#a2d9b9' : '#36704c',
    'success-soft': p.dark ? '#263e36' : '#e7f2ea',
    danger: p.dark ? '#efb5a6' : '#a34d38',
    'danger-soft': p.dark ? '#452f31' : '#faeee8',
  };
  const aliases = {
    'theme-top': heroFrom,
    'theme-mid': heroTo,
    'theme-paper': p.bg,
    'theme-accent': p.accent,
    'theme-soft': values.soft,
    'skin-ink': heroText,
    'content-ink': p.text,
    'content-muted': p.muted,
    text: p.text,
  };
  const style = Object.entries(values)
    .map(([k, v]) => '--ui-' + k + ':' + v)
    .concat(Object.entries(aliases).map(([k, v]) => '--' + k + ':' + v))
    .join(';');
  return {
    key,
    palette: values,
    style,
    navigation: p.top,
    background: p.bg,
    accent: p.accent,
    heroAccent,
    heroTrack: values['hero-track'],
  };
}

module.exports = { palettes, resolveAppearance };
