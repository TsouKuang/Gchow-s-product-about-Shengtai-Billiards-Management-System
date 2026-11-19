const exclusiveStyles = require('./exclusive-skins');
const { resolveAppearance } = require('./appearance');
const skins = [
  { key: 'club', name: '圣泰绿' },
  { key: 'bronze', tier: '青铜', name: '暖铜' },
  { key: 'silver', tier: '白银', name: '月银' },
  { key: 'gold', tier: '黄金', name: '鎏金' },
  { key: 'diamond', tier: '钻石', name: '钻蓝' },
  { key: 'black-gold', tier: '黑金', name: '黑金' },
];

const methods = {
  editProfile() {
    const p = this.data.profile;
    this.setData({
      profilePage: 'details',
      profileMessage: '',
      profileDraft: {
        nickname: p.nickname || '',
        age: p.age == null ? '' : String(p.age),
        gender: p.gender === '未填写' ? '不显示' : p.gender || '不显示',
      },
    });
    this.updateTitle();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  profileNickname(e) {
    this.setData({ profileDraft: { ...this.data.profileDraft, nickname: e.detail.value } });
  },
  profileAge(e) {
    this.setData({ profileDraft: { ...this.data.profileDraft, age: e.detail.value } });
  },
  profileGender(e) {
    this.setData({
      profileDraft: { ...this.data.profileDraft, gender: e.currentTarget.dataset.value },
    });
  },
  openMembership() {
    this.setData({ profilePage: 'membership' });
    this.updateTitle();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  cancelProfile() {
    this.closeWalletSecurity();
    this.setData({
      profilePage: null,
      profileMessage: '',
      walletRecord: null,
      topupVisible: false,
    });
    this.applySkin();
    this.updateTitle();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  openSkins() {
    this.setData({ profilePage: 'skins', skinCategory: 'interface' });
    this.applySkin();
    this.updateTitle();
    wx.pageScrollTo?.({ scrollTop: 0, duration: 0 });
  },
  openExclusiveSkins() {
    this.openSkins();
    this.setData({ skinCategory: 'exclusive' });
  },
  chooseSkinCategory(e) {
    this.setData({ skinCategory: e.currentTarget.dataset.category });
  },
  selectExclusiveSkin(e) {
    const key = e.currentTarget.dataset.value;
    if (!this.data.customerId) return;
    if (
      key !== 'none' &&
      !this.data.exclusiveOptions.some((style) => style.key === key && !style.locked)
    )
      return;
    wx.setStorageSync('memberExclusiveSkin:' + this.data.customerId, key);
    wx.vibrateShort?.({ type: 'light' });
    this.applySkin();
  },
  applySkin() {
    const { customerId, membership } = this.data;
    const preference = customerId ? wx.getStorageSync('memberSkin:' + customerId) : 'auto';
    const rank = skins.findIndex((skin) => skin.key === membership?.badge);
    const skinOptions = skins.map((skin, index) => ({
      ...skin,
      locked: index > Math.max(0, rank),
    }));
    const chosen = skinOptions.find((skin) => skin.key === preference && !skin.locked);
    const skin = chosen || skins[Math.max(0, rank)];
    const exclusiveRank = Math.max(
      0,
      ['bronze', 'silver', 'gold', 'diamond', 'black-gold'].indexOf(membership?.badge),
    );
    const exclusiveOptions = exclusiveStyles.map((style) => ({
      ...style,
      locked: style.level > exclusiveRank,
    }));
    const exclusivePreference = customerId
      ? wx.getStorageSync('memberExclusiveSkin:' + customerId)
      : 'none';
    const exclusive = exclusiveOptions.find(
      (style) => style.key === exclusivePreference && !style.locked,
    );
    const appearance = resolveAppearance(skin.key, exclusive?.key);
    this.setData({
      skinOptions,
      skinMode: chosen?.key || 'auto',
      skinKey: skin.key,
      exclusiveOptions,
      exclusiveSkinKey: exclusive?.key || 'none',
      themeStyle: appearance.style,
      themeAccent: appearance.accent,
      themeLine: appearance.palette.line,
      heroAccent: appearance.heroAccent,
      heroTrack: appearance.heroTrack,
    });
    this.syncWalletPrivacy();
    wx.setNavigationBarColor?.({ frontColor: '#ffffff', backgroundColor: appearance.navigation });
    wx.setBackgroundColor?.({
      backgroundColorTop: appearance.background,
      backgroundColorBottom: appearance.background,
    });
  },
  selectSkin(e) {
    const key = e.currentTarget.dataset.value;
    if (
      !this.data.customerId ||
      (key !== 'auto' && !this.data.skinOptions.some((skin) => skin.key === key && !skin.locked))
    )
      return;
    wx.setStorageSync('memberSkin:' + this.data.customerId, key);
    wx.setStorageSync('memberExclusiveSkin:' + this.data.customerId, 'none');
    this.applySkin();
  },
  formatMembership(membership) {
    if (!membership?.tiers) return null;
    const badge = this.data.memberBadges.find((item) => item.name === membership.tier);
    return {
      ...membership,
      badge: badge?.image,
      icon: membership.tiers.find((tier) => tier.name === membership.tier)?.icon || badge?.image,
      nextLevel: membership.level + 1,
      levelRemainingText: this.money(membership.levelRemaining),
      tierRemainingText: this.money(membership.tierRemaining),
      spendText: this.money(membership.spend),
      tiers: [...membership.tiers].reverse().map((tier, index) => ({
        ...tier,
        badge: tier.icon || this.data.memberBadges[index].image,
        minimumText: this.money(tier.minimum),
        levels: 'LV' + (41 - index * 10) + '–' + (50 - index * 10),
      })),
    };
  },
  async saveProfile() {
    if (this.data.busy || !this.data.logged) return;
    const p = this.data.profileDraft;
    const age = p.age === '' ? null : Number(p.age);
    if (!p.nickname.trim() || (age !== null && (!Number.isInteger(age) || age < 1 || age > 120))) {
      this.setData({ profileMessage: '请填写昵称，年龄可留空或填写1至120的整数' });
      return;
    }
    this.setData({ busy: true, profileMessage: '' });
    try {
      const result = await this.request('/me/profile', 'PUT', {
        nickname: p.nickname.trim(),
        age,
        gender: p.gender,
      });
      this.setData({ profile: result.profile, profileMessage: '资料已保存' });
    } catch (e) {
      this.setData({ profileMessage: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
  chooseProfileAvatar() {
    const options = ['🎱', '⭐', '🌙', '🍀', '🏆', '圣'];
    wx.showActionSheet({
      itemList: options,
      success: ({ tapIndex }) => {
        const profileAvatar = options[tapIndex];
        wx.setStorageSync('memberAvatar:' + this.data.customerId, profileAvatar);
        this.setData({ profileAvatar });
      },
    });
  },
};
module.exports = { methods };
