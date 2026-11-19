const methods = {
  filterServices() {
    const category = this.data.services.filter((i) => i.kind === this.data.serviceKind);
    const brands = ['全部品牌', ...new Set(category.map((i) => i.brand))];
    const brand = brands.includes(this.data.serviceBrand) ? this.data.serviceBrand : '全部品牌';
    this.setData({
      serviceBrands: brands,
      serviceBrand: brand,
      visibleServices: category.filter((i) =>
        this.data.serviceKind === 'CUE'
          ? brand === '全部品牌' || i.brand === brand
          : (this.data.serviceGender === '全部' || i.gender === this.data.serviceGender) &&
            (!this.data.serviceGrade || i.star_level === this.data.serviceGrade),
      ),
    });
  },
  setServiceGender(e) {
    this.setData({ serviceGender: e.currentTarget.dataset.value });
    this.filterServices();
  },
  setServiceGrade(e) {
    this.setData({ serviceGrade: Number(e.currentTarget.dataset.value) });
    this.filterServices();
  },
  showService(e) {
    this.setData({
      serviceSelected:
        this.data.services.find((i) => i.id === Number(e.currentTarget.dataset.id)) || null,
      error: '',
    });
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  setServiceKind(e) {
    this.setData({ serviceKind: e.currentTarget.dataset.value, serviceBrand: '全部品牌' });
    this.filterServices();
  },
  setServiceBrand(e) {
    this.setData({ serviceBrand: e.currentTarget.dataset.value });
    this.filterServices();
  },
  async pickPlayOrder() {
    const choices = this.data.orders.filter((o) => o.status === 'ACTIVE');
    if (!choices.length) {
      const result = await new Promise((resolve) =>
        this.showPrompt({
          title: '请先开台',
          content:
            '台球助教和球杆租赁需要关联正在使用的球桌。可先自助开台，或在前台开台时提供' +
            (this.data.customerId ? '顾客 ID：' + this.data.customerCode : '本人顾客编号') +
            '，由前台关联后再选服务。',
          confirmText: '去选球桌',
          success: resolve,
        }),
      );
      if (result.confirm) {
        this.setData({ tab: 'tables', serviceSelected: null, detail: null, bill: null });
        wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
      }
      return null;
    }
    if (choices.length === 1) return choices[0];
    let offset = 0;
    while (true) {
      const rest = choices.length - offset,
        showMore = rest > 6,
        page = choices.slice(offset, offset + (showMore ? 5 : 6));
      const names = page.map((o) => o.table_no + ' · ' + o.package_name);
      if (showMore) names.push('查看其他正在使用的球桌');
      const pick = await new Promise((resolve) =>
        wx.showActionSheet({
          itemList: names,
          success: (r) => resolve(r.tapIndex),
          fail: () => resolve(-1),
        }),
      );
      if (pick < 0) return null;
      if (showMore && pick === 5) {
        offset += 5;
        continue;
      }
      return page[pick] || null;
    }
  },
  async bookService(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (this.data.busy) return;
    if (!this.data.logged) {
      this.promptForLogin();
      return;
    }
    await this.refresh(true);
    const item = this.data.services.find((i) => i.id === id);
    if (!item || !item.enabled || item.available < 1) {
      wx.showToast({ title: '该服务暂不可选，请刷新后重试', icon: 'none' });
      return;
    }
    const plays = this.data.orders.filter((o) => o.status === 'ACTIVE');
    if (!plays.length) {
      await this.pickPlayOrder();
      return;
    }
    const hourly = Number(item.rate) * (item.kind === 'COACH' ? 60 : 1);
    this.setData({
      bookingSheet: {
        item,
        plays,
        playId: plays[0].id,
        hours: 1,
        hourly,
        unitText: this.money(hourly),
        total: this.money(hourly),
        options: [1, 2, 3, 4, 5, 6].map((hours) => ({ hours, amount: this.money(hourly * hours) })),
      },
      error: '',
    });
  },
  closeBookingSheet() {
    if (!this.data.busy) this.setData({ bookingSheet: null });
  },
  stopBookingBubble() {},
  chooseBookingPlay(e) {
    if (this.data.busy || !this.data.bookingSheet) return;
    this.setData({ 'bookingSheet.playId': Number(e.currentTarget.dataset.id) });
  },
  chooseBookingHours(e) {
    this.updateBookingHours(Number(e.currentTarget.dataset.hours));
  },
  stepBookingHours(e) {
    if (!this.data.bookingSheet) return;
    this.updateBookingHours(this.data.bookingSheet.hours + Number(e.currentTarget.dataset.step));
  },
  updateBookingHours(hours) {
    const sheet = this.data.bookingSheet;
    if (!sheet || this.data.busy || !Number.isInteger(hours) || hours < 1 || hours > 12) return;
    this.setData({
      'bookingSheet.hours': hours,
      'bookingSheet.total': this.money(sheet.hourly * hours),
    });
  },
  async confirmServiceBooking() {
    const sheet = this.data.bookingSheet;
    if (this.data.busy || !sheet) return;
    await this.beginCheckout({
      kind: 'SERVICE',
      itemId: sheet.item.id,
      playOrderId: sheet.playId,
      hours: sheet.hours,
      title: sheet.item.name + ' · ' + sheet.hours + '小时',
      amount: Number(sheet.total),
    });
  },
  async cancelService(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (this.data.busy) return;
    const result = await new Promise((resolve) =>
      this.showPrompt({
        title: '取消球杆租赁申请？',
        content: '仅尚未确认领取的申请可以取消，取消后不产生租赁费用。',
        success: resolve,
      }),
    );
    if (!result.confirm) return;
    await this.serviceAction(id, 'cancel');
  },
  async finishCoach(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (this.data.busy) return;
    const item =
      this.data.serviceOrders.find((o) => o.id === id) ||
      this.data.bill?.services.find((o) => o.id === id);
    const result = await new Promise((resolve) =>
      this.showPrompt({
        title: '结束助教服务？',
        content: item?.mock_prepaid
          ? '本次收款已登记，结束服务后可查看账单。'
          : item?.booked_hours
            ? '按预约的 ' + item.booked_hours + ' 小时收费。'
            : '费用按预约小时数计入本局账单。',
        confirmText: '结束计费',
        success: resolve,
      }),
    );
    if (!result.confirm) return;
    await this.serviceAction(id, 'finish');
  },
  async serviceAction(id, action) {
    this.setData({ busy: true, error: '' });
    try {
      const updated = await this.request('/service-orders/' + id + '/' + action, 'POST', {});
      await this.refresh(true);
      wx.showToast({
        title:
          action === 'finish'
            ? updated?.status === 'SETTLED'
              ? '服务已结束'
              : '已结束，费用待结算'
            : '已取消',
        icon: 'none',
      });
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
};
module.exports = { methods };
