package com.billiard;
import java.util.*;
import java.math.*;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Commerce {
 final Business b;
 final Ledger ledger;
 Commerce(Business b,Ledger ledger){this.b=b;this.ledger=ledger;}
 static Timestamp now(){return Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));}
 static void money(BigDecimal value){if(value==null||value.signum()<=0||value.scale()>2||value.compareTo(new BigDecimal("99999"))>0)throw new IllegalArgumentException("价格须为0.01至99999元，最多两位小数");}
 public void seed(){
  // Only seed once. Existing manager edits are never overwritten on restart.
  if(b.jdbc().queryForObject("select count(*) from package_price",Integer.class)==0){int[] hourly={38,48,32,42,36,58};for(int m=1;m<=6;m++)for(var p:b.jdbc().queryForList("select * from play_package")){int minutes=((Number)p.get("minutes")).intValue();var price=BigDecimal.valueOf(hourly[m-1]).multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);if(minutes>=120)price=price.multiply(new BigDecimal("0.90")).setScale(2,RoundingMode.HALF_UP);b.jdbc().update("insert into package_price(scope_type,scope_id,package_id,price) values('MODEL',?,?,?)",m,p.get("id"),price);}}
  if(b.jdbc().queryForObject("select count(*) from service_item",Integer.class)>0)return;
  cue("皮尔力","天王",24,"https://www.pericue.com/ProductCust/2957381-649170.html");cue("皮尔力","黄金甲",22,"https://www.pericue.com/ProductCust/2957381-649170.html");cue("皮尔力","赤兔",18,"https://www.pericue.com/ProductCust/2957381-649170.html");
  cue("美洲豹 MEZZ","ACE218 系列",24,"https://mezzcue.com/en/");cue("美洲豹 MEZZ","CP-26 系列",24,"https://mezzcue.com/en/");cue("美洲豹 MEZZ","AVT 系列",28,"https://mezzcue.com/en/");
  cue("威利 FURY","AS-1 黑刃",16,"https://www.furycues.cn/");cue("威利 FURY","AS-2 血刃",18,"https://www.furycues.cn/");cue("威利 FURY","CN-12 临风",16,"https://www.furycues.cn/");
  cue("野豹","硬核 II",18,"https://www.yebaocues.com/");cue("野豹","准神 4",20,"https://www.yebaocues.com/");cue("野豹","G4",22,"https://www.yebaocues.com/");
  cue("奥秘 O’MIN","三国系列",18,"https://gb.omincn.com/");cue("奥秘 O’MIN","新时代系列",16,"https://gb.omincn.com/");
  cue("John Parris","Ultimate",30,"https://www.parriscues.com/");cue("John Parris","Paragon",30,"https://www.parriscues.com/");cue("John Parris","Special",26,"https://www.parriscues.com/");
  b.jdbc().update("insert into service_item(kind,brand,name,description,rate,stock,enabled,source_url) values('CUE','仁杆','门店型号待确认','尚未核实准确系列；确认实物型号后由管理员修改并上架。',20,0,false,'')");
  coach("助教 A · 基础陪练","基础档 · 站姿、握杆、出杆与基础走位练习",1);
  coach("助教 B · 进阶陪练","进阶档 · 连续进攻、分球与母球控制练习",2);
  coach("助教 C · 竞技陪练","竞技档 · 战术分析、清台思路与比赛模拟",3);
 }
 void cue(String brand,String name,int rate,String source){b.jdbc().update("insert into service_item(kind,brand,name,description,rate,stock,enabled,source_url) values('CUE',?,?,?, ?,2,true,?)",brand,name,"演示库存 · 到前台验杆、领取和归还；按实际使用时长，整小时向上计费。",rate,source);}
 void coach(String name,String desc,int rate){b.jdbc().update("insert into service_item(kind,brand,name,description,rate,stock,enabled,source_url) values('COACH','女子助教',?,?,?,1,true,'')",name,desc+"。演示人物，等级按球技划分，非真实人员履历。",rate);}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void price(long uid,String scope,long sid,long pid,BigDecimal value){if(!List.of("TABLE","MODEL","BRAND").contains(scope))throw new IllegalArgumentException("定价范围无效");b.row("select id from "+(scope.equals("TABLE")?"billiard_table":"table_model")+" where id=? for update",sid);b.row("select id from play_package where id=?",pid);if(value!=null)money(value);b.jdbc().update("delete from package_price where scope_type=? and scope_id=? and package_id=?",scope,sid,pid);if(value!=null)b.jdbc().update("insert into package_price(scope_type,scope_id,package_id,price) values(?,?,?,?)",scope,sid,pid,value);b.log(uid,"PRICE_"+scope,sid);}
 public List<Map<String,Object>> items(boolean admin){var items=b.jdbc().queryForList("select i.*,g.title as grade_title,g.rate as grade_rate,g.hourly_rate,(i.stock-(select count(*) from service_order o where o.item_id=i.id and o.status in ('REQUESTED','ACTIVE'))) as available from service_item i left join coach_grade g on g.stars=i.star_level "+(admin?"where i.deleted=false ":"where i.enabled=true and i.deleted=false ")+"order by i.kind,i.star_level,i.id");for(var item:items)if("COACH".equals(item.get("kind"))&&item.get("grade_rate")!=null){var hourly=item.get("hourly_rate");item.put("rate",hourly==null?item.get("grade_rate"):((BigDecimal)hourly).divide(BigDecimal.valueOf(60),12,RoundingMode.HALF_UP));}return items;}

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void edit(long uid,long id,String name,String description,BigDecimal rate,int stock,boolean enabled){money(rate);if(name==null||name.isBlank()||name.length()>100||description==null||description.length()>500||stock<0||stock>999)throw new IllegalArgumentException("名称、说明或库存无效");var editing=b.row("select * from service_item where id=? for update",id);if(Boolean.TRUE.equals(editing.get("deleted")))throw new IllegalArgumentException("该服务已删除");if("COACH".equals(editing.get("kind"))&&stock>1)throw new IllegalArgumentException("每位助教同一时间只能服务一单");int used=b.jdbc().queryForObject("select count(*) from service_order where item_id=? and status in ('REQUESTED','ACTIVE')",Integer.class,id);if(stock<used)throw new IllegalArgumentException("库存不能低于已预约和使用中的数量");b.jdbc().update("update service_item set name=?,description=?,rate=?,stock=?,enabled=? where id=?",name,description,rate,stock,enabled,id);b.log(uid,"SERVICE_EDIT",id);}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> book(long uid,boolean admin,long id,long playId,String key){
  return book(uid,admin,id,playId,key,null);
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> book(long uid,boolean admin,long id,long playId,String key,Integer hours){
  return book(uid,admin,id,playId,key,hours,false);
 }
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> book(long uid,boolean admin,long id,long playId,String key,Integer hours,boolean mockPrepaid){return bookPaid(uid,admin,id,playId,key,hours,mockPrepaid?"MOCK":null);}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> bookPaid(long uid,boolean admin,long id,long playId,String key,Integer hours,String paymentMethod){
  if(paymentMethod!=null&&!List.of("CASH","MOCK","WALLET").contains(paymentMethod))throw new IllegalArgumentException("收款方式无效");
  if("CASH".equals(paymentMethod)&&!admin)throw new IllegalArgumentException("仅前台可登记现金");
  boolean prepaid=paymentMethod!=null, mockPrepaid="MOCK".equals(paymentMethod);
  if(key==null||!key.matches("[A-Za-z0-9-]{8,80}"))throw new IllegalArgumentException("请求编号无效");
  if(playId<1)throw new IllegalArgumentException("请先开台并选择本次服务关联的球桌订单");
  var play=ledger.lockPlay(uid,admin,playId);
  var item=b.row("select * from service_item where id=? for update",id);
  boolean coach="COACH".equals(item.get("kind"));
  if((prepaid&&hours==null)||(hours!=null&&((!coach&&!prepaid)||hours<1||hours>12)))throw new IllegalArgumentException("服务时长须为1至12个整小时");
  var old=b.jdbc().queryForList("select * from service_order where request_key=?",key);
  if(!old.isEmpty()){var order=old.get(0);if(order.get("created_by")==null||Ledger.number(order.get("created_by"))!=uid||Ledger.number(order.get("item_id"))!=id||order.get("play_order_id")==null||Ledger.number(order.get("play_order_id"))!=playId||(!java.util.Objects.equals(hours,order.get("booked_hours"))||!Objects.equals(paymentMethod,order.get("payment_method"))))throw new IllegalArgumentException("请求编号已被其他服务或球桌订单使用");return order;}
  if(!"ACTIVE".equals(play.get("status")))throw new IllegalArgumentException("本局已结束，请先开台再选择服务");
  if(((Timestamp)play.get("expected_end_at")).before(now()))throw new IllegalArgumentException("套餐已到时，请刷新查看本局账单");
  BigDecimal hourly=null;
  if(coach&&item.get("star_level")!=null){var grade=b.row("select rate,hourly_rate from coach_grade where stars=?",item.get("star_level"));item.put("rate",grade.get("rate"));hourly=(BigDecimal)grade.get("hourly_rate");if(hourly==null)hourly=((BigDecimal)grade.get("rate")).multiply(BigDecimal.valueOf(60));}
  int used=b.jdbc().queryForObject("select count(*) from service_order where item_id=? and status in ('REQUESTED','ACTIVE')",Integer.class,id);if(Boolean.TRUE.equals(item.get("deleted"))||!Boolean.TRUE.equals(item.get("enabled"))||used>=((Number)item.get("stock")).intValue())throw new IllegalArgumentException("该服务暂不可预约");
  Timestamp time=now();
  boolean startNow=coach||prepaid;
  b.jdbc().update("insert into service_order(request_key,user_id,item_id,item_name,kind,rate,status,created_at,started_at,play_order_id,created_by,booked_hours,payment_method,settled_at,amount,mock_prepaid) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",key,play.get("user_id"),id,item.get("brand")+" · "+item.get("name"),item.get("kind"),item.get("rate"),startNow?"ACTIVE":"REQUESTED",time,startNow?time:null,playId,uid,hours,paymentMethod,prepaid?time:null,prepaid?amount((String)item.get("kind"),(BigDecimal)item.get("rate"),hours*60):null,mockPrepaid);
  var order=b.row("select * from service_order where request_key=?",key);
  if(hourly!=null){order.put("hourly_rate",hourly);var charge=prepaid?bookedAmount(order,hours*60):null;b.jdbc().update("update service_order set hourly_rate=?,amount=? where id=?",hourly,charge,order.get("id"));order.put("amount",charge);}
  b.log(uid,coach?"COACH_START":"CUE_REQUEST",Ledger.number(order.get("id")));return order;
 }
 static int minutes(Instant start,Instant end){return (int)Math.max(1,(Duration.between(start,end).toMillis()+59999)/60000);}
 static BigDecimal bookedAmount(Map<String,Object> order,int minutes){
  if("COACH".equals(order.get("kind"))&&order.get("hourly_rate") instanceof BigDecimal hourly){
   int billedHours=order.get("booked_hours") instanceof Number hours?hours.intValue():Math.max(1,(minutes+59)/60);
   return hourly.multiply(BigDecimal.valueOf(billedHours)).setScale(2,RoundingMode.HALF_UP);
  }
  var rate=(BigDecimal)order.get("rate");
  if(order.get("booked_hours") instanceof Number hours)return amount((String)order.get("kind"),rate,hours.intValue()*60);
  return amount((String)order.get("kind"),rate,minutes);
 }
 static BigDecimal amount(String kind,BigDecimal rate,int minutes){return rate.multiply(BigDecimal.valueOf(kind.equals("CUE")?(minutes+59)/60:minutes)).setScale(2,RoundingMode.HALF_UP);}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Object action(long uid,boolean admin,long id,String action){var o=ledger.lockService(uid,admin,id);String state=(String)o.get("status");
  if(action.equals("cancel")){if(state.equals("CANCELLED"))return o;if(!state.equals("REQUESTED"))throw new IllegalArgumentException("仅待确认预约可以取消");b.jdbc().update("update service_order set status='CANCELLED' where id=?",id);}
  else {if(!admin&&!(action.equals("finish")&&"COACH".equals(o.get("kind"))))throw new IllegalArgumentException("球杆领取、归还和线下收款请联系前台处理");switch(action){case "start":if(state.equals("ACTIVE"))return o;if(!state.equals("REQUESTED"))throw new IllegalArgumentException("服务状态已改变");if(o.get("play_order_id")!=null&&!"ACTIVE".equals(b.row("select status from play_order where id=?",o.get("play_order_id")).get("status")))throw new IllegalArgumentException("关联球桌已结束，不能再领取球杆");b.jdbc().update("update service_order set status='ACTIVE',started_at=? where id=?",now(),id);break;
   case "finish":if(state.equals("COMPLETED")||state.equals("SETTLED"))return o;if(!state.equals("ACTIVE"))throw new IllegalArgumentException("服务尚未开始");ledger.completeService(uid,o,now());break;
   case "settle":if(state.equals("SETTLED"))return o;if(!state.equals("COMPLETED"))throw new IllegalArgumentException("请先结束服务再登记收款");b.jdbc().update("update service_order set status='SETTLED',payment_method='CASH',settled_at=? where id=?",now(),id);break;default:throw new IllegalArgumentException("操作无效");}}
  b.log(uid,"SERVICE_"+action.toUpperCase(),id);return b.row("select * from service_order where id=?",id);
 }
}

