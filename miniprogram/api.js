function networkError(error, url) {
  const detail = String((error && error.errMsg) || '未知网络错误').slice(0, 180);
  let reason = '暂时连不上门店';
  if (
    /not in domain list|domain.*(invalid|illegal|check)|合法域名|域名.*(校验|不合法)/i.test(detail)
  ) {
    reason = '微信未放行当前请求域名';
  } else if (/timeout|timed out|超时/i.test(detail)) {
    reason = '连接门店服务器超时';
  } else if (/ssl|tls|certificate|cert|证书/i.test(detail)) {
    reason = '门店服务器 HTTPS 连接失败';
  } else if (
    /connection.reset|err_connection_reset|connection refused|connect failed/i.test(detail)
  ) {
    reason = '门店服务器拒绝或重置了连接';
  }
  return Error(`${reason}。请求：${url}；微信错误：${detail}`);
}

function requestApi({ baseUrl, path, method = 'GET', data, token, onUnauthorized }) {
  if (!baseUrl) return Promise.reject(Error('门店线上服务尚未配置，请联系前台'));
  const url = baseUrl + path;
  return new Promise((resolve, reject) =>
    wx.request({
      url,
      method,
      data,
      timeout: 10000,
      header: { Authorization: 'Bearer ' + (token || '') },
      success: (response) => {
        if (response.statusCode === 401) onUnauthorized?.();
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data);
        } else {
          const message = response.data && response.data.message;
          reject(Error(message || `门店接口返回 HTTP ${response.statusCode}：${url}`));
        }
      },
      fail: (error) => {
        console.error('门店接口请求失败', { url, errMsg: error && error.errMsg });
        reject(networkError(error, url));
      },
    }),
  );
}

module.exports = { networkError, requestApi };
