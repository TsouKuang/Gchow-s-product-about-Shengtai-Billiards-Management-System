const data = {
  pendingOrderCount: 0,
  leaderPeriod: 'month',
  leaderboard: null,
  leaderLoading: false,
  leaderError: '',
};
const methods = {
  async openLeaderboard() {
    if (!this.data.logged) {
      this.promptForLogin();
      return;
    }
    this.setData({ profilePage: 'leaderboard', tab: 'profile', leaderError: '' });
    this.updateTitle();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
    await this.loadLeaderboard();
  },
  async chooseLeaderPeriod(e) {
    const period = e.currentTarget.dataset.period;
    if (this.data.leaderLoading || !['month', 'year'].includes(period)) return;
    this.setData({ leaderPeriod: period });
    await this.loadLeaderboard();
  },
  async loadLeaderboard() {
    if (this.data.leaderLoading) return;
    this.setData({ leaderLoading: true, leaderError: '' });
    try {
      const result = await this.request('/leaderboard?period=' + this.data.leaderPeriod);
      const format = (row) => ({
        ...row,
        timeText: this.formatPlayedTime(Math.floor(Number(row.seconds) / 60)),
        initial: (row.nickname || '圣').slice(0, 1),
      });
      this.setData({
        leaderboard: {
          ...result,
          rows: result.rows.map(format),
          champion: result.rows[0] ? format(result.rows[0]) : null,
          me: format(result.me),
        },
      });
    } catch (e) {
      this.setData({ leaderError: e.message });
    } finally {
      this.setData({ leaderLoading: false });
    }
  },
};
module.exports = { data, methods };
