package com.billiard;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Session billing and personal list visibility. All mutations lock table -> play -> item -> service. */
@Service
public class Ledger {
 final JdbcTemplate db;
 @Value("${app.demo-enabled}") boolean demo;
 Ledger(JdbcTemplate db){this.db=db;}
 Map<String,Object> row(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw new IllegalArgumentException("记录不存在");return rows.get(0);}
 static long number(Object n){return ((Number)n).longValue();}
 static BigDecimal decimal(Object n){return n==null?new BigDecimal("0.00"):new BigDecimal(n.toString()).setScale(2);}
 void log(long uid,String action,long id){db.update("insert into operation_log(user_id,action,business_id,created_at) values(?,?,?,?)",uid,action,id,Commerce.now());}
 void owner(Map<String,Object> record,long uid,boolean admin){if(!admin&&number(record.get("user_id"))!=uid)throw new IllegalArgumentException("无权操作他人订单");}

 Map<String,Object> lockPlay(long uid,boolean admin,long id){
  var initial=row("select * from play_order where id=?",id);owner(initial,uid,admin);
  row("select * from billiard_table where id=? for update",initial.get("table_id"));
  var play=row("select * from play_order where id=? for update",id);owner(play,uid,admin);return play;
 }
 Map<String,Object> lockService(long uid,boolean admin,long id){
  var initial=row("select * from service_order where id=?",id);owner(initial,uid,admin);
  if(initial.get("play_order_id")!=null)lockPlay(uid,admin,number(initial.get("play_order_id")));
  row("select * from service_item where id=? for update",initial.get("item_id"));
  var service=row("select * from service_order where id=? for update",id);owner(service,uid,admin);return service;
 }
 void completeService(long uid,Map<String,Object> order,Timestamp end){
  if(!"ACTIVE".equals(order.get("status")))return;
  int minutes=Commerce.minutes(((Timestamp)order.get("started_at")).toInstant(),end.toInstant());
  db.update("update service_order set status=?,ended_at=?,billed_minutes=?,amount=? where id=?",order.get("settled_at")==null?"COMPLETED":"SETTLED",end,minutes,Commerce.bookedAmount(order,minutes),order.get("id"));
  log(uid,"SERVICE_FINISH",number(order.get("id")));
 }
 /** Caller already holds table + play lock. */
 void endRelated(long uid,long playId,Timestamp end){
  var rows=db.queryForList("select * from service_order where play_order_id=? and status in ('REQUESTED','ACTIVE') order by item_id,id",playId);
  for(var initial:rows){
   row("select * from service_item where id=? for update",initial.get("item_id"));
   var service=row("select * from service_order where id=? for update",initial.get("id"));
   if("COACH".equals(service.get("kind"))&&"ACTIVE".equals(service.get("status")))completeService(uid,service,end);
   else if("REQUESTED".equals(service.get("status"))){db.update("update service_order set status='CANCELLED' where id=?",service.get("id"));log(uid,"SERVICE_CANCEL",number(service.get("id")));}
  }
 }
 Map<String,Object> decorateService(Map<String,Object> service,Timestamp time){
  boolean active="ACTIVE".equals(service.get("status"));int minutes=service.get("billed_minutes")==null?0:((Number)service.get("billed_minutes")).intValue();
  if(active&&service.get("started_at")!=null)minutes=Commerce.minutes(((Timestamp)service.get("started_at")).toInstant(),time.toInstant());
  service.put("elapsed_minutes",minutes);service.put("is_estimate",active);
  service.put("estimated_amount",active?Commerce.bookedAmount(service,minutes):decimal(service.get("amount")));
  service.put("can_hide",List.of("CANCELLED","SETTLED").contains(service.get("status")));return service;
 }
 public List<Map<String,Object>> services(long uid,boolean admin){
  var rows=db.queryForList("select s.*,u.customer_code,t.table_no from service_order s join app_user u on u.id=s.user_id left join play_order p on p.id=s.play_order_id left join billiard_table t on t.id=p.table_id "+(admin?"":"where s.user_id=? and not exists(select 1 from personal_history_hide h where h.user_id=? and h.record_kind=s.kind and h.record_id=s.id) ")+"order by s.id desc limit 100",admin?new Object[]{}:new Object[]{uid,uid});
  Timestamp time=Commerce.now();for(var service:rows)decorateService(service,time);return rows;
 }
 public List<Map<String,Object>> orders(long uid,boolean admin){
  var rows=db.queryForList("select p.*,t.table_no from play_order p join billiard_table t on t.id=p.table_id "+(admin?"":"where p.user_id=? and not exists(select 1 from personal_history_hide h where h.user_id=? and h.record_kind='PLAY' and h.record_id=p.id) ")+"order by p.id desc limit 100",admin?new Object[]{}:new Object[]{uid,uid});
  return decorateOrders(rows);
 }
 private List<Map<String,Object>> decorateOrders(List<Map<String,Object>> rows){
  for(var play:rows){var bill=buildBill(play);play.put("total_amount",bill.get("totalAmount"));play.put("paid_amount",bill.get("paidAmount"));play.put("service_amount",bill.get("estimatedServiceTotal"));play.put("due_amount",bill.get("unpaidAmount"));play.put("estimated_due_amount",bill.get("estimatedUnpaidAmount"));play.put("pending_cues",bill.get("pendingCueCount"));play.put("active_coaches",bill.get("activeCoachCount"));play.put("settlement_status",bill.get("settlementStatus"));play.put("can_hide","SETTLED".equals(bill.get("settlementStatus"))&&"COMPLETED".equals(play.get("status")));}return rows;
 }
 public Map<String,Object> datedOrders(java.time.LocalDate from,java.time.LocalDate to,int page){
  return datedOrders(from,to,page,false);
 }
 public Map<String,Object> datedOrders(java.time.LocalDate from,java.time.LocalDate to,int page,boolean pendingOnly){
  String source=pendingOnly?"(select po.* from play_order po where po.status='ACTIVE' or exists (select 1 from service_order so where so.play_order_id=po.id and (so.status in ('REQUESTED','ACTIVE') or (so.status='COMPLETED' and so.settled_at is null)))) p":"play_order p";
  var start=Timestamp.valueOf(from.atStartOfDay());
  var end=Timestamp.valueOf(to.plusDays(1).atStartOfDay());
  var totals=row("""
   select coalesce(sum(is_play),0) as order_count,
          coalesce(sum(case when payment_method='CASH' then amount else 0 end),0) as cash,
          coalesce(sum(case when payment_method='WALLET' then amount else 0 end),0) as wallet,
          coalesce(sum(case when payment_method='MOCK' then amount else 0 end),0) as mock
   from (
     select 1 as is_play,p.payment_method,p.amount from %s where p.started_at>=? and p.started_at<?
     union all
     select 0,s.payment_method,s.amount from service_order s join %s on p.id=s.play_order_id
     where p.started_at>=? and p.started_at<? and s.settled_at is not null
   ) receipts
   """.formatted(source,source),start,end,start,end);
  int size=20;
  long pages=Math.max(1,(((Number)totals.get("order_count")).longValue()+size-1)/size);
  long current=Math.min(Math.max(1,page),pages);
  var rows=db.queryForList("""
   select p.*,t.table_no,u.username as account_name,p.amount+coalesce((
     select sum(s.amount) from service_order s where s.play_order_id=p.id and s.settled_at is not null
       and s.payment_method in ('CASH','MOCK','WALLET')
   ),0) as paid_total from %s join billiard_table t on t.id=p.table_id join app_user u on u.id=p.user_id
   where p.started_at>=? and p.started_at<?
   order by p.started_at desc,p.id desc limit ? offset ?
   """.formatted(source),start,end,size,(current-1)*size);
  return Map.of("rows",decorateOrders(rows),"totals",totals,"page",current,"pages",pages);
 }
 public Map<String,Object> bill(long uid,boolean admin,long id){var play=row("select p.*,t.table_no from play_order p join billiard_table t on t.id=p.table_id where p.id=?",id);owner(play,uid,admin);return buildBill(play);}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> assignCustomer(long uid,boolean admin,long id,long customerId){
  if(!admin)throw new IllegalArgumentException("需要管理员权限");
  row("select id from app_user where id=? and role='CUSTOMER'",customerId);
  var play=lockPlay(uid,true,id);
  if(!"FRONT_DESK".equals(play.get("source")))throw new IllegalArgumentException("仅前台开台的订单可关联顾客");
  if(number(play.get("user_id"))==customerId)return bill(uid,true,id);
  if(!"ACTIVE".equals(play.get("status")))throw new IllegalArgumentException("只能为正在使用的球桌关联顾客");
  var current=row("select role from app_user where id=?",play.get("user_id"));
  if(!List.of("ADMIN",FrontDeskAccount.ROLE).contains(current.get("role")))throw new IllegalArgumentException("订单已归属顾客，不能转给其他人");
  long previousOwner=number(play.get("user_id"));
  var services=db.queryForList("select id,item_id from service_order where play_order_id=? order by item_id,id",id);
  for(var initial:services){
   row("select id from service_item where id=? for update",initial.get("item_id"));
   var service=row("select user_id from service_order where id=? for update",initial.get("id"));
   if(number(service.get("user_id"))!=previousOwner)throw new IllegalArgumentException("本局服务归属不一致，请刷新后核对");
  }
  db.update("update play_order set user_id=? where id=?",customerId,id);
  db.update("update service_order set user_id=? where play_order_id=? and user_id=?",customerId,id,previousOwner);
  log(uid,"SESSION_CUSTOMER",id);return bill(uid,true,id);
 }
 Map<String,Object> buildBill(Map<String,Object> play){
  var rows=db.queryForList("select s.*,t.table_no from service_order s join play_order p on p.id=s.play_order_id join billiard_table t on t.id=p.table_id where s.play_order_id=? order by s.id",play.get("id"));
  BigDecimal fixed=decimal(null),unpaid=decimal(null),estimated=decimal(null),estimatedUnpaid=decimal(null);int cues=0,coaches=0,live=0;Timestamp time=Commerce.now();
  for(var service:rows){decorateService(service,time);String state=(String)service.get("status");BigDecimal amount=decimal(service.get("amount"));BigDecimal estimate=decimal(service.get("estimated_amount"));
   if(List.of("COMPLETED","SETTLED").contains(state)){fixed=fixed.add(amount);estimated=estimated.add(amount);}
   if("COMPLETED".equals(state)){unpaid=unpaid.add(amount);estimatedUnpaid=estimatedUnpaid.add(amount);}
   if("ACTIVE".equals(state)){estimated=estimated.add(estimate);if(service.get("settled_at")==null)estimatedUnpaid=estimatedUnpaid.add(estimate);}
   if(List.of("REQUESTED","ACTIVE").contains(state)){live++;if("CUE".equals(service.get("kind")))cues++;if("COACH".equals(service.get("kind"))&&"ACTIVE".equals(state))coaches++;}
  }
  String status="ACTIVE".equals(play.get("status"))?"IN_USE":cues>0?"PENDING_RETURN":live>0||unpaid.signum()>0?"PAYMENT_PENDING":"SETTLED";
  var result=new LinkedHashMap<String,Object>();result.put("playOrder",new LinkedHashMap<>(play));result.put("services",rows);result.put("totalAmount",decimal(play.get("amount")).add(estimated));
  BigDecimal servicePaid=rows.stream().filter(s->s.get("settled_at")!=null).map(s->decimal(s.get("amount"))).reduce(decimal(null),BigDecimal::add);
  var ownerAccount=row("select username,role from app_user where id=?",play.get("user_id"));
  Object ownerRole=ownerAccount.get("role");
  result.put("accountName",ownerAccount.get("username"));
  result.put("paidAmount",decimal(play.get("amount")).add(servicePaid));result.put("servicePaid",servicePaid);result.put("tablePaid",decimal(play.get("amount")));result.put("serviceTotal",fixed);result.put("estimatedServiceTotal",estimated);result.put("unpaidAmount",unpaid);result.put("estimatedUnpaidAmount",estimatedUnpaid);result.put("pendingCueCount",cues);result.put("activeCoachCount",coaches);result.put("settlementStatus",status);result.put("canAssignCustomer","ACTIVE".equals(play.get("status"))&&"FRONT_DESK".equals(play.get("source"))&&List.of("ADMIN",FrontDeskAccount.ROLE).contains(ownerRole));return result;
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> settle(long uid,boolean admin,long id,String method){
  if(method==null||!List.of("CASH","MOCK","WALLET").contains(method))throw new IllegalArgumentException("收款方式无效");
  if(!admin&&!List.of("MOCK","WALLET").contains(method))throw new IllegalArgumentException("顾客不能登记现金收款");
  if("MOCK".equals(method)&&!demo)throw new IllegalArgumentException("请到前台核对并登记收款");
  var play=lockPlay(uid,admin,id);if(!"COMPLETED".equals(play.get("status")))throw new IllegalArgumentException("请先结束本局再结算");
  var rows=db.queryForList("select * from service_order where play_order_id=? order by item_id,id",id);
  for(var initial:rows){row("select * from service_item where id=? for update",initial.get("item_id"));var service=row("select * from service_order where id=? for update",initial.get("id"));if(List.of("REQUESTED","ACTIVE").contains(service.get("status")))throw new IllegalArgumentException("还有未结束或待归还的服务，请先归还球杆并联系前台处理");}
  Timestamp time=Commerce.now();int changed=db.update("update service_order set status='SETTLED',settled_at=?,payment_method=? where play_order_id=? and status='COMPLETED'",time,method,id);
  if(changed>0)log(uid,"SESSION_SETTLE_"+method,id);return bill(uid,admin,id);
 }
 boolean clearable(long uid,String kind,Map<String,Object> record){
  if(number(record.get("user_id"))!=uid)return false;
  if(kind.equals("PLAY"))return "COMPLETED".equals(record.get("status"))&&"SETTLED".equals(buildBill(record).get("settlementStatus"));
  return kind.equals(record.get("kind"))&&List.of("SETTLED","CANCELLED").contains(record.get("status"));
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> hide(long uid,String kind,List<Long> ids,boolean all){
  if(kind==null||!List.of("PLAY","COACH","CUE").contains(kind))throw new IllegalArgumentException("历史分类无效");
  if(!all&&(ids==null||ids.isEmpty()||ids.size()>1000||ids.stream().anyMatch(Objects::isNull)))throw new IllegalArgumentException("请选择需要移除的历史记录");
  String table=kind.equals("PLAY")?"play_order":"service_order";
  List<Long> targets=all?db.queryForList("select id from "+table+" where user_id=? "+(kind.equals("PLAY")?"":"and kind='"+kind+"' ")+"order by id",Long.class,uid):new ArrayList<>(new TreeSet<>(ids));
  int hidden=0;
  // Eligible states are irreversible. No resource locks here: bulk history cleaning must not
  // hold one session's item lock while waiting on a second session's table or service lock.
  for(long id:targets){var record=row("select * from "+table+" where id=?",id);if(!clearable(uid,kind,record)){if(all)continue;throw new IllegalArgumentException("只能移除本人已取消或已结清的记录；进行中、待付或待归还记录不能清理");}
   try{db.update("insert into personal_history_hide(user_id,record_kind,record_id,created_at) values(?,?,?,?)",uid,kind,id,Commerce.now());hidden++;}catch(org.springframework.dao.DuplicateKeyException alreadyHidden){}}
  if(hidden>0)log(uid,"HISTORY_HIDE_"+kind,0);return Map.of("hiddenCount",hidden);
 }
}


