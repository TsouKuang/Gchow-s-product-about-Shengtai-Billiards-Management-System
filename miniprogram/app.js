// All builds share the same cloud API; authentication remains server-side.
App({
  globalData: {
    baseUrl: 'https://www.yongzhoushengtai.homes/api',
    loginMode: 'member',
    demoCheckout: true,
  },
});
