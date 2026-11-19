const labels = {
  IDLE: '空闲可开台',
  IN_USE: '使用中',
  MAINTENANCE: '维护中',
  ACTIVE: '使用中',
  COMPLETED: '待结算',
  REQUESTED: '待前台确认',
  SETTLED: '已结清',
  CANCELLED: '已取消',
};
const settlementLabels = {
  IN_USE: '正在使用',
  PENDING_RETURN: '请到前台归还球杆',
  PAYMENT_PENDING: '待结算',
  SETTLED: '已结清',
};
module.exports = { labels, settlementLabels };
