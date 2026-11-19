const clubUI = require('./club');
const walletUI = require('./wallet');
const profileUI = require('./profile');
const servicesUI = require('./services');
const ordersUI = require('./orders');
const { labels, settlementLabels } = require('./labels');
const { resolveAppearance } = require('./appearance');
const { requestApi } = require('../../api');
const initialAppearance = resolveAppearance();

Page({
  ...profileUI.methods,
  ...servicesUI.methods,
  ...ordersUI.methods,
  ...walletUI.methods,
  ...clubUI.methods,
  data: {
    themeStyle: initialAppearance.style,
    themeAccent: initialAppearance.accent,
    themeLine: initialAppearance.palette.line,
    heroAccent: initialAppearance.heroAccent,
    heroTrack: initialAppearance.heroTrack,
    prompt: null,
    authMode: 'login',
    ...walletUI.data,
    ...clubUI.data,
    customerCode: null,
    bookingSheet: null,
    skinOptions: [],
    skinMode: 'auto',
    skinKey: 'club',
    skinCategory: 'interface',
    exclusiveSkinKey: 'none',
    exclusiveOptions: [],
    profile: {},
    profileDraft: {},
    profilePage: null,
    profileMessage: '',
    membership: null,
    playedTime: '0 分钟',
    memberBadges: [
      { name: '黑金', image: 'black-gold' },
      { name: '钻石', image: 'diamond' },
      { name: '黄金', image: 'gold' },
      { name: '白银', image: 'silver' },
      { name: '青铜', image: 'bronze' },
    ],
    isDemo: true,
    logged: false,
    customerId: null,
    tab: 'tables',
    tables: [],
    visibleTables: [],
    packages: [],
    orders: [],
    visibleOrders: [],
    selected: null,
    detail: null,
    bill: null,
    billLoading: false,
    packageId: 0,
    chosenPackage: null,
    busy: false,
    loading: true,
    error: '',
    labels,
    settlementLabels,
    serviceKind: 'CUE',
    serviceGender: '全部',
    serviceGrade: 0,
    serviceSelected: null,
    serviceBrand: '全部品牌',
    serviceBrands: [],
    services: [],
    visibleServices: [],
    serviceOrders: [],
    visibleServiceOrders: [],
    orderKind: 'PLAY',
    orderFilter: '进行中',
    managingHistory: false,
    selectedHistoryIds: [],
    selectedHistoryCount: 0,
    allHistorySelected: false,
    filter: '全部',
    brandFilter: '全部品牌',
    brandOptions: ['全部品牌'],
    availableOnly: false,
    available: 0,
    active: null,
    activeCount: 0,
    activeServiceCount: 0,
    pendingServiceCount: 0,
    orderBadge: 0,
    totalSpent: '0.00',
  },
  showPrompt(options) {
    this.promptOptions = options;
    this.setData({
      prompt: {
        title: options.title || '操作提示',
        paragraphs: String(options.content || '')
          .split(/\n|(?<=。)|；/)
          .map((x) => x.trim())
          .filter(Boolean),
        showCancel: options.showCancel !== false,
        confirmText: options.confirmText || '确认',
        cancelText: options.cancelText || '取消',
      },
    });
  },
  resolvePrompt(e) {
    const confirm = e.currentTarget.dataset.accept === 'yes';
    const options = this.promptOptions;
    this.promptOptions = null;
    this.setData({ prompt: null });
    options?.success?.({ confirm, cancel: !confirm });
    options?.complete?.({ confirm, cancel: !confirm });
  },
  formatPlayedTime(value) {
    const minutes = Number(value) || 0;
    const hours = Math.floor(minutes / 60);
    return (hours ? hours + ' 小时 ' : '') + (minutes % 60) + ' 分钟';
  },
  accountTokenKey() {
    return getApp().globalData.loginMode === 'member' ? 'memberCustomerToken' : 'demoCustomerToken';
  },
  onLoad() {
    const { loginMode, demoCheckout } = getApp().globalData;
    this.setData({
      memberLogin: loginMode === 'member',
      isDemo: loginMode !== 'member' || demoCheckout === true,
    });
    this.token = wx.getStorageSync(this.accountTokenKey()) || '';
    this.setData({ logged: !!this.token });
  },
  onShow() {
    this.updateTitle();
    this.applySkin();
    this.refresh();
    this.timer = setInterval(() => this.tick(), 1000);
    this.poll = setInterval(() => {
      if (!this.data.busy) {
        this.refresh(true);
        if (this.data.profilePage === 'leaderboard') this.loadLeaderboard();
      }
    }, 5000);
  },
  onHide() {
    clearInterval(this.timer);
    clearInterval(this.poll);
    this.closePayment();
  },
  onUnload() {
    clearInterval(this.timer);
    clearInterval(this.poll);
  },
  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh());
  },
  resetAccount() {
    this.setData({ pendingOrderCount: 0, leaderboard: null, leaderError: '' });
    this.pinDigits = '';
    this.firstPin = '';
    this.walletOldPin = '';
    this.walletRecoveryPassword = '';
    this.topupRequest = null;
    this.pinReturnToCheckout = false;
    this.walletIntent = null;
    this.walletIntentScope = null;
    this.walletIntentKey = null;
    this.token = '';
    wx.removeStorageSync(this.accountTokenKey());
    this.walletPrivacyAccount = null;
    this.setData({
      walletHidden: true,
      logged: false,
      customerId: null,
      customerCode: null,
      wallet: null,
      pinSheet: null,
      checkout: null,
      walletBalance: '0.00',
      walletTransactions: [],
      walletVisibleTransactions: [],
      walletSecurityPage: '',
      walletLoginPassword: '',
      walletRecord: null,
      topupVisible: false,
      walletNotice: '',
      walletError: '',
      scanStage: '',
      membership: null,
      playedTime: '0 分钟',
      profile: {},
      profileDraft: {},
      profilePage: null,
      profileMessage: '',
      orders: [],
      serviceOrders: [],
      visibleOrders: [],
      visibleServiceOrders: [],
      detail: null,
      bill: null,
      active: null,
      activeCount: 0,
      activeServiceCount: 0,
      pendingServiceCount: 0,
      orderBadge: 0,
      totalSpent: '0.00',
      managingHistory: false,
      selectedHistoryIds: [],
      selectedHistoryCount: 0,
      allHistorySelected: false,
    });
    this.applySkin();
    this.updateTitle();
  },
  request(path, method = 'GET', data) {
    return requestApi({
      baseUrl: getApp().globalData.baseUrl,
      path,
      method,
      data,
      token: this.token,
      onUnauthorized: () => this.resetAccount(),
    });
  },
  image(path) {
    return path ? getApp().globalData.baseUrl.replace(/\/api\/?$/, '') + path : '';
  },
  money(value) {
    return Number(value || 0).toFixed(2);
  },
  date(t) {
    return t ? String(t).replace('T', ' ').slice(0, 16) : '—';
  },
  async refresh(silent = false) {
    silent = silent === true;
    if (!silent) this.setData({ loading: true, error: '' });
    try {
      const [rawTables, rawServices] = await Promise.all([
        this.request('/tables'),
        this.request('/services'),
      ]);
      const tables = rawTables.map((t) => ({ ...t, imageSrc: this.image(t.image_path) }));
      const selected = this.data.selected
        ? tables.find((t) => t.id === this.data.selected.id)
        : null;
      const services = rawServices.map((i) => ({
        ...i,
        hourlyText: this.money(
          i.kind === 'COACH' ? (i.hourly_rate ?? Number(i.rate) * 60) : i.rate,
        ),
        imageSrc: this.image(i.image_path),
        starsText: '★'.repeat(i.star_level || 0),
      }));
      this.setData({
        tables,
        services,
        selected: selected || null,
        serviceSelected: this.data.serviceSelected
          ? services.find((i) => i.id === this.data.serviceSelected.id) || null
          : null,
        available: tables.filter((t) => t.status === 'IDLE').length,
      });
      if (selected) await this.loadPackages(selected.id);
      if (this.data.logged) {
        const [rows, rawServiceOrders, me] = await Promise.all([
          this.request('/orders'),
          this.request('/service-orders'),
          this.request('/me').catch(() => null),
          this.loadWallet().catch(() => null),
        ]);
        if (me) {
          this.setData({
            customerId: me.id,
            customerCode: me.customerCode,
            totalSpent: this.money(me.totalSpent),
            playedTime: this.formatPlayedTime(me.playedMinutes),
            membership: this.formatMembership(me.membership),
            profile: me.profile || {},
            profileAvatar: wx.getStorageSync('memberAvatar:' + me.id) || '',
          });
          this.applySkin();
        }
        const orders = rows.map((o) => this.normalizePlay(o)),
          serviceOrders = rawServiceOrders.map((o) => this.normalizeService(o));
        const pendingReturn = orders.find(
          (o) =>
            o.settlement_status === 'PENDING_RETURN' &&
            !wx.getStorageSync('cueReminder:' + this.data.customerId + ':' + o.id),
        );
        if (pendingReturn && !this.data.prompt && !this.data.checkout && !this.data.busy) {
          wx.setStorageSync('cueReminder:' + this.data.customerId + ':' + pendingReturn.id, true);
          this.showPrompt({
            title: '请到前台归还球杆',
            content: pendingReturn.table_no + ' 的球局已结束，还有租用的球杆待归还。',
            showCancel: false,
          });
        }
        const pendingServiceCount = serviceOrders.filter((o) =>
          ['REQUESTED', 'ACTIVE', 'COMPLETED'].includes(o.status),
        ).length;
        const activeCount = orders.filter((o) => o.status === 'ACTIVE').length;
        this.setData({
          orders,
          serviceOrders,
          activeCount,
          activeServiceCount: serviceOrders.filter((o) =>
            ['REQUESTED', 'ACTIVE'].includes(o.status),
          ).length,
          pendingServiceCount,
          orderBadge:
            activeCount +
            orders.filter((o) => o.status !== 'ACTIVE' && o.settlement_status !== 'SETTLED').length,
        });
        if (this.data.detail) await this.loadBill(this.data.detail.id);
      }
      if (this.data.error.startsWith('暂时连不上')) this.setData({ error: '' });
      this.filterData();
      this.filterServices();
      this.tick();
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ loading: false });
    }
  },
  async loadPackages(id) {
    const packages = await this.request('/packages?tableId=' + id);
    if (this.data.selected?.id !== id) return;
    const pid = packages.some((p) => p.id === this.data.packageId)
      ? this.data.packageId
      : packages[0]?.id || 0;
    this.setData({
      packages,
      packageId: pid,
      chosenPackage: packages.find((p) => p.id === pid) || null,
    });
  },
  filterData() {
    const f = this.data.filter,
      of = this.data.orderFilter;
    const category = this.data.tables.filter((t) => f === '全部' || t.type === f);
    const brands = ['全部品牌', ...new Set(category.map((t) => t.brand).filter(Boolean))];
    const brand = brands.includes(this.data.brandFilter) ? this.data.brandFilter : '全部品牌';
    const playMatch = (o) =>
      of === '进行中'
        ? o.status === 'ACTIVE'
        : of === '待结算'
          ? o.status !== 'ACTIVE' && o.settlement_status !== 'SETTLED'
          : o.canHide;
    const serviceMatch = (o) =>
      of === '进行中'
        ? ['REQUESTED', 'ACTIVE'].includes(o.status)
        : of === '待结算'
          ? o.status === 'COMPLETED'
          : o.canHide;
    const visibleOrders = this.data.orders.filter(playMatch).map((o) => {
        const services = this.data.serviceOrders.filter(
          (service) =>
            Number(service.play_order_id) === Number(o.id) && service.status !== 'CANCELLED',
        );
        return {
          ...o,
          sessionServices: services,
          historySelected: this.data.selectedHistoryIds.includes(o.id),
        };
      }),
      visibleServiceOrders = this.data.serviceOrders
        .filter((o) => o.kind === this.data.orderKind && serviceMatch(o))
        .map((o) => ({ ...o, historySelected: this.data.selectedHistoryIds.includes(o.id) })),
      shown = this.data.orderKind === 'PLAY' ? visibleOrders : visibleServiceOrders;
    this.setData({
      pendingOrderCount: this.data.orders.filter(
        (o) => o.status !== 'ACTIVE' && o.settlement_status !== 'SETTLED',
      ).length,
      brandOptions: brands,
      brandFilter: brand,
      visibleTables: category.filter(
        (t) =>
          (brand === '全部品牌' || t.brand === brand) &&
          (!this.data.availableOnly || t.status === 'IDLE'),
      ),
      visibleOrders,
      visibleServiceOrders,
      selectedHistoryCount: shown.filter((o) => o.historySelected).length,
      allHistorySelected: shown.length > 0 && shown.every((o) => o.historySelected),
    });
  },
  setFilter(e) {
    wx.vibrateShort?.({ type: 'light' });
    this.setData({ filter: e.currentTarget.dataset.value, brandFilter: '全部品牌' });
    this.filterData();
  },
  setBrand(e) {
    this.setData({ brandFilter: e.currentTarget.dataset.value });
    this.filterData();
  },
  toggleAvailable() {
    this.setData({ availableOnly: !this.data.availableOnly });
    this.filterData();
  },
  promptForLogin() {
    if (this.data.prompt) return;
    this.setData({ error: '' });
    this.showPrompt({
      title: '登录后开启你的球局',
      content: '登录或注册球友账号，即可开台、预约服务，并保存消费记录。',
      confirmText: '登录 / 注册',
      cancelText: '再看看',
      success: (result) => {
        if (!result.confirm) return;
        this.setData({
          tab: 'profile',
          profilePage: null,
          authMode: 'login',
          selected: null,
          serviceSelected: null,
          detail: null,
          bill: null,
          error: '',
        });
        this.applySkin();
        this.updateTitle();
        wx.pageScrollTo?.({ scrollTop: 0, duration: 200 });
      },
    });
  },
  showMemberLogin() {
    this.switchTab({ currentTarget: { dataset: { tab: 'profile' } } });
  },
  setAuthMode(e) {
    this.setData({ authMode: e.currentTarget.dataset.mode });
  },
  submitMemberAuth() {
    return this.data.authMode === 'register' ? this.registerMember() : this.login();
  },
  memberUsername(e) {
    this.setData({ memberUsername: e.detail.value });
  },
  memberPassword(e) {
    this.setData({ memberPassword: e.detail.value });
  },
  registerMember() {
    return this.login('register');
  },
  async login(action) {
    if (this.data.busy) return;
    const member = getApp().globalData.loginMode === 'member';
    const username = (this.data.memberUsername || '').trim();
    const password = this.data.memberPassword || '';
    if (member && (!username || !password)) {
      this.setData({ error: '请输入账号和密码' });
      return;
    }
    this.setData({ busy: true, error: '' });
    try {
      const session = member
        ? await this.request(
            '/auth/member/' + (action === 'register' ? 'register' : 'login'),
            'POST',
            { username, password },
          )
        : await this.request('/auth/demo', 'POST', {});
      this.token = session.token;
      wx.setStorageSync(this.accountTokenKey(), session.token);
      this.setData({ logged: true, memberPassword: '' });
      await this.refresh(true);
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
  async logout() {
    if (this.data.busy) return;
    try {
      await this.request('/auth/logout', 'POST', {});
    } catch (e) {}
    this.resetAccount();
  },
  async choose(e) {
    const selected = this.data.tables.find((t) => t.id === Number(e.currentTarget.dataset.id));
    this.pendingKey = null;
    this.setData({
      selected: selected || null,
      paymentCodeVisible: false,
      detail: null,
      bill: null,
      error: '',
      packages: [],
      chosenPackage: null,
    });
    if (selected)
      try {
        await this.loadPackages(selected.id);
      } catch (e) {
        this.setData({ error: e.message });
      }
  },
  pack(e) {
    this.setData({ paymentCodeVisible: false });
    this.pendingKey = null;
    const id = Number(e.currentTarget.dataset.id);
    this.setData({ packageId: id, chosenPackage: this.data.packages.find((p) => p.id === id) });
  },
  back() {
    if (this.data.busy) return;
    this.setData({ selected: null, detail: null, bill: null, serviceSelected: null, error: '' });
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  switchTab(e) {
    if (this.data.busy) return;
    this.closePayment();
    this.updateTitle(e.currentTarget.dataset.tab);
    wx.vibrateShort?.({ type: 'light' });
    this.setData({
      tab: e.currentTarget.dataset.tab,
      managingHistory: false,
      selectedHistoryIds: [],
      selectedHistoryCount: 0,
      selected: null,
      detail: null,
      bill: null,
      serviceSelected: null,
      error: '',
    });
    this.refresh(true);
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  closePayment() {
    this.setData({ paymentCodeVisible: false, settlementCodeOrderId: null });
  },
  showPaymentSuccess(amount) {
    this.setData({ paidResult: { amount: this.money(amount) } });
  },
  dismissPaymentSuccess() {
    if (!this.data.paidResult?.pending) this.setData({ paidResult: null });
  },
  updateTitle(tab = this.data.tab) {
    wx.setNavigationBarTitle?.({
      title:
        {
          tables: '圣泰台球',
          services: '门店服务',
          orders: '我的订单',
          profile:
            {
              details: '个人资料',
              membership: '会员等级',
              skins: '我的皮肤',
              wallet: '圣泰钱包',
              leaderboard: '圣泰球力榜',
            }[this.data.profilePage] || '我的球友档案',
        }[tab] || '圣泰台球',
    });
  },
  async open() {
    const { selected, chosenPackage: pack, busy } = this.data;
    if (busy || selected?.status !== 'IDLE' || !pack) return;
    if (!this.data.logged) {
      this.promptForLogin();
      return;
    }
    await this.beginCheckout({
      kind: 'PLAY',
      tableId: selected.id,
      packageId: pack.id,
      title: selected.table_no + ' · ' + pack.name,
      amount: Number(pack.price),
    });
  },
  rules() {
    this.showPrompt({
      title: '开台与服务须知',
      content:
        '球桌套餐\n付款后开始计时，套餐到时自动结束球局并生成账单。\n助教服务\n按所选等级的小时价及预约小时数收费，可提前结束，也会随球局结束停止服务。\n球杆租赁\n前台验杆领取后计时，归还须前台验收。球局结束后，未归还球杆仍计入租赁。\n本局结算\n套餐费用已付，服务费用在账单中核对结算。',
      showCancel: false,
    });
  },
});
