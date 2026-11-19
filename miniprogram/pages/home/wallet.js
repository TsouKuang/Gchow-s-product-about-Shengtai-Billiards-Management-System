const data = {
  wallet: null,
  walletBalance: '0.00',
  walletTransactions: [],
  walletVisibleTransactions: [],
  walletLoading: false,
  walletHidden: true,
  walletFilter: 'ALL',
  walletMonthIncome: '0.00',
  walletMonthExpense: '0.00',
  walletNotice: '',
  walletError: '',
  walletSecurityPage: '',
  walletLoginPassword: '',
  walletRecord: null,
  checkout: null,
  scanStage: '',
  checkoutMethod: 'WALLET',
  pinSheet: null,
  pinLength: 0,
  pinDots: [0, 1, 2, 3, 4, 5],
  pinKeys: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'delete'],
  topupAmount: '100',
  topupChannel: 'WECHAT',
  topupChannelText: '微信',
  topupCustom: false,
  topupOptions: [100, 200, 500, 1000],
  topupVisible: false,
  topupStage: 'form',
  topupQuote: '100.00',
};

const methods = {
  applyWallet(summary) {
    this.syncWalletPrivacy();
    this.walletRequestVersion = (this.walletRequestVersion || 0) + 1;
    const wallet = { ...this.data.wallet, ...summary };
    const transactions = (wallet.transactions || []).map((item) => ({
      ...item,
      amountText: (Number(item.amount) > 0 ? '+' : '−') + this.money(Math.abs(Number(item.amount))),
      balanceText: this.money(item.balance_after),
      dateText: this.date(item.created_at),
      channelText:
        item.kind === 'TOPUP'
          ? item.channel === 'ALIPAY'
            ? '支付宝充值'
            : '微信充值'
          : '钱包支付',
    }));
    this.setData({
      wallet,
      walletBalance: this.money(wallet.balance),
      walletMonthIncome: this.money(wallet.monthIncome),
      walletMonthExpense: this.money(wallet.monthExpense),
      walletTransactions: transactions,
    });
    this.filterWalletRecords();
  },
  async loadWallet() {
    const version = (this.walletRequestVersion || 0) + 1;
    this.walletRequestVersion = version;
    const token = this.token;
    const wallet = await this.request('/wallet');
    if (version === this.walletRequestVersion && token === this.token) this.applyWallet(wallet);
    return wallet;
  },
  async openWallet() {
    if (!this.data.logged) {
      this.promptForLogin();
      return;
    }
    if (this.data.busy) return;
    this.walletIntent = null;
    this.setData({
      profilePage: 'wallet',
      walletLoading: true,
      walletError: '',
      walletNotice: '',
      tab: 'profile',
    });
    this.updateTitle();
    this.applySkin();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
    try {
      await this.loadWallet();
    } catch (e) {
      this.setData({ walletError: e.message });
    } finally {
      this.setData({ walletLoading: false });
    }
  },
  syncWalletPrivacy() {
    const account = this.data.customerId;
    if (this.walletPrivacyAccount === account) return;
    this.walletPrivacyAccount = account;
    this.setData({
      walletHidden: account ? wx.getStorageSync('memberWalletHidden:' + account) === true : true,
    });
  },
  toggleWalletBalance() {
    const hidden = !this.data.walletHidden;
    if (this.data.customerId)
      wx.setStorageSync('memberWalletHidden:' + this.data.customerId, hidden);
    this.setData({ walletHidden: hidden });
  },
  chooseWalletFilter(e) {
    this.setData({ walletFilter: e.currentTarget.dataset.filter });
    this.filterWalletRecords();
  },
  filterWalletRecords() {
    this.setData({
      walletVisibleTransactions: this.data.walletTransactions.filter(
        (item) => this.data.walletFilter === 'ALL' || item.kind === this.data.walletFilter,
      ),
    });
  },
  openWalletRecord(e) {
    const record = this.data.walletTransactions.find(
      (item) => item.id === Number(e.currentTarget.dataset.id),
    );
    if (record) this.setData({ walletRecord: record });
  },
  closeWalletRecord() {
    this.setData({ walletRecord: null });
  },
  openWalletSecurity() {
    if (!this.data.busy)
      this.setData({ walletSecurityPage: 'menu', walletError: '', walletNotice: '' });
  },
  closeWalletSecurity() {
    if (this.data.busy) return;
    this.walletRecoveryPassword = '';
    this.setData({ walletSecurityPage: '', walletLoginPassword: '', walletError: '' });
  },
  startWalletPinChange() {
    if (this.data.busy) return;
    if (!this.data.wallet?.pinSet) {
      this.openWalletPin();
      return;
    }
    this.pinAction = 'change';
    this.walletOldPin = '';
    this.pinDigits = '';
    this.setData({
      walletSecurityPage: '',
      pinLength: 0,
      walletError: '',
      pinSheet: { mode: 'old', title: '修改支付密码', caption: '输入当前的六位支付密码' },
    });
  },
  startWalletRecovery() {
    if (this.data.busy) return;
    this.setData({ walletSecurityPage: 'recover', walletLoginPassword: '', walletError: '' });
  },
  walletResetPassword(e) {
    this.setData({ walletLoginPassword: e.detail.value, walletError: '' });
  },
  async verifyWalletIdentity() {
    if (this.data.busy) return;
    const password = this.data.walletLoginPassword;
    if (!password) {
      this.setData({ walletError: '请输入账号登录密码' });
      return;
    }
    this.setData({ busy: true, walletError: '' });
    try {
      await this.request('/wallet/pin/recovery/verify', 'POST', { loginPassword: password });
      this.walletRecoveryPassword = password;
      this.pinAction = 'recover';
      this.pinDigits = '';
      this.firstPin = '';
      this.setData({
        walletSecurityPage: '',
        walletLoginPassword: '',
        pinLength: 0,
        pinSheet: { mode: 'setup', title: '重设支付密码', caption: '设置新的六位支付密码' },
      });
    } catch (e) {
      this.setData({ walletError: e.message });
      await this.loadWallet().catch(() => {});
    } finally {
      this.setData({ busy: false });
    }
  },
  async beginCheckout(intent) {
    if (!this.data.logged) {
      this.promptForLogin();
      return;
    }
    if (this.data.busy) return;
    try {
      if (
        intent.kind === 'PLAY' &&
        (await this.request('/orders')).some((o) => o.status === 'ACTIVE')
      ) {
        this.showPrompt({
          title: '一次只能开一个台',
          content: '请先结束当前球局，再选择其他球桌。',
          showCancel: false,
        });
        return;
      }
      await this.loadWallet();
      const scope = JSON.stringify(intent);
      if (this.walletIntentScope !== scope) {
        this.walletIntentKey = 'wallet-' + Date.now() + '-' + Math.random().toString(16).slice(2);
        this.walletIntentScope = scope;
      }
      this.walletIntent = intent;
      this.setData({
        checkout: { title: intent.title, amount: this.money(intent.amount) },
        checkoutMethod: this.data.wallet?.pinSet ? 'WALLET' : 'SCAN',
        walletError: '',
        bookingSheet: null,
      });
    } catch (e) {
      this.setData({ error: e.message });
    }
  },
  chooseCheckoutMethod(e) {
    if (!this.data.busy)
      this.setData({ checkoutMethod: e.currentTarget.dataset.value, walletError: '' });
  },
  closeCheckout() {
    if (this.data.busy) return;
    clearTimeout(this.scanTimer);
    this.pinDigits = '';
    this.setData({ checkout: null, scanStage: '', pinSheet: null, pinLength: 0, walletError: '' });
  },
  async confirmCheckout() {
    if (this.data.busy || !this.walletIntent) return;
    if (this.data.checkoutMethod === 'WALLET') {
      if (!this.data.wallet?.pinSet) {
        this.openWalletPin();
        return;
      }
      if (this.data.wallet.locked) {
        this.setData({ walletError: '支付密码已锁定15分钟，请稍后重试' });
        return;
      }
      if (Number(this.data.wallet.balance) < Number(this.walletIntent.amount)) {
        this.setData({ walletError: '余额不足，请先前往圣泰钱包充值，或选择其他方式' });
        return;
      }
      this.openPayPin();
      return;
    }
    this.setData({ scanStage: 'code' });
    clearTimeout(this.scanTimer);
    if (this.data.isDemo) {
      this.scanTimer = setTimeout(() => {
        if (this.data.scanStage !== 'code' || !this.walletIntent) return;
        this.setData({ scanStage: 'focus' });
        this.scanTimer = setTimeout(() => {
          if (this.data.scanStage === 'focus' && this.walletIntent) this.openScanPin();
        }, 600);
      }, 900);
    }
  },
  openScanPin() {
    if (!this.walletIntent || this.data.busy) return;
    this.pinDigits = '';
    if (!this.data.wallet?.pinSet) {
      this.setData({ scanStage: '' });
      this.openWalletPin();
      return;
    }
    this.setData({
      scanStage: 'pin',
      pinLength: 0,
      walletError: '',
      pinSheet: {
        mode: 'scan',
        title: '扫码支付确认',
        caption: this.walletIntent.title,
        amount: this.money(this.walletIntent.amount),
      },
    });
  },
  openPayPin() {
    this.pinDigits = '';
    this.setData({
      scanStage: 'pin',
      pinSheet: {
        mode: 'pay',
        title: '请输入支付密码',
        caption: this.walletIntent.title,
        amount: this.money(this.walletIntent.amount),
      },
      pinLength: 0,
      walletError: '',
    });
  },
  openWalletPin() {
    if (this.data.busy) return;
    this.pinAction = 'setup';
    this.pinReturnToCheckout = !!this.data.checkout;
    this.pinDigits = '';
    this.firstPin = '';
    this.setData({
      walletSecurityPage: '',
      pinLength: 0,
      walletError: '',
      pinSheet: { mode: 'setup', title: '设置支付密码', caption: '设置六位数字，用于圣泰钱包付款' },
    });
  },
  closePin() {
    if (this.data.busy) return;
    if (['scan', 'pay'].includes(this.data.pinSheet?.mode)) {
      this.closeCheckout();
      return;
    }
    this.pinDigits = '';
    this.firstPin = '';
    this.walletOldPin = '';
    this.walletRecoveryPassword = '';
    this.pinReturnToCheckout = false;
    this.setData({ pinSheet: null, pinLength: 0, walletError: '' });
  },
  async pressPinKey(e) {
    if (this.data.busy || !this.data.pinSheet) return;
    const key = e.currentTarget.dataset.key;
    if (!key) return;
    if (key === 'delete') {
      this.pinDigits = (this.pinDigits || '').slice(0, -1);
      this.setData({ pinLength: this.pinDigits.length, walletError: '' });
      return;
    }
    if (!/^[0-9]$/.test(key) || (this.pinDigits || '').length >= 6) return;
    this.pinDigits = (this.pinDigits || '') + key;
    this.setData({ pinLength: this.pinDigits.length, walletError: '' });
    if (this.pinDigits.length < 6) return;
    const pin = this.pinDigits;
    this.pinDigits = '';
    const mode = this.data.pinSheet.mode;
    if (mode === 'old') {
      this.setData({ busy: true });
      try {
        await this.request('/wallet/pin/verify', 'POST', { pin });
        this.walletOldPin = pin;
        this.setData({
          pinLength: 0,
          pinSheet: { mode: 'setup', title: '设置新支付密码', caption: '输入新的六位支付密码' },
        });
      } catch (e) {
        this.setData({ pinLength: 0, walletError: e.message });
        await this.loadWallet().catch(() => {});
      } finally {
        this.setData({ busy: false });
      }
      return;
    }
    if (mode === 'setup') {
      this.firstPin = pin;
      this.setData({
        pinSheet: { mode: 'confirm', title: '确认支付密码', caption: '再次输入，确保两次密码一致' },
        pinLength: 0,
      });
      return;
    }
    if (mode === 'confirm') {
      if (pin !== this.firstPin) {
        this.firstPin = '';
        this.setData({
          pinSheet: { mode: 'setup', title: '重新设置支付密码', caption: '请输入六位数字' },
          pinLength: 0,
          walletError: '两次密码不一致，请重新输入',
        });
        return;
      }
      await this.saveWalletPin(pin);
      return;
    }
    await this.executeCheckout(pin, mode === 'scan');
  },
  async saveWalletPin(pin) {
    this.setData({ busy: true, walletError: '' });
    try {
      let path = '/wallet/pin',
        body = { pin };
      if (this.pinAction === 'change') {
        path = '/wallet/pin/change';
        body = { oldPin: this.walletOldPin, newPin: pin };
      }
      if (this.pinAction === 'recover') {
        path = '/wallet/pin/recover';
        body = { loginPassword: this.walletRecoveryPassword, newPin: pin };
      }
      const wallet = await this.request(path, 'PUT', body);
      this.applyWallet(wallet);
      this.setData({
        pinSheet: null,
        pinLength: 0,
        walletNotice: '支付密码已更新',
        walletLoginPassword: '',
      });
      this.firstPin = '';
      this.walletOldPin = '';
      this.walletRecoveryPassword = '';
      wx.showToast?.({ title: '支付密码已设置', icon: 'success' });
      const resume = this.pinReturnToCheckout && this.walletIntent;
      this.pinReturnToCheckout = false;
      this.setData({ busy: false });
      if (resume) {
        if (this.data.checkoutMethod === 'SCAN') this.openScanPin();
        else this.openPayPin();
      }
    } catch (e) {
      this.firstPin = '';
      this.setData({
        pinLength: 0,
        walletError: e.message,
        pinSheet: { mode: 'setup', title: '重新设置支付密码', caption: '请输入六位数字' },
      });
    } finally {
      this.setData({ busy: false });
    }
  },
  async executeCheckout(pin, scan = false) {
    const intent = this.walletIntent;
    if (!intent || !pin || this.data.busy) return;
    this.setData({ busy: true, walletError: '', scanStage: 'pin' });
    let result;
    try {
      result = await this.request(scan ? '/scan/pay' : '/wallet/pay', 'POST', {
        ...intent,
        requestKey: this.walletIntentKey,
        pin,
      });
    } catch (e) {
      this.pinDigits = '';
      this.setData({ busy: false, scanStage: 'pin', walletError: e.message, pinLength: 0 });
      await this.loadWallet().catch(() => {});
      return;
    }
    if (result.wallet) this.applyWallet(result.wallet);
    this.setData({ pinSheet: null, pinLength: 0, scanStage: 'processing' });
    await new Promise((resolve) => setTimeout(resolve, 1500));
    this.walletIntentScope = null;
    this.walletIntent = null;
    this.walletIntentKey = null;
    this.pinDigits = '';
    this.setData({
      scanStage: '',
      checkout: null,
      pinSheet: null,
      pinLength: 0,
      selected: null,
      serviceSelected: null,
      bookingSheet: null,
      tab: 'orders',
      orderKind: 'PLAY',
      orderFilter: '进行中',
    });
    this.updateTitle('orders');
    this.showPaymentSuccess(result.amount ?? intent.amount);
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
    try {
      await this.refresh(true);
      await this.loadWallet();
    } catch (e) {
      this.setData({ walletNotice: '支付已完成，余额与记录将在网络恢复后同步' });
    } finally {
      this.setData({ busy: false });
    }
  },
  showTopup() {
    if (this.data.busy) return;
    this.setData({ topupVisible: true, topupStage: 'form', walletError: '', walletNotice: '' });
  },
  closeTopup() {
    if (this.data.busy) return;
    this.setData({ topupVisible: false, walletError: '' });
  },
  chooseTopupAmount(e) {
    if (!this.data.busy)
      this.setData({
        topupAmount: String(e.currentTarget.dataset.amount),
        topupCustom: false,
        walletError: '',
      });
  },
  inputTopupAmount(e) {
    if (!this.data.busy)
      this.setData({ topupAmount: e.detail.value, topupCustom: true, walletError: '' });
  },
  chooseCustomTopup() {
    if (!this.data.busy) this.setData({ topupCustom: true, topupAmount: '', walletError: '' });
  },
  chooseTopupChannel(e) {
    const channel = e.currentTarget.dataset.channel;
    if (this.data.busy || !['WECHAT', 'ALIPAY'].includes(channel)) return;
    this.setData({
      topupChannel: channel,
      topupChannelText: channel === 'ALIPAY' ? '支付宝' : '微信',
      walletError: '',
    });
  },
  reviewTopup() {
    if (this.data.busy) return;
    if (!this.data.wallet?.topupEnabled) {
      this.setData({ walletError: '请到门店前台办理充值' });
      return;
    }
    const text = String(this.data.topupAmount).trim();
    if (!/^\d+(\.\d{1,2})?$/.test(text) || Number(text) < 1 || Number(text) > 5000) {
      this.setData({ walletError: '请输入1至5000元，最多两位小数' });
      return;
    }
    this.setData({ topupQuote: this.money(Number(text)), topupStage: 'review', walletError: '' });
  },
  editTopup() {
    if (!this.data.busy) this.setData({ topupStage: 'form', walletError: '' });
  },
  async confirmTopup() {
    if (this.data.busy || this.data.topupStage !== 'review') return;
    const scope = this.data.topupQuote + ':' + this.data.topupChannel;
    if (this.topupRequest?.scope !== scope)
      this.topupRequest = {
        scope,
        key: 'topup-' + Date.now() + '-' + Math.random().toString(16).slice(2),
      };
    this.setData({ busy: true, topupStage: 'processing', walletError: '' });
    try {
      const [wallet] = await Promise.all([
        this.request('/wallet/topup', 'POST', {
          amount: Number(this.data.topupQuote),
          channel: this.data.topupChannel,
          requestKey: this.topupRequest.key,
        }),
        new Promise((resolve) => setTimeout(resolve, 900)),
      ]);
      this.applyWallet(wallet);
      this.topupRequest = null;
      this.setData({ topupStage: 'success', walletNotice: '充值已到账' });
    } catch (e) {
      this.setData({ topupStage: 'review', walletError: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
};
module.exports = { data, methods };
