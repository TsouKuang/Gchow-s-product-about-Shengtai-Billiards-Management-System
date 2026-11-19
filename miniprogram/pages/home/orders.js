const { labels, settlementLabels } = require('./labels');

const methods = {
  settledBillText(play, services) {
    const methods = [
      play.payment_method,
      ...services.filter((o) => o.status === 'SETTLED').map((o) => o.payment_method),
    ];
    if (methods.every((x) => x === 'MOCK')) return '收款已登记';
    if (methods.every((x) => x === 'CASH')) return '现金登记已结清';
    return methods.includes('MOCK') ? '已结清' : '已结清';
  },
  normalizePlay(o) {
    const settlement =
      o.settlement_status ||
      (o.status === 'ACTIVE'
        ? 'IN_USE'
        : Number(o.pending_cues) > 0
          ? 'PENDING_RETURN'
          : Number(o.due_amount) > 0
            ? 'PAYMENT_PENDING'
            : 'SETTLED');
    return {
      ...o,
      startText: this.date(o.started_at),
      endText: this.date(o.expected_end_at),
      finishText: this.date(o.ended_at),
      totalText: this.money(o.total_amount ?? o.amount),
      serviceAmountText: this.money(o.service_amount),
      amountText: this.money(o.amount),
      tablePaidLabel:
        o.payment_method === 'WALLET'
          ? '球桌套餐钱包已付'
          : o.payment_method === 'MOCK'
            ? '球桌套餐收款已登记'
            : o.payment_method === 'CASH'
              ? '球桌套餐现金已付'
              : '球桌套餐已付',
      dueText: this.money(o.due_amount),
      estimateDueText: this.money(o.estimated_due_amount ?? o.due_amount),
      settlement_status: settlement,
      stateText: settlementLabels[settlement] || '已结束',
      canHide: o.status !== 'ACTIVE' && settlement === 'SETTLED',
    };
  },
  normalizeService(o) {
    const item = this.data.services.find((i) => i.id === o.item_id);
    const running = o.status === 'ACTIVE';
    return {
      ...o,
      imageSrc: item?.imageSrc || '',
      createdText: this.date(o.created_at),
      startText: this.date(o.started_at),
      finishText: this.date(o.ended_at),
      amountText: this.money(o.amount),
      estimateText: this.money(o.estimated_amount),
      rateText: this.money(o.kind === 'COACH' && o.booked_hours ? Number(o.rate) * 60 : o.rate),
      paymentText:
        o.payment_method === 'WALLET'
          ? '钱包已付'
          : o.payment_method === 'MOCK'
            ? '收款已登记'
            : o.payment_method === 'CASH'
              ? '现金登记已结清'
              : '已结清',
      stateText:
        o.status === 'REQUESTED'
          ? '待前台确认'
          : running
            ? o.kind === 'CUE'
              ? '租用中 · 待归还'
              : '服务中'
            : o.status === 'SETTLED'
              ? o.payment_method === 'WALLET'
                ? '钱包结清'
                : o.payment_method === 'MOCK'
                  ? '收款已登记'
                  : o.payment_method === 'CASH'
                    ? '现金登记已结清'
                    : '已结清'
              : labels[o.status] || o.status,
      canHide: o.status === 'SETTLED' || o.status === 'CANCELLED',
      canFinish: running && o.kind === 'COACH',
    };
  },
  tick() {
    const first = this.data.orders.find((o) => o.status === 'ACTIVE');
    let active = null;
    if (first) {
      const seconds = Math.max(
          0,
          Math.floor((Date.now() - new Date(first.started_at).getTime()) / 1000),
        ),
        left = Math.ceil((new Date(first.expected_end_at).getTime() - Date.now()) / 60000);
      const coaches = this.data.serviceOrders.filter(
          (o) => o.play_order_id === first.id && o.kind === 'COACH' && o.status === 'ACTIVE',
        ).length,
        cues = this.data.serviceOrders.filter(
          (o) =>
            o.play_order_id === first.id &&
            o.kind === 'CUE' &&
            ['REQUESTED', 'ACTIVE'].includes(o.status),
        ).length;
      active = {
        ...first,
        elapsed:
          String(Math.floor(seconds / 3600)).padStart(2, '0') +
          ':' +
          String(Math.floor((seconds % 3600) / 60)).padStart(2, '0') +
          ':' +
          String(seconds % 60).padStart(2, '0'),
        remaining: left > 0 ? '还可使用 ' + left + ' 分钟' : '套餐已到时，正在生成账单',
        progress: Math.min(100, Math.floor((seconds / (first.minutes * 60)) * 100)),
        serviceSummary: '助教服务中 ' + coaches + ' 位 · 球杆申请/租用 ' + cues + ' 根',
      };
    }
    this.setData({ active });
  },
  setOrderFilter(e) {
    if (this.data.busy) return;
    this.setData({
      orderFilter: e.currentTarget.dataset.value,
      managingHistory: false,
      selectedHistoryIds: [],
    });
    this.filterData();
  },
  openOrders(e) {
    const filter =
      e?.currentTarget?.dataset?.filter ||
      (this.data.orders.some((o) => o.status === 'ACTIVE')
        ? '进行中'
        : this.data.orders.some((o) => o.settlement_status !== 'SETTLED')
          ? '待结算'
          : '历史');
    this.closePayment();
    this.updateTitle('orders');
    this.setData({
      tab: 'orders',
      orderKind: 'PLAY',
      orderFilter: filter,
      managingHistory: false,
      selectedHistoryIds: [],
      selectedHistoryCount: 0,
      detail: null,
      bill: null,
      selected: null,
      serviceSelected: null,
    });
    this.filterData();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  async showOrder(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (!id || this.data.busy) return;
    if (
      this.data.managingHistory &&
      this.data.orderFilter === '历史' &&
      this.data.orderKind === 'PLAY' &&
      !this.data.detail
    ) {
      this.toggleHistory(e);
      return;
    }
    this.updateTitle('orders');
    this.setData({
      tab: 'orders',
      selected: null,
      serviceSelected: null,
      detail: this.data.orders.find((o) => o.id === id) || { id },
      bill: null,
      billLoading: true,
      settlementCodeOrderId: null,
      error: '',
    });
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
    try {
      await this.loadBill(id);
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ billLoading: false });
    }
  },
  async loadBill(id) {
    const raw = await this.request('/orders/' + id + '/bill');
    if (this.data.detail?.id !== id) return raw;
    const play = this.normalizePlay(raw.playOrder),
      services = raw.services.map((o) => this.normalizeService(o));
    const bill = {
      ...raw,
      services,
      totalText: this.money(raw.totalAmount),
      paidText: this.money(raw.paidAmount),
      tablePaidText: this.money(raw.tablePaid),
      serviceTotalText: this.money(raw.serviceTotal),
      estimateTotalText: this.money(raw.estimatedServiceTotal ?? raw.serviceTotal),
      unpaidText: this.money(raw.unpaidAmount),
      estimateDueText: this.money(raw.estimatedUnpaidAmount ?? raw.unpaidAmount),
      stateText:
        raw.settlementStatus === 'SETTLED'
          ? this.settledBillText(play, services)
          : settlementLabels[raw.settlementStatus] || '已结束',
      canPay:
        play.status !== 'ACTIVE' &&
        raw.settlementStatus === 'PAYMENT_PENDING' &&
        Number(raw.unpaidAmount) > 0,
    };
    this.setData({
      detail: { ...play, settlement_status: raw.settlementStatus, stateText: bill.stateText },
      bill,
    });
    return raw;
  },
  async refreshBill() {
    const id = this.data.detail?.id;
    if (this.data.busy || !id) return;
    this.setData({ busy: true, error: '' });
    try {
      await this.refresh(true);
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
  async finish(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (this.data.busy) return;
    let quote;
    try {
      quote = await this.request('/orders/' + id + '/bill');
    } catch (e) {
      this.setData({ error: e.message });
      return;
    }
    const r = await new Promise((resolve) =>
      this.showPrompt({
        title: '结束本局？',
        content: quote.pendingCueCount
          ? '请到前台归还 ' + quote.pendingCueCount + ' 根球杆。'
          : '结束后可查看本局账单。',
        confirmText: '结束计费',
        success: resolve,
      }),
    );
    if (!r.confirm) return;
    this.setData({
      busy: true,
      error: '',
      tab: 'orders',
      detail: this.data.orders.find((o) => o.id === id) || { id },
      bill: null,
    });
    try {
      await this.request('/orders/' + id + '/finish', 'POST', {});
      await this.refresh(true);
      wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
      wx.showToast({ title: '已结束，请查看本局账单', icon: 'none' });
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
  async settle(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (this.data.busy) return;
    try {
      const bill = await this.request('/orders/' + id + '/bill');
      if (bill.settlementStatus !== 'PAYMENT_PENDING') {
        this.setData({ error: '当前账单状态已改变，请刷新后查看' });
        return;
      }
      await this.beginCheckout({
        kind: 'SETTLE',
        playOrderId: id,
        title: '本局服务费用',
        amount: Number(bill.unpaidAmount),
      });
    } catch (e) {
      this.setData({ error: e.message });
    }
  },
  async hideRecord(e) {
    const id = Number(e.currentTarget.dataset.id),
      kind = e.currentTarget.dataset.kind;
    if (this.data.busy) return;
    const r = await new Promise((resolve) =>
      this.showPrompt({
        title: '移除这条历史记录？',
        content: '仅从你的历史列表隐藏，门店仍保留交易记录。进行中、待归还或待付记录不能移除。',
        confirmText: '移除记录',
        success: resolve,
      }),
    );
    if (!r.confirm) return;
    await this.hideHistory({ kind, ids: [id] });
  },
  toggleManageHistory() {
    if (this.data.busy) return;
    this.setData({ managingHistory: !this.data.managingHistory, selectedHistoryIds: [] });
    this.filterData();
  },
  toggleHistory(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (this.data.busy || !this.data.managingHistory || this.data.orderFilter !== '历史') return;
    const rows =
      this.data.orderKind === 'PLAY' ? this.data.visibleOrders : this.data.visibleServiceOrders;
    if (!rows.some((o) => o.id === id && o.canHide)) return;
    const selected = this.data.selectedHistoryIds.includes(id)
      ? this.data.selectedHistoryIds.filter((x) => x !== id)
      : [...this.data.selectedHistoryIds, id];
    this.setData({ selectedHistoryIds: selected });
    this.filterData();
  },
  toggleSelectAllHistory() {
    if (this.data.busy || !this.data.managingHistory) return;
    const rows =
      this.data.orderKind === 'PLAY' ? this.data.visibleOrders : this.data.visibleServiceOrders;
    this.setData({
      selectedHistoryIds: this.data.allHistorySelected
        ? []
        : rows.filter((o) => o.canHide).map((o) => o.id),
    });
    this.filterData();
  },
  async removeSelectedHistory() {
    if (this.data.busy || !this.data.selectedHistoryCount) return;
    const kind = this.data.orderKind,
      rows = kind === 'PLAY' ? this.data.visibleOrders : this.data.visibleServiceOrders,
      ids = rows.filter((o) => o.historySelected && o.canHide).map((o) => o.id);
    const r = await new Promise((resolve) =>
      this.showPrompt({
        title: '移除选中的历史记录？',
        content: '将从你的列表隐藏 ' + ids.length + ' 条已结束记录，门店仍保留交易记录。',
        confirmText: '移除所选',
        success: resolve,
      }),
    );
    if (r.confirm) await this.hideHistory({ kind, ids });
  },
  async clearHistory() {
    if (this.data.busy) return;
    const kind = this.data.orderKind;
    const r = await new Promise((resolve) =>
      this.showPrompt({
        title: '清空这一分类的历史？',
        content:
          '将隐藏本分类已取消或已结清的记录；进行中、待归还和待付记录会保留。门店账务记录不会删除。',
        confirmText: '清空历史',
        success: resolve,
      }),
    );
    if (!r.confirm) return;
    await this.hideHistory({ kind, all: true });
  },
  async hideHistory(payload) {
    this.setData({ busy: true, error: '' });
    try {
      await this.request('/history/hide', 'POST', payload);
      this.setData({ managingHistory: false, selectedHistoryIds: [] });
      if (
        payload.kind === 'PLAY' &&
        this.data.detail &&
        (payload.all || payload.ids?.includes(this.data.detail.id))
      )
        this.setData({ detail: null, bill: null });
      await this.refresh(true);
      wx.showToast({ title: '历史记录已整理', icon: 'none' });
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
};
module.exports = { methods };
