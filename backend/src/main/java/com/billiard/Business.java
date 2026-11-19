package com.billiard;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
@Service
public class Business {
 final JdbcTemplate db;
 final Ledger ledger;
 final FrontDeskAccount frontDesk;
 Business(JdbcTemplate db,Ledger ledger,FrontDeskAccount frontDesk){this.db=db;this.ledger=ledger;this.frontDesk=frontDesk;}
 public JdbcTemplate jdbc(){return db;}
 Map<String,Object> row(String sql,Object... args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw new IllegalArgumentException("记录不存在");return rows.get(0);}
 void log(long uid,String action,long id){db.update("insert into operation_log(user_id,action,business_id,created_at) values(?,?,?,?)",uid,action,id,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))));}
 public List<Map<String,Object>> prices(long tid){return db.queryForList("select p.id,p.name,p.minutes,coalesce(bp.price,tp.price,mp.price,p.price) as price,case when bp.price is not null then 'BRAND' when tp.price is not null then 'TABLE' when mp.price is not null then 'MODEL' else 'DEFAULT' end as price_source from play_package p join billiard_table t on t.id=? join table_model tm on tm.id=t.model_id left join package_price bp on bp.scope_type='BRAND' and bp.scope_id=(select min(bm.id) from table_model bm where bm.brand=tm.brand) and bp.package_id=p.id left join package_price tp on tp.scope_type='TABLE' and tp.scope_id=t.id and tp.package_id=p.id left join package_price mp on mp.scope_type='MODEL' and mp.scope_id=t.model_id and mp.package_id=p.id where p.enabled=true order by p.minutes",tid);}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Map<String,Object> open(long uid,boolean admin, long tid,long pid,String key,String method){
  if(key==null||!key.matches("[A-Za-z0-9-]{8,80}"))throw new IllegalArgumentException("请求编号无效");
  if(!admin&&!List.of("MOCK","WALLET").contains(method))throw new IllegalArgumentException("请选择可用的收款方式");
  if(!List.of("CASH","MOCK","WALLET").contains(method))throw new IllegalArgumentException("收款方式无效");
  long ownerId=admin?frontDesk.id():uid;
  var table=row("select * from billiard_table where id=? for update",tid);
  var old=db.queryForList("select * from play_order where request_key=?",key);
  if(!old.isEmpty()){var o=old.get(0);if(((Number)o.get("user_id")).longValue()!=ownerId||((Number)o.get("table_id")).longValue()!=tid||((Number)o.get("package_id")).longValue()!=pid||!o.get("payment_method").equals(method))throw new IllegalArgumentException("请求编号已被使用");return o;}
  if(!"IDLE".equals(table.get("status")))throw new IllegalArgumentException("球桌当前不可开台，请刷新状态");
  var pack=prices(tid).stream().filter(p->((Number)p.get("id")).longValue()==pid).findFirst().orElseThrow(()->new IllegalArgumentException("套餐已下架"));
  var now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));var end=now.plusMinutes(((Number)pack.get("minutes")).longValue());var no="B"+UUID.randomUUID().toString().replace("-","");
  db.update("insert into play_order(order_no,request_key,user_id,table_id,package_id,package_name,minutes,amount,source,payment_method,status,started_at,expected_end_at,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",no,key,ownerId,tid,pid,pack.get("name"),pack.get("minutes"),pack.get("price"),admin?"FRONT_DESK":"MINIPROGRAM",method,"ACTIVE",Timestamp.valueOf(now),Timestamp.valueOf(end),Timestamp.valueOf(now));
  var order=row("select * from play_order where order_no=?",no);long id=((Number)order.get("id")).longValue();
  db.update("update billiard_table set status='IN_USE',active_order_id=? where id=?",id,tid);log(uid,"PAID_AND_OPEN",id);return order;
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public void finish(long uid,boolean admin,long oid){
  finishInternal(uid,admin,oid,false);
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public void expire(long oid){finishInternal(0,true,oid,true);}
 private void finishInternal(long uid,boolean admin,long oid,boolean expired){
  var initial=row("select * from play_order where id=?",oid);
  if(!admin&&((Number)initial.get("user_id")).longValue()!=uid)throw new IllegalArgumentException("无权操作他人订单");
  long tid=((Number)initial.get("table_id")).longValue();row("select * from billiard_table where id=? for update",tid);
  var o=row("select * from play_order where id=? for update",oid);
  if("COMPLETED".equals(o.get("status")))return;
  var ended=Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
  var deadline=(Timestamp)o.get("expected_end_at");
  if(expired&&deadline.after(ended))return;
  if(deadline.before(ended))ended=deadline;
  if(expired)uid=((Number)o.get("user_id")).longValue();
  ledger.endRelated(uid,oid,ended);
  db.update("update play_order set status='COMPLETED',ended_at=? where id=?",ended,oid);
  db.update("update billiard_table set status='IDLE',active_order_id=null where id=?",tid);log(uid,expired?"PACKAGE_EXPIRED":"FINISH",oid);
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public void status(long uid,long tid,String state){
  if(!List.of("IDLE","MAINTENANCE").contains(state))throw new IllegalArgumentException("状态无效");
  var t=row("select * from billiard_table where id=? for update",tid);
  if("IN_USE".equals(t.get("status")))throw new IllegalArgumentException("请先结束当前使用");
  db.update("update billiard_table set status=? where id=?",state,tid);log(uid,"TABLE_"+state,tid);
 }
}

