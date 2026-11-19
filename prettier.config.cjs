module.exports = {
  printWidth: 100,
  tabWidth: 2,
  singleQuote: true,
  trailingComma: 'all',
  htmlWhitespaceSensitivity: 'css',
  embeddedLanguageFormatting: 'off',
  overrides: [
    { files: '*.wxss', options: { parser: 'css' } },
    { files: 'desktop/settings.html', options: { embeddedLanguageFormatting: 'auto' } },
  ],
};
