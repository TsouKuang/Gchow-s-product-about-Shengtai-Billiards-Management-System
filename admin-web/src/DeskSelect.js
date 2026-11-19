import {
  defineComponent,
  h,
  ref,
  computed,
  onMounted,
  onUnmounted,
  nextTick,
  Teleport,
} from 'vue/dist/vue.esm-bundler.js';
let serial = 0;
function text(nodes) {
  if (nodes == null) return '';
  if (typeof nodes === 'string' || typeof nodes === 'number') return String(nodes);
  if (Array.isArray(nodes)) return nodes.map(text).join('');
  return text(nodes.children);
}
function options(nodes, result = []) {
  for (const node of nodes || []) {
    if (node?.type === 'option')
      result.push({
        label: text(node.children),
        value: node.props?.value ?? text(node.children),
        disabled: !!node.props?.disabled,
      });
    else if (Array.isArray(node?.children)) options(node.children, result);
  }
  return result;
}
export default defineComponent({
  name: 'DeskSelect',
  inheritAttrs: false,
  props: ['modelValue', 'value', 'disabled', 'modelModifiers'],
  emits: ['update:modelValue', 'change'],
  setup(props, { slots, emit, attrs }) {
    const open = ref(false),
      query = ref(''),
      trigger = ref(),
      panel = ref(),
      geometry = ref({}),
      cursor = ref(0),
      id = 'desk-options-' + ++serial;
    const rows = computed(() => options(slots.default?.()));
    const filtered = computed(() =>
      rows.value.filter(
        (o) => !query.value || o.label.toLowerCase().includes(query.value.toLowerCase()),
      ),
    );
    const current = computed(() =>
      props.modelValue === undefined ? props.value : props.modelValue,
    );
    const chosen = computed(
      () => rows.value.find((o) => String(o.value) === String(current.value)) || rows.value[0],
    );
    function close() {
      open.value = false;
      query.value = '';
    }
    async function show() {
      if (props.disabled) return;
      if (open.value) {
        close();
        return;
      }
      query.value = '';
      const rect = trigger.value.getBoundingClientRect();
      const height = Math.min(350, rows.value.length * 43 + (rows.value.length > 8 ? 55 : 16));
      const above = innerHeight - rect.bottom < height + 12 && rect.top > height;
      geometry.value = {
        position: 'fixed',
        left: Math.min(rect.left, innerWidth - Math.max(190, rect.width) - 12) + 'px',
        width: Math.max(190, rect.width) + 'px',
        maxHeight: Math.min(350, above ? rect.top - 16 : innerHeight - rect.bottom - 16) + 'px',
        ...(above
          ? { bottom: innerHeight - rect.top + 7 + 'px' }
          : { top: rect.bottom + 7 + 'px' }),
      };
      cursor.value = Math.max(0, rows.value.indexOf(chosen.value));
      open.value = true;
      await nextTick();
      const selected = panel.value?.querySelector('[aria-selected="true"]');
      if (selected && panel.value)
        panel.value.scrollTop = Math.max(
          0,
          selected.offsetTop - panel.value.clientHeight / 2 + selected.clientHeight / 2,
        );
    }
    function choose(row) {
      if (row.disabled) return;
      emit('update:modelValue', row.value);
      emit('change', { target: { value: row.value } });
      close();
      nextTick(() => trigger.value?.focus());
    }
    function keyboard(event) {
      if (event.key === 'Escape') {
        close();
        return;
      }
      if (event.key === 'Tab') {
        close();
        return;
      }
      if (
        event.target?.tagName === 'INPUT' &&
        !['ArrowDown', 'ArrowUp', 'Enter'].includes(event.key)
      )
        return;
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) event.preventDefault();
      if (!open.value && ['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        show();
        return;
      }
      if (!open.value) return;
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        cursor.value = Math.max(
          0,
          Math.min(filtered.value.length - 1, cursor.value + (event.key === 'ArrowDown' ? 1 : -1)),
        );
        nextTick(() => {
          const option = panel.value?.querySelector('.focused');
          if (option && panel.value)
            panel.value.scrollTop = Math.max(
              0,
              option.offsetTop - panel.value.clientHeight / 2 + option.clientHeight / 2,
            );
        });
      }
      if (event.key === 'Enter' && filtered.value[cursor.value])
        choose(filtered.value[cursor.value]);
    }
    function outside(event) {
      if (
        open.value &&
        !trigger.value?.contains(event.target) &&
        !panel.value?.contains(event.target)
      )
        close();
    }
    function scroll(event) {
      if (open.value && !panel.value?.contains(event.target)) close();
    }
    onMounted(() => {
      document.addEventListener('pointerdown', outside);
      window.addEventListener('resize', close);
      window.addEventListener('scroll', scroll, true);
    });
    onUnmounted(() => {
      document.removeEventListener('pointerdown', outside);
      window.removeEventListener('resize', close);
      window.removeEventListener('scroll', scroll, true);
    });
    return () =>
      h('div', { class: ['desk-select', attrs.class] }, [
        h(
          'button',
          {
            ...attrs,
            class: ['desk-select-trigger', { expanded: open.value }],
            ref: trigger,
            type: 'button',
            role: 'combobox',
            'aria-haspopup': 'listbox',
            'aria-expanded': String(open.value),
            'aria-controls': id,
            disabled: !!props.disabled,
            onClick: show,
            onKeydown: keyboard,
          },
          [
            h('span', chosen.value?.label || '请选择'),
            h('i', { class: 'desk-select-chevron', 'aria-hidden': 'true' }),
          ],
        ),
        open.value
          ? h(
              Teleport,
              { to: 'body' },
              h(
                'div',
                {
                  ref: panel,
                  class: 'desk-select-popover',
                  style: geometry.value,
                  onKeydown: keyboard,
                },
                [
                  rows.value.length > 8
                    ? h('input', {
                        class: 'desk-select-search',
                        value: query.value,
                        placeholder: '搜索选项…',
                        'aria-label': '搜索下拉选项',
                        onInput: (e) => {
                          query.value = e.target.value;
                          cursor.value = 0;
                        },
                      })
                    : null,
                  h(
                    'div',
                    { id, role: 'listbox', class: 'desk-select-options' },
                    filtered.value.map((row, index) =>
                      h(
                        'button',
                        {
                          type: 'button',
                          role: 'option',
                          'aria-selected': String(String(row.value) === String(current.value)),
                          disabled: row.disabled,
                          class: [
                            {
                              selected: String(row.value) === String(current.value),
                              focused: index === cursor.value,
                            },
                          ],
                          onClick: () => choose(row),
                        },
                        [
                          h('span', row.label),
                          h(
                            'i',
                            { 'aria-hidden': 'true' },
                            String(row.value) === String(current.value) ? '✓' : '',
                          ),
                        ],
                      ),
                    ),
                  ),
                  !filtered.value.length
                    ? h('div', { class: 'desk-select-empty' }, '没有匹配选项')
                    : null,
                ],
              ),
            )
          : null,
      ]);
  },
});
