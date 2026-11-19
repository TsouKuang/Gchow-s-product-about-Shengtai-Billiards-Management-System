import { ref, computed, onMounted, onUnmounted } from 'vue/dist/vue.esm-bundler.js';
import DeskSelect from './DeskSelect.js';
import template from './App.html?raw';

export default {
  components: { DeskSelect },
  setup() {
    const token = ref(sessionStorage.getItem('token') || ''),
      password = ref(''),
      username = ref('admin'),
      tab = ref('球桌工作台'),
      tables = ref([]),
      packages = ref([]),
      orders = ref([]),
      error = ref(''),
      busy = ref(false),
      selected = ref(null),
      packageId = ref(''),
      method = ref('CASH'),
      newPack = ref({ name: '', minutes: 60, price: 38 }),
      now = ref(Date.now());
    let timer;
    const selectedPackages = ref([]),
      models = ref([]),
      prices = ref([]),
      services = ref([]),
      serviceOrders = ref([]);
    const grades = ref([]),
      bill = ref(null),
      customerId = ref('');
    const catalogKind = ref('CUE'),
      catalogSearch = ref(''),
      editItemId = ref(null),
      stats = ref(null);
    const localDate = (d) =>
      d.getFullYear() +
      '-' +
      String(d.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(d.getDate()).padStart(2, '0');
    const statsTo = ref(localDate(new Date())),
      statsFrom = ref(localDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const catalogFilter = ref('');
    const catalogFilters = computed(() =>
      [
        ...new Set(
          services.value
            .filter((i) => i.kind === catalogKind.value)
            .map((i) => (catalogKind.value === 'CUE' ? i.brand : i.grade_title)),
        ),
      ].filter(Boolean),
    );
    const catalogRows = computed(() =>
      services.value.filter(
        (i) =>
          i.kind === catalogKind.value &&
          (!catalogFilter.value ||
            (catalogKind.value === 'CUE' ? i.brand : i.grade_title) === catalogFilter.value) &&
          (!catalogSearch.value ||
            ((i.service_code || '') + ' ' + i.brand + ' ' + i.name + ' ' + (i.grade_title || ''))
              .toLowerCase()
              .includes(catalogSearch.value.toLowerCase())),
      ),
    );
    const statsRows = computed(() => stats.value?.receipts || []);
    const receiptTotal = (method) =>
      statsRows.value
        .filter((r) => r.payment_method === method)
        .reduce((n, r) => n + Number(r.amount), 0);
    const kindTotal = (kind, method) =>
      statsRows.value
        .filter((r) => r.kind === kind && r.payment_method === method)
        .reduce((n, r) => n + Number(r.amount), 0);
    const dailyStats = computed(() => {
      const days = {};
      for (const r of statsRows.value) {
        const key = String(r.receipt_day).slice(0, 10);
        if (!days[key]) days[key] = { day: key, CASH: 0, MOCK: 0, WALLET: 0 };
        if (['CASH', 'MOCK', 'WALLET'].includes(r.payment_method))
          days[key][r.payment_method] += Number(r.amount);
      }
      return Object.values(days).sort((a, b) => b.day.localeCompare(a.day));
    });
    const editDraft = ref(null),
      saved = ref(''),
      statsPeriod = ref('month');
    function beginEdit(item) {
      saved.value = '';
      editDraft.value = {
        ...item,
        rate:
          item.kind === 'COACH'
            ? item.personal_rate == null
              ? ''
              : Number(money(Number(item.personal_rate) * 60))
            : item.rate,
      };
      editItemId.value = item.id;
    }
    function setPeriod(period) {
      statsPeriod.value = period;
      const today = new Date(),
        year = today.getFullYear(),
        month = today.getMonth();
      let start, end;
      if (period === 'day') {
        start = new Date(year, month, today.getDate());
        end = new Date(start);
      } else if (period === 'year') {
        start = new Date(year, 0, 1);
        end = new Date(year, 11, 31);
      } else if (period === 'month') {
        start = new Date(year, month, 1);
        end = new Date(year, month + 1, 0);
      } else {
        start = new Date(year, month, today.getDate() - ((today.getDay() + 6) % 7));
        end = new Date(start);
        end.setDate(start.getDate() + 6);
      }
      statsFrom.value = localDate(start);
      statsTo.value = localDate(end);
      loadStats();
    }

    const statsCoaches = computed(() =>
      (stats.value?.items || [])
        .filter((i) => i.kind === 'COACH')
        .sort((a, b) => Number(b.cash) + Number(b.wallet) - (Number(a.cash) + Number(a.wallet))),
    );
    const statsCues = computed(() =>
      (stats.value?.items || [])
        .filter((i) => i.kind === 'CUE')
        .sort((a, b) => Number(b.uses) - Number(a.uses)),
    );

    const todayIncome = ref(null),
      finance = ref(null),
      tableQuery = ref(''),
      tableState = ref(''),
      tableType = ref('');
    const visibleTables = computed(() =>
      tables.value.filter(
        (t) =>
          (!tableQuery.value ||
            t.table_no.toLowerCase().includes(tableQuery.value.trim().toLowerCase())) &&
          (!tableState.value || t.status === tableState.value) &&
          (!tableType.value || t.type === tableType.value),
      ),
    );
    const expenseCategories = {
      TIP: '皮头',
      PAYROLL: '人员工资',
      UTILITIES: '水电煤气费',
      CLEANING: '清洁保洁费',
      REPAIR: '清理维修费',
      RENT: '房租物业费',
      SUPPLIES: '耗材采购',
      MARKETING: '宣传推广',
    };
    const expenseEditing = ref(null),
      expenseMessage = ref(''),
      settleMethod = ref('CASH');
    const expense = ref({
      category: 'TIP',
      customCategory: '',
      date: localDate(new Date()),
      amount: '',
      note: '',
    });
    const expenseTotal = computed(() =>
      (finance.value?.expenses || []).reduce((sum, e) => sum + Number(e.amount), 0),
    );
    const expenseParts = computed(() => {
      const totals = new Map();
      for (const entry of finance.value?.expenses || []) {
        const name = expenseCategories[entry.category] || entry.category;
        totals.set(name, (totals.get(name) || 0) + Number(entry.amount));
      }
      return [...totals].map(([name, amount]) => ({ name, amount }));
    });
    const expenseColors = [
      'var(--desk-accent)',
      '#b98a50',
      '#7596a2',
      '#876b99',
      '#a76565',
      '#667a3f',
    ];
    const revenueParts = computed(() =>
      ['PLAY', 'COACH', 'CUE'].map(
        (k) => kindTotal(k, 'CASH') + kindTotal(k, 'MOCK') + kindTotal(k, 'WALLET'),
      ),
    );
    const revenueTotal = computed(
      () => receiptTotal('CASH') + receiptTotal('MOCK') + receiptTotal('WALLET'),
    );
    function ring(values, colors) {
      const sum = values.reduce((total, value) => total + value, 0);
      if (!sum) return '#e7e3d8';
      let start = 0;
      const segments = values.map((value, index) => {
        const end = start + (value / sum) * 100;
        const segment = colors[index] + ' ' + start + '% ' + end + '%';
        start = end;
        return segment;
      });
      return 'conic-gradient(' + segments.join(',') + ')';
    }
    function resetExpense() {
      expenseEditing.value = null;
      expense.value = {
        category: 'TIP',
        customCategory: '',
        date: localDate(new Date()),
        amount: '',
        note: '',
      };
    }
    function editExpense(entry) {
      expenseEditing.value = { id: entry.id, version: entry.version };
      expense.value = {
        category: expenseCategories[entry.category] ? entry.category : 'CUSTOM',
        customCategory: expenseCategories[entry.category] ? '' : entry.category,
        date: String(entry.expense_date).slice(0, 10),
        amount: Number(entry.amount),
        note: entry.note,
      };
      expenseMessage.value = '';
      expenseDialog.value = true;
    }
    async function refreshFinance() {
      finance.value = await api('/monthly-finance');
      if (storeBill.value) await openStoreBill();
    }
    async function saveExpense() {
      await act(async () => {
        const existing = expenseEditing.value;
        await api('/expenses' + (existing ? '/' + existing.id : ''), {
          method: existing ? 'PUT' : 'POST',
          body: JSON.stringify({
            ...expense.value,
            category:
              expense.value.category === 'CUSTOM'
                ? expense.value.customCategory
                : expense.value.category,
            version: existing?.version,
          }),
        });
        resetExpense();
        await refreshFinance();
        expenseMessage.value = existing ? '支出修改已保存' : '支出已登记';
      });
    }
    async function cancelExpense(entry) {
      if (
        !(await confirmDialog(
          '撤销这笔支出？\n' +
            (expenseCategories[entry.category] || entry.category) +
            ' · ¥' +
            money(entry.amount) +
            '\n撤销后从合计中扣除，变更记录可供核对。',
        ))
      )
        return;
      await act(async () => {
        await api('/expenses/' + entry.id + '/cancel', {
          method: 'POST',
          body: JSON.stringify({ version: entry.version }),
        });
        if (expenseEditing.value?.id === entry.id) resetExpense();
        await refreshFinance();
        expenseMessage.value = '支出已撤销';
      });
    }
    const prompt = ref(null);
    let promptResolve;
    function confirmDialog(content, title = '请确认操作') {
      return new Promise((resolve) => {
        promptResolve = resolve;
        prompt.value = {
          title,
          paragraphs: String(content)
            .split(/\n|(?<=。)/)
            .filter(Boolean),
        };
      });
    }
    function answerPrompt(accepted) {
      const resolve = promptResolve;
      promptResolve = null;
      prompt.value = null;
      resolve?.(accepted);
    }
    const packPresets = [
      { name: '热身半小时', minutes: 30, price: 20 },
      { name: '进阶90分钟', minutes: 90, price: 55 },
      { name: '好友四小时', minutes: 240, price: 128 },
      { name: '畅玩五小时', minutes: 300, price: 158 },
    ];
    const detailKind = ref('coach'),
      detailQuery = ref(''),
      detailPage = ref(1),
      usedOnly = ref(false);
    const detailRows = computed(() => {
      const source =
        detailKind.value === 'coach'
          ? statsCoaches.value
          : detailKind.value === 'cue'
            ? statsCues.value
            : dailyStats.value;
      const query = detailQuery.value.trim().toLowerCase();
      return source.filter(
        (i) =>
          [i.service_code, i.name, i.brand, i.day]
            .filter(Boolean)
            .join(' ')
            .toLowerCase()
            .includes(query) &&
          (!usedOnly.value ||
            Number(i.uses) > 0 ||
            Number(i.cash) > 0 ||
            Number(i.mock) > 0 ||
            Number(i.wallet) > 0),
      );
    });
    const detailPages = computed(() => Math.max(1, Math.ceil(detailRows.value.length / 12)));
    const detailCards = computed(() =>
      detailRows.value.slice(
        (Math.min(detailPage.value, detailPages.value) - 1) * 12,
        Math.min(detailPage.value, detailPages.value) * 12,
      ),
    );
    function switchDetail(kind) {
      detailKind.value = kind;
      detailPage.value = 1;
      detailQuery.value = '';
      usedOnly.value = false;
    }
    async function loadStats() {
      await act(async () => {
        const [report, month] = await Promise.all([
          api('/statistics?from=' + statsFrom.value + '&to=' + statsTo.value),
          api('/monthly-finance'),
        ]);
        stats.value = report;
        statsRefresh.value++;
        finance.value = month;
        detailPage.value = 1;
      });
    }
    const orderDates = ref({
      from: localDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)),
      to: localDate(new Date()),
    });
    const pendingOrdersOnly = ref(false);
    const pendingCueOrders = ref([]);
    const appliedOrderDates = ref({ ...orderDates.value }),
      datedOrders = ref(null);
    async function fetchDatedOrders(page = 1) {
      const { from, to } = appliedOrderDates.value;
      datedOrders.value = await api(
        '/admin/orders-by-date?' +
          new URLSearchParams({ from, to, page, pendingOnly: pendingOrdersOnly.value }),
      );
    }
    async function searchOrders(page = 1, apply = false) {
      await act(async () => {
        const previous = appliedOrderDates.value;
        if (apply) appliedOrderDates.value = { ...orderDates.value };
        try {
          await fetchDatedOrders(page);
        } catch (e) {
          appliedOrderDates.value = previous;
          throw e;
        }
      });
    }
    const orderPreset = ref(null);
    const statsRefresh = ref(0);
    function orderShortcut(days) {
      orderPreset.value = days;
      const today = new Date(),
        start = new Date(today);
      if (days === 0) start.setDate(1);
      else start.setDate(today.getDate() - days + 1);
      orderDates.value = { from: localDate(start), to: localDate(today) };
      searchOrders(1, true);
    }
    const pcSkins = [
      '翡翠流光',
      '暖铜暮色',
      '月银云境',
      '香槟鎏金',
      '深海蓝调',
      '曜石黑金',
      '雾紫星河',
      '松影青瓷',
    ];
    const pcSkin = ref(0);
    function setPcSkin(value) {
      const index = Number(value);
      if (!Number.isInteger(index) || !pcSkins[index]) return;
      pcSkin.value = index;
      document.documentElement.dataset.skin = String(index);
      if (window.frontDeskSkin)
        window.frontDeskSkin.save(index).catch((e) => {
          error.value = e.message;
        });
      else localStorage.setItem('pc-skin', String(index));
    }
    const initialSkin = window.frontDeskSkin
      ? window.frontDeskSkin.get()
      : Promise.resolve(localStorage.getItem('pc-skin'));
    initialSkin
      .then((value) => {
        const index = Number(value);
        if (pcSkins[index]) {
          pcSkin.value = index;
          document.documentElement.dataset.skin = String(index);
        }
      })
      .catch(() => {});
    const memberDetail = ref(null),
      memberDraft = ref(null),
      memberPage = ref(1),
      memberBusy = ref(false);
    const memberMessage = ref(''),
      memberError = ref(''),
      newMemberPassword = ref('');
    async function openMember(id, page = 1) {
      if (memberBusy.value) return;
      memberBusy.value = true;
      memberError.value = '';
      memberMessage.value = '';
      try {
        const detail = await api('/admin/members/' + id + '?page=' + page);
        if (memberDetail.value?.account.id !== id) {
          memberEditing.value = false;
          memberPasswordEditing.value = false;
          memberDraft.value = {
            nickname: detail.account.nickname || detail.account.username,
            age: detail.account.age ?? '',
            gender:
              detail.account.gender === '未填写' ? '不显示' : detail.account.gender || '不显示',
          };
          newMemberPassword.value = '';
        }
        memberDetail.value = detail;
        memberPage.value = page;
      } catch (e) {
        (memberDetail.value ? memberError : error).value = e.message;
      } finally {
        memberBusy.value = false;
      }
    }
    async function saveMember(kind) {
      if (memberBusy.value || !memberDetail.value) return;
      if (kind === 'password' && !(await confirmDialog('重置后该会员需要重新登录。继续？'))) return;
      memberBusy.value = true;
      memberError.value = '';
      memberMessage.value = '';
      const draft = memberDraft.value;
      const body =
        kind === 'password'
          ? { password: newMemberPassword.value }
          : { ...draft, age: draft.age === '' ? null : Number(draft.age) };
      try {
        await api('/admin/members/' + memberDetail.value.account.id + '/' + kind, {
          method: 'PUT',
          body: JSON.stringify(body),
        });
        if (kind === 'password') newMemberPassword.value = '';
        else {
          Object.assign(memberDetail.value.account, body);
          await loadMembers();
        }
        memberMessage.value = kind === 'password' ? '密码已重置' : '资料已保存';
        memberEditing.value = false;
        memberPasswordEditing.value = false;
      } catch (e) {
        memberError.value = e.message;
      } finally {
        memberBusy.value = false;
      }
    }
    const members = ref([]),
      memberQuery = ref(''),
      memberTier = ref(''),
      memberSort = ref('spend-desc');
    const tierRules = ref([]),
      tierDraft = ref(null);
    const tierIcons = [
      ['bronze', '青铜'],
      ['silver', '白银'],
      ['gold', '黄金'],
      ['diamond', '钻石'],
      ['black-gold', '黑金'],
    ];
    async function editTierRules() {
      await act(async () => {
        tierDraft.value = (await api('/admin/member-tiers')).map((rule) => ({ ...rule }));
      });
    }
    async function saveTierRules() {
      await act(async () => {
        tierRules.value = await api('/admin/member-tiers', {
          method: 'PUT',
          body: JSON.stringify(tierDraft.value),
        });
        tierDraft.value = null;
        await loadMembers();
      });
    }
    function tierIcon(name) {
      return (
        tierRules.value.find((rule) => rule.name === name)?.icon ||
        tierIcons.find((option) => option[1] === name)?.[0]
      );
    }
    const memberTiers = ['青铜', '白银', '黄金', '钻石', '黑金', '前台账户'];
    const visibleMembers = computed(() => {
      const query = memberQuery.value.trim().toLowerCase();
      const rows = members.value.filter(
        (m) =>
          (!memberTier.value || m.tier === memberTier.value) &&
          [m.customer_code, m.username, m.nickname, m.tier].some((v) =>
            String(v ?? '')
              .toLowerCase()
              .includes(query),
          ),
      );
      const sorters = {
        'spend-desc': (a, b) => Number(b.total_spend) - Number(a.total_spend),
        'spend-asc': (a, b) => Number(a.total_spend) - Number(b.total_spend),
        visits: (a, b) => Number(b.visits) - Number(a.visits),
      };
      return rows.sort((a, b) => sorters[memberSort.value](a, b) || a.id - b.id);
    });
    async function loadMembers() {
      try {
        const [rows, rules] = await Promise.all([
          api('/admin/members'),
          api('/admin/member-tiers'),
        ]);
        members.value = rows;
        tierRules.value = rules;
      } catch (e) {
        error.value = e.message;
      }
    }
    const activePackage = computed(() =>
      selectedPackages.value.find((pack) => pack.id === packageId.value),
    );
    const signedInName = computed(() => brand.value?.account.username || '');
    const brand = ref(null),
      storeDraft = ref(null),
      credentials = ref(null),
      logoutPrompt = ref(false),
      loginMessage = ref('');
    async function loadBrand() {
      try {
        brand.value = await api('/admin/account');
        const self = await api('/me');
        brand.value.account.customer_code = self.customerCode;
        finance.value = brand.value.finance;
      } catch (e) {
        error.value = e.message;
      }
    }
    function editStore(store) {
      error.value = '';
      storeDraft.value = { ...store };
    }
    async function saveStore() {
      await act(async () => {
        await api('/admin/account/store', {
          method: 'PUT',
          body: JSON.stringify(storeDraft.value),
        });
        storeDraft.value = null;
        await loadBrand();
      });
    }
    function editCredentials() {
      error.value = '';
      credentials.value = {
        username: brand.value.account.username,
        currentPassword: '',
        newPassword: '',
      };
    }
    async function saveCredentials() {
      await act(async () => {
        const draft = credentials.value;
        await api('/admin/account/credentials', { method: 'PUT', body: JSON.stringify(draft) });
        username.value = draft.username.toLowerCase();
        credentials.value = null;
        token.value = '';
        sessionStorage.removeItem('token');
        brand.value = null;
        tab.value = '球桌工作台';
        loginMessage.value = '账号已更新，请重新登录。';
      });
    }
    function enterTab(name) {
      tab.value = name;
      if (name === '用户设置') loadBrand();
      if (name === '套餐管理') act(loadPackageCatalog);
      if (name === '会员管理') loadMembers();
      if (name === '经营统计') setPeriod('month');
      if (name === '消费订单') searchOrders(datedOrders.value?.page || 1);
    }
    const settlementLabels = {
      IN_USE: '使用中',
      PENDING_RETURN: '球杆待归还',
      PAYMENT_PENDING: '服务待收款',
      SETTLED: '已结清',
    };

    const canSettleBill = computed(
      () =>
        bill.value &&
        bill.value.playOrder.status === 'COMPLETED' &&
        bill.value.pendingCueCount === 0 &&
        bill.value.activeCoachCount === 0 &&
        Number(bill.value.unpaidAmount) > 0,
    );
    function money(value) {
      return Number(value || 0).toFixed(2);
    }
    function tablePaidLabel(o) {
      return o.payment_method === 'WALLET'
        ? '钱包已支付'
        : o.payment_method === 'MOCK'
          ? '收款已登记'
          : '现金已支付';
    }
    function serviceStatusLabel(o) {
      if (o.status === 'SETTLED')
        return o.payment_method === 'WALLET'
          ? '已结算（钱包）'
          : o.payment_method === 'MOCK'
            ? '已结算（收款登记）'
            : o.payment_method === 'CASH'
              ? '已结算（现金登记）'
              : '已结算（历史登记）';
      return o.kind === 'CUE' && o.status === 'REQUESTED'
        ? '待前台确认领取'
        : serviceLabels[o.status];
    }

    function runningServices(b) {
      return (b.services || []).some((o) => o.status === 'ACTIVE');
    }
    function serviceMinutes(o) {
      return o.status === 'ACTIVE'
        ? Math.max(1, Math.ceil((now.value - new Date(o.started_at).getTime()) / 60000))
        : (o.billed_minutes ?? 0);
    }
    function serviceAmount(o) {
      if (o.status === 'REQUESTED' || o.status === 'CANCELLED') return '—';
      if (o.status !== 'ACTIVE') return money(o.amount);
      if (o.estimated_amount != null) return money(o.estimated_amount);
      return money(
        Number(o.rate) *
          (o.booked_hours
            ? Number(o.booked_hours) * 60
            : o.kind === 'CUE'
              ? Math.ceil(serviceMinutes(o) / 60)
              : serviceMinutes(o)),
      );
    }
    const serviceLabels = {
      REQUESTED: '待确认',
      ACTIVE: '计时中',
      COMPLETED: '待结算',
      SETTLED: '已结算',
      CANCELLED: '已取消',
    };

    const labels = {
      IDLE: '空闲可用',
      IN_USE: '使用中',
      CLEANING: '待清理',
      MAINTENANCE: '维护中',
      ACTIVE: '使用中',
      COMPLETED: '已结束',
    };
    async function api(path, options = {}) {
      const r = await fetch('/api' + path, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer ' + token.value,
          ...options.headers,
        },
      });
      if (!r.ok) {
        let d = await r.json();
        if (r.status === 401) {
          token.value = '';
          sessionStorage.removeItem('token');
        }
        throw Error(d.message || '请求失败');
      }
      return r.status === 204 || r.headers.get('content-length') === '0'
        ? null
        : r.text().then((t) => (t ? JSON.parse(t) : null));
    }
    async function act(fn) {
      if (busy.value) return;
      busy.value = true;
      error.value = '';
      try {
        await fn();
      } catch (e) {
        error.value = e.message;
      } finally {
        busy.value = false;
      }
    }
    async function load() {
      pendingCueOrders.value = await api('/admin/order-attention');
      const summary = api('/workbench-summary')
        .then((r) => {
          todayIncome.value = r;
        })
        .catch(() => {
          todayIncome.value = null;
        });
      const [t, p, o, m, pr, sv, so, gr] = await Promise.all([
        api('/tables'),
        api('/packages'),
        api('/orders'),
        api('/models'),
        api('/prices'),
        api('/admin/services'),
        api('/service-orders'),
        api('/coach-grades'),
      ]);
      grades.value = gr.map((g) => ({
        ...g,
        rate: Number(money(g.hourly_rate ?? Number(g.rate) * 60)),
      }));
      models.value = m;
      prices.value = pr;
      services.value = sv.filter((i) => i.image_path);
      serviceOrders.value = so;
      now.value = Date.now();
      tables.value = t;
      packages.value = p;
      orders.value = o;
      if (bill.value) bill.value = await api('/orders/' + bill.value.playOrder.id + '/bill');
      await summary;
      if (tab.value === '套餐管理') await loadPackageCatalog();
      if (tab.value === '消费订单') await fetchDatedOrders(datedOrders.value?.page || 1);
    }
    async function login() {
      loginMessage.value = '';
      await act(async () => {
        let d = await api('/auth/admin', {
          method: 'POST',
          body: JSON.stringify({ username: username.value, password: password.value }),
        });
        token.value = d.token;
        sessionStorage.setItem('token', d.token);
        password.value = '';
        await Promise.all([load(), loadBrand()]);
      });
    }
    async function logout() {
      logoutPrompt.value = false;
      brand.value = null;
      tab.value = '球桌工作台';
      try {
        await api('/auth/logout', { method: 'POST' });
      } finally {
        token.value = '';
        sessionStorage.removeItem('token');
      }
    }
    async function choose(t) {
      await act(async () => {
        method.value = 'CASH';
        selectedPackages.value = [];
        packageId.value = '';
        selectedPackages.value = await api('/packages?tableId=' + t.id);
        selected.value = t;
        packageId.value = selectedPackages.value[0]?.id || '';
      });
    }
    const frontServiceMethod = ref('MOCK');
    const frontServiceId = ref(''),
      frontServiceHours = ref(1);
    const frontServices = computed(() =>
      services.value.filter((item) => item.enabled && Number(item.available) > 0),
    );
    const billTool = ref(''),
      frontServiceKind = ref('COACH'),
      frontServiceSearch = ref(''),
      customerSearch = ref(''),
      billNotice = ref('');
    const frontChoice = computed(() =>
      frontServices.value.find((item) => Number(item.id) === Number(frontServiceId.value)),
    );
    const frontServiceCards = computed(() =>
      frontServices.value.filter(
        (item) =>
          item.kind === frontServiceKind.value &&
          (!frontServiceSearch.value ||
            (item.brand + ' ' + item.name)
              .toLowerCase()
              .includes(frontServiceSearch.value.trim().toLowerCase())),
      ),
    );
    const customerCards = computed(() =>
      members.value
        .filter((member) => member.role !== 'FRONT_DESK')
        .filter(
          (member) =>
            !customerSearch.value ||
            [member.customer_code, member.username, member.nickname, member.tier]
              .join(' ')
              .toLowerCase()
              .includes(customerSearch.value.trim().toLowerCase()),
        )
        .slice(0, 8),
    );
    const customerChoice = computed(() =>
      members.value.find((member) => Number(member.id) === Number(customerId.value)),
    );
    function frontHourly(item) {
      return item.kind === 'COACH'
        ? item.personal_rate == null
          ? (grades.value.find((g) => Number(g.stars) === Number(item.star_level))?.rate ??
            Number(item.rate) * 60)
          : Number(item.personal_rate) * 60
        : Number(item.rate);
    }
    async function toggleBillTool(tool) {
      billTool.value = tool;
      billNotice.value = '';
      if (billTool.value === 'customer') await act(loadMembers);
    }
    const frontServiceQuote = computed(() => {
      const item = frontServices.value.find(
        (row) => Number(row.id) === Number(frontServiceId.value),
      );
      if (!item) return '0.00';
      const rate =
        item.kind === 'COACH'
          ? item.personal_rate == null
            ? (grades.value.find((g) => Number(g.stars) === Number(item.star_level))?.rate ??
              Number(item.rate) * 60)
            : Number(item.personal_rate) * 60
          : Number(item.rate);
      return money(rate * Number(frontServiceHours.value));
    });
    let frontServiceRequest;
    async function bookFrontService() {
      const play = bill.value?.playOrder;
      const item = frontServices.value.find(
        (row) => Number(row.id) === Number(frontServiceId.value),
      );
      if (play?.status !== 'ACTIVE' || !item) return;
      await act(async () => {
        const hours = Number(frontServiceHours.value);
        if (!Number.isInteger(hours) || hours < 1 || hours > 12) throw Error('请选择1至12小时');
        const paymentMethod = frontServiceMethod.value;
        const scope = [play.id, item.id, hours, paymentMethod].join(':');
        if (frontServiceRequest?.scope !== scope)
          frontServiceRequest = { scope, key: crypto.randomUUID() };
        demoCheckout.value = {
          amount: frontServiceQuote.value,
          table: item.name,
          paid: false,
          method: paymentMethod,
        };
        if (!(await awaitReceipt(paymentMethod))) return;
        try {
          const order = await api('/service-orders', {
            method: 'POST',
            body: JSON.stringify({
              itemId: item.id,
              playOrderId: play.id,
              requestKey: frontServiceRequest.key,
              hours,
              paymentMethod,
            }),
          });
          frontServiceRequest = null;
          billTool.value = '';
          demoCheckout.value = {
            amount: money(order.amount),
            table: item.name,
            paid: true,
            method: paymentMethod,
            message: item.name + ' · ' + hours + '小时服务已添加',
          };
        } catch (e) {
          demoCheckout.value = null;
          throw e;
        }
        frontServiceId.value = '';
        await load();
      });
    }
    const demoCheckout = ref(null);
    function storeMapUrl(store) {
      const query = new URLSearchParams({
        keyword: store.address || store.name,
        city: store.city || '',
        view: 'map',
        src: 'shengtai',
      });
      return 'https://uri.amap.com/search?' + query;
    }
    let receiptAnswer;
    function confirmReceipt(accepted) {
      receiptAnswer?.(accepted);
      receiptAnswer = null;
    }
    async function awaitReceipt(method) {
      if (method !== 'CASH') {
        demoCheckout.value.awaiting = true;
        const accepted = await new Promise((resolve) => {
          receiptAnswer = resolve;
        });
        if (!accepted) {
          demoCheckout.value = null;
          return false;
        }
        demoCheckout.value.awaiting = false;
      }
      await new Promise((resolve) => setTimeout(resolve, 1500));
      return true;
    }
    let openRequest;
    async function open() {
      await act(async () => {
        const table = selected.value;
        const pack = selectedPackages.value.find(
          (item) => Number(item.id) === Number(packageId.value),
        );
        if (!table || !pack) return;
        const paymentMethod = method.value;
        const simulated = paymentMethod === 'MOCK';
        const scope = [table.id, pack.id, paymentMethod].join(':');
        if (openRequest?.scope !== scope) openRequest = { scope, key: crypto.randomUUID() };
        {
          demoCheckout.value = {
            amount: money(pack.price),
            table: table.table_no,
            paid: false,
            method: paymentMethod,
          };
          if (!(await awaitReceipt(paymentMethod))) return;
        }
        try {
          const order = await api('/orders/open', {
            method: 'POST',
            body: JSON.stringify({
              tableId: table.id,
              packageId: Number(pack.id),
              requestKey: openRequest.key,
              paymentMethod,
            }),
          });
          openRequest = null;
          selected.value = null;
          demoCheckout.value = {
            amount: money(order.amount ?? pack.price),
            table: table.table_no,
            paid: true,
            method: paymentMethod,
          };
        } catch (e) {
          demoCheckout.value = null;
          throw e;
        }
        await load();
      });
    }
    async function finish(id) {
      await act(async () => {
        const preview = await api('/orders/' + id + '/bill');
        const message = preview.pendingCueCount
          ? '结束本局？还有 ' + preview.pendingCueCount + ' 根球杆待归还。'
          : '结束本局并生成账单？';
        if (!(await confirmDialog(message))) return;
        await api('/orders/' + id + '/finish', { method: 'POST' });
        await load();
        bill.value = await api('/orders/' + id + '/bill');
      });
    }
    async function showBill(id) {
      await act(async () => {
        bill.value = await api('/orders/' + id + '/bill');
        frontServiceMethod.value = bill.value.playOrder.payment_method === 'CASH' ? 'CASH' : 'MOCK';
      });
    }
    async function linkCustomer() {
      const id = Number(customerId.value);
      if (!Number.isSafeInteger(id) || id < 1) {
        error.value = '请先选择一位顾客';
        return;
      }
      await act(async () => {
        const name = customerChoice.value?.nickname || customerChoice.value?.username || '顾客';
        await api('/orders/' + bill.value.playOrder.id + '/customer', {
          method: 'POST',
          body: JSON.stringify({ customerId: id }),
        });
        customerId.value = '';
        billTool.value = '';
        await load();
        billNotice.value = '已关联 ' + name + '，手机端刷新即可查看本局。';
      });
    }
    async function settleBill() {
      if (!canSettleBill.value) return;
      const { playOrder, unpaidAmount } = bill.value;
      const paymentMethod = settleMethod.value;
      if (
        paymentMethod === 'CASH' &&
        !(await confirmDialog('请确认已经收到本局服务费 ¥' + money(unpaidAmount)))
      )
        return;
      await act(async () => {
        const receipt = {
          amount: money(unpaidAmount),
          table: playOrder.table_no,
          paid: false,
          method: paymentMethod,
        };
        demoCheckout.value = receipt;
        try {
          if (!(await awaitReceipt(paymentMethod))) return;
          await api('/orders/' + playOrder.id + '/settle', {
            method: 'POST',
            body: JSON.stringify({ paymentMethod }),
          });
          demoCheckout.value = {
            ...receipt,
            paid: true,
            message: playOrder.table_no + ' · 服务费用已结清',
          };
        } catch (e) {
          demoCheckout.value = null;
          throw e;
        }
        await load();
      });
    }
    async function state(t, status) {
      await act(async () => {
        await api('/tables/' + t.id + '/status', {
          method: 'POST',
          body: JSON.stringify({ status }),
        });
        await load();
      });
    }

    async function editService(item) {
      if (!item.image_path) {
        error.value = '请先从电脑选择服务图片';
        return;
      }
      await act(async () => {
        const profile = {
          ...item,
          rate: item.kind === 'COACH' ? null : Number(item.rate),
          imagePath: item.image_path,
          starLevel: item.star_level,
          heightCm: item.height_cm,
          weightKg: item.weight_kg,
        };
        await api('/admin/services' + (item.id ? '/' + item.id : ''), {
          method: item.id ? 'PUT' : 'POST',
          body: JSON.stringify({ kind: item.kind, brand: item.brand, profile }),
        });
        await load();
        editDraft.value = null;
        editItemId.value = null;
        saved.value = '服务资料已保存';
      });
    }
    async function saveGrade(g) {
      await act(async () => {
        await api('/coach-grades/' + g.stars, {
          method: 'PUT',
          body: JSON.stringify({ hourlyRate: Number(g.rate) }),
        });
        await load();
      });
    }
    async function serviceAction(o, action) {
      if (action === 'settle' && o.play_order_id) {
        await showBill(o.play_order_id);
        return;
      }
      const hints = {
        start:
          o.kind === 'CUE'
            ? '确认已验杆并交给顾客？确认后开始球杆租赁计时。'
            : '处理旧版待确认助教单：确认实际开始服务后计时。新助教单由顾客选择后直接计时。',
        finish:
          o.kind === 'CUE'
            ? '确认球杆已归还并验收？确认后停止租赁计时，生成最终费用。'
            : '确认结束助教服务？费用按预约小时数计入本局账单。',
        settle:
          '这是旧版未关联球桌的服务单。请确认已经收到 ¥' + money(o.amount) + '，再登记线下收款。',
        cancel: '确认取消尚未开始的申请？',
      };
      if (!(await confirmDialog(hints[action]))) return;
      await act(async () => {
        await api('/service-orders/' + o.id + '/' + action, { method: 'POST' });
        await load();
        if (action === 'finish' && o.play_order_id)
          bill.value = await api('/orders/' + o.play_order_id + '/bill');
      });
    }

    const packageCatalog = ref([]);
    async function loadPackageCatalog() {
      packageCatalog.value = await api('/admin/packages');
    }
    async function enablePackage(id) {
      await act(async () => {
        await api('/packages/' + id + '/enable', { method: 'POST' });
        await load();
      });
    }
    async function addPackage() {
      await act(async () => {
        await api('/packages', { method: 'POST', body: JSON.stringify(newPack.value) });
        newPack.value = { name: '', minutes: 60, price: 38 };
        await load();
      });
    }
    async function disable(id) {
      if (await confirmDialog('下架后将不能选择此套餐，历史订单保留。'))
        await act(async () => {
          await api('/packages/' + id + '/disable', { method: 'POST' });
          await load();
        });
    }
    const income = computed(() =>
      orders.value.reduce((a, o) => a + Number(o.amount), 0).toFixed(2),
    );
    function remaining(t) {
      if (!t.expected_end_at) return '';
      let n = Math.ceil((new Date(t.expected_end_at).getTime() - now.value) / 60000);
      return n >= 0 ? '剩余 ' + n + ' 分钟' : '已超时 ' + -n + ' 分钟';
    }
    function date(s) {
      return s ? new Date(s).toLocaleString('zh-CN') : '—';
    }
    function refreshWorkspace() {
      if (tab.value === '用户设置') return loadBrand();
      if (tab.value === '经营统计') return loadStats();
      if (tab.value === '会员管理') return act(loadMembers);
      return act(load);
    }
    let polling = false;
    onMounted(() => {
      if (token.value) act(() => Promise.all([load(), loadBrand()]));
      timer = setInterval(async () => {
        now.value = Date.now();
        if (
          !token.value ||
          busy.value ||
          polling ||
          selected.value ||
          memberDetail.value ||
          tierDraft.value ||
          editDraft.value ||
          packageEdit.value ||
          gradeEditor.value ||
          billTool.value
        )
          return;
        const current = tab.value;
        if (!['球桌工作台', '消费订单', '服务管理', '会员管理', '经营统计'].includes(current))
          return;
        polling = true;
        try {
          if (current === '经营统计') {
            const [report, month] = await Promise.all([
              api('/statistics?from=' + statsFrom.value + '&to=' + statsTo.value),
              api('/monthly-finance'),
            ]);
            stats.value = report;
            finance.value = month;
          } else if (current === '会员管理') await loadMembers();
          else if (current === '服务管理') await load();
          else await load();
        } catch (e) {
          error.value = e.message;
        } finally {
          polling = false;
        }
      }, 10000);
    });
    onUnmounted(() => clearInterval(timer));

    const brands = computed(() => {
      const map = new Map();
      for (const m of models.value) {
        const old = map.get(m.brand);
        if (!old || m.id < old.id) map.set(m.brand, { id: m.id, name: m.brand });
      }
      return [...map.values()];
    });
    const pricingTarget = ref('DEFAULT'),
      packageEdit = ref(null),
      gradeEditor = ref(false),
      memberEditing = ref(false),
      memberPasswordEditing = ref(false);
    const selectedMember = computed(() =>
      members.value.find((m) => m.id === memberDetail.value?.account.id),
    );
    function scopedPackage(pack) {
      const [scope, sid] = pricingTarget.value.split(':');
      if (scope === 'DEFAULT')
        return { ...pack, displayPrice: pack.price, scopeLabel: '门店默认价格' };
      const own = prices.value.find(
        (r) =>
          r.scope_type === scope &&
          Number(r.scope_id) === Number(sid) &&
          Number(r.package_id) === Number(pack.id),
      );
      const table = tables.value.find((t) => Number(t.id) === Number(sid));
      const inherited =
        scope === 'TABLE'
          ? prices.value.find(
              (r) =>
                r.scope_type === 'MODEL' &&
                Number(r.scope_id) === Number(table?.model_id) &&
                Number(r.package_id) === Number(pack.id),
            )
          : null;
      return {
        ...pack,
        displayPrice: own?.price ?? inherited?.price ?? pack.price,
        hasOverride: !!own,
        scopeLabel: own
          ? '品牌统一价格'
          : inherited
            ? '继承型号价格'
            : '未设置品牌价 · 保留现有定价',
      };
    }
    const scopedPackages = computed(() => packageCatalog.value.map(scopedPackage));
    function beginPackageEdit(pack) {
      packageEdit.value = {
        ...pack,
        price: Number(pack.displayPrice),
        target: pricingTarget.value,
      };
      error.value = '';
    }
    async function savePackageEdit(reset = false) {
      const pack = packageEdit.value;
      if (!pack) return;
      await act(async () => {
        const [scope, sid] = pack.target.split(':');
        if (scope === 'DEFAULT')
          await api('/packages/' + pack.id, {
            method: 'PUT',
            body: JSON.stringify({
              name: pack.name,
              minutes: Number(pack.minutes),
              price: Number(pack.price),
            }),
          });
        else
          await api('/prices', {
            method: 'PUT',
            body: JSON.stringify({
              scope,
              scopeId: Number(sid),
              packageId: pack.id,
              price: reset ? null : Number(pack.price),
            }),
          });
        packageEdit.value = null;
        await load();
      });
    }
    function beginCreateService() {
      const coach = catalogKind.value === 'COACH';
      const picture =
        services.value.find((p) => p.kind === catalogKind.value)?.image_path ||
        (coach ? '/assets/services/coach-mianmian.png' : '/assets/services/peri-tianwang.png');
      editDraft.value = {
        kind: catalogKind.value,
        brand: coach ? '圣泰助教' : '自定义品牌',
        name: '',
        image_path: '',
        description: '',
        rate: coach ? '' : 24,
        stock: 1,
        enabled: true,
        gender: '女',
        star_level: 3,
        age: 22,
        height_cm: 168,
        weight_kg: 52,
        personality: '耐心细致',
        specialties: '基础出杆与走位',
      };
      editItemId.value = null;
      error.value = '';
    }
    async function deleteService(item) {
      if (
        !(await confirmDialog(
          '删除 ' + item.service_code + ' · ' + item.name + '？历史订单会保留。',
        ))
      )
        return;
      await act(async () => {
        await api('/admin/services/' + item.id, { method: 'DELETE' });
        await load();
      });
    }

    function monthlySalary(item) {
      return (
        finance.value?.payroll?.find((row) => Number(row.id) === Number(item.id))
          ?.estimated_salary ??
        item.estimated_salary ??
        0
      );
    }
    function serviceCode(item) {
      return item.service_code || (item.kind === 'COACH' ? 'S' : 'G') + (100 + Number(item.id));
    }

    const uploadingImage = ref(false),
      expenseDialog = ref(false),
      storeBillDialog = ref(false),
      storeBillLoading = ref(false),
      storeBillError = ref(''),
      storeBill = ref(null),
      storeMonth = ref(localDate(new Date()).slice(0, 7)),
      storeBillTab = ref('overview');
    let billRequest = 0;
    async function uploadServiceImage(event) {
      const file = event.target.files?.[0];
      if (!file) return;
      error.value = '';
      if (file.size > 5 * 1024 * 1024) {
        error.value = '请选择不超过 5 MB 的图片';
        return;
      }
      uploadingImage.value = true;
      try {
        const form = new FormData();
        form.append('file', file);
        const r = await fetch('/api/admin/service-images', {
          method: 'POST',
          headers: { Authorization: 'Bearer ' + token.value },
          body: form,
        });
        const data = await r.json();
        if (!r.ok) throw Error(data.message || '图片上传失败');
        if (editDraft.value) editDraft.value.image_path = data.path;
      } catch (e) {
        error.value = e.message;
      } finally {
        uploadingImage.value = false;
        event.target.value = '';
      }
    }
    async function openStoreBill(month = storeMonth.value) {
      storeBillDialog.value = true;
      storeMonth.value = month;
      storeBillLoading.value = true;
      storeBillError.value = '';
      const request = ++billRequest;
      try {
        const report = await api('/admin/store-bill?month=' + month);
        if (request === billRequest) storeBill.value = report;
      } catch (e) {
        if (request === billRequest) storeBillError.value = e.message;
      } finally {
        if (request === billRequest) storeBillLoading.value = false;
      }
    }
    function shiftStoreMonth(delta) {
      const [y, m] = storeMonth.value.split('-').map(Number);
      const d = new Date(y, m - 1 + delta, 1);
      openStoreBill(localDate(d).slice(0, 7));
    }
    const billExpenses = computed(() =>
      (storeBill.value?.expenses || []).reduce((n, e) => n + Number(e.amount), 0),
    );
    const billPayrollPaid = computed(() =>
      (storeBill.value?.expenses || [])
        .filter((e) => e.category === 'PAYROLL')
        .reduce((n, e) => n + Number(e.amount), 0),
    );
    const billPayrollPending = computed(() =>
      Math.max(0, Number(storeBill.value?.payrollTotal || 0) - billPayrollPaid.value),
    );

    return {
      storeMapUrl,
      confirmReceipt,
      prompt,
      answerPrompt,
      settleMethod,
      expenseEditing,
      expenseMessage,
      resetExpense,
      editExpense,
      cancelExpense,
      pendingOrdersOnly,
      pendingCueOrders,
      catalogFilter,
      catalogFilters,
      brands,
      uploadingImage,
      uploadServiceImage,
      expenseDialog,
      storeBillDialog,
      storeBillLoading,
      storeBillError,
      storeBill,
      storeMonth,
      storeBillTab,
      openStoreBill,
      shiftStoreMonth,
      billExpenses,
      billPayrollPaid,
      billPayrollPending,
      pricingTarget,
      packageEdit,
      gradeEditor,
      memberEditing,
      memberPasswordEditing,
      selectedMember,
      scopedPackages,
      beginPackageEdit,
      savePackageEdit,
      beginCreateService,
      deleteService,
      monthlySalary,
      serviceCode,
      frontServiceMethod,
      billTool,
      frontServiceKind,
      frontServiceSearch,
      customerSearch,
      billNotice,
      frontChoice,
      frontServiceCards,
      customerCards,
      customerChoice,
      frontHourly,
      toggleBillTool,
      frontServiceQuote,
      refreshWorkspace,
      tierDraft,
      tierIcons,
      tierIcon,
      editTierRules,
      saveTierRules,
      orderPreset,
      statsRefresh,
      packageCatalog,
      enablePackage,
      activePackage,
      signedInName,
      brand,
      loadBrand,
      storeDraft,
      logoutPrompt,
      editStore,
      saveStore,
      credentials,
      editCredentials,
      saveCredentials,
      loginMessage,
      pcSkins,
      pcSkin,
      setPcSkin,
      memberDetail,
      memberDraft,
      memberPage,
      memberBusy,
      memberMessage,
      memberError,
      newMemberPassword,
      openMember,
      saveMember,
      frontServiceId,
      frontServiceHours,
      frontServices,
      bookFrontService,
      demoCheckout,
      memberQuery,
      memberTier,
      memberSort,
      memberTiers,
      visibleMembers,
      loadMembers,
      orderDates,
      appliedOrderDates,
      datedOrders,
      searchOrders,
      orderShortcut,
      detailKind,
      detailQuery,
      detailPage,
      usedOnly,
      detailRows,
      detailPages,
      detailCards,
      switchDetail,
      todayIncome,
      finance,
      expense,
      expenseTotal,
      expenseParts,
      expenseCategories,
      expenseColors,
      revenueParts,
      revenueTotal,
      ring,
      saveExpense,
      visibleTables,
      tableQuery,
      tableState,
      tableType,
      packPresets,
      editDraft,
      saved,
      beginEdit,
      statsPeriod,
      setPeriod,
      statsCoaches,
      statsCues,
      catalogKind,
      catalogSearch,
      catalogRows,
      editItemId,
      stats,
      statsFrom,
      statsTo,
      statsRows,
      receiptTotal,
      kindTotal,
      dailyStats,
      loadStats,
      enterTab,
      customerId,
      linkCustomer,
      tablePaidLabel,
      serviceStatusLabel,
      runningServices,
      bill,
      settlementLabels,
      canSettleBill,
      money,
      serviceMinutes,
      serviceAmount,
      showBill,
      settleBill,
      grades,
      saveGrade,
      selectedPackages,
      models,
      prices,
      services,
      serviceOrders,
      serviceLabels,
      editService,
      serviceAction,
      token,
      password,
      username,
      tab,
      tables,
      packages,
      orders,
      error,
      busy,
      selected,
      packageId,
      method,
      labels,
      newPack,
      income,
      login,
      logout,
      load,
      act,
      choose,
      open,
      finish,
      state,
      addPackage,
      disable,
      remaining,
      date,
    };
  },
  template: template,
};
