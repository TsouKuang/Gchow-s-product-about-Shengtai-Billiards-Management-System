const path = require('node:path');
const { baseParse } = require(
  path.resolve(__dirname, '../admin-web/node_modules/@vue/compiler-dom'),
);

const voidTags = new Set([
  'area',
  'base',
  'br',
  'col',
  'embed',
  'hr',
  'img',
  'input',
  'link',
  'meta',
  'param',
  'source',
  'track',
  'wbr',
]);

// Keep mixed text, currency labels and {{ expressions }} byte-for-byte intact.
// Generic HTML formatters can insert newlines into WXML string literals or labels.
function formatMarkup(source, printWidth = 100) {
  const ast = baseParse(source, { isVoidTag: (tag) => voidTags.has(tag) });
  function render(node, depth) {
    const indent = '  '.repeat(depth);
    if (node.type === 3) return indent + node.loc.source;
    if (node.type !== 1) return node.loc.source.trim() ? indent + node.loc.source : '';
    const attributes = node.props.map((property) => property.loc.source);
    const tag = '<' + node.tag;
    const ending = node.isSelfClosing || voidTags.has(node.tag) ? ' />' : '>';
    const opening =
      indent.length + tag.length + attributes.join(' ').length + ending.length + 1 <= printWidth
        ? indent + tag + (attributes.length ? ' ' + attributes.join(' ') : '') + ending
        : indent +
          tag +
          '\n' +
          attributes.map((attribute) => indent + '  ' + attribute).join('\n') +
          '\n' +
          indent +
          ending.trimStart();
    if (node.isSelfClosing || voidTags.has(node.tag)) return opening;
    const mixed = node.children.some(
      (child) =>
        child.type === 5 ||
        (child.type === 2 && (child.content.trim() || /^[ \t]+$/.test(child.loc.source))),
    );
    const close = '</' + node.tag + '>';
    if (mixed) {
      const raw = node.loc.source;
      let quote = '',
        end = 0;
      for (let i = 0; i < raw.length; i++) {
        const char = raw[i];
        if (quote) {
          if (char === quote) quote = '';
        } else if (char === '"' || char === "'") quote = char;
        else if (char === '>') {
          end = i + 1;
          break;
        }
      }
      return opening + raw.slice(end, raw.lastIndexOf(close)) + close;
    }
    const children = node.children.map((child) => render(child, depth + 1)).filter(Boolean);
    return children.length
      ? opening + '\n' + children.join('\n') + '\n' + indent + close
      : opening + close;
  }
  return (
    ast.children
      .map((node) => render(node, 0))
      .filter(Boolean)
      .join('\n') + '\n'
  );
}

module.exports = { formatMarkup };
