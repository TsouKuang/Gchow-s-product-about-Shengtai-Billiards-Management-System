package com.billiard;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:experience;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
class ExperienceTest {
 @Autowired CatalogRefresh refresh;@Autowired Ledger ledger;@Autowired Business business;@Autowired Commerce commerce;@Autowired CatalogAdmin catalog;@Autowired Api api;@Autowired Wallet wallet;@Autowired Leaderboard leaderboard;
 long admin;
 String key(){return UUID.randomUUID().toString();}
 @BeforeEach void prepare(){var db=business.jdbc();db.update("delete from personal_history_hide");db.update("delete from service_order");db.update("delete from play_order");db.update("update billiard_table set status='IDLE',active_order_id=null");admin=db.queryForObject("select id from app_user where username='admin'",Long.class);}
 long member(){String username="test_"+key().substring(0,8);business.jdbc().update("insert into app_user(username,role) values(?,'CUSTOMER')",username);long id=business.jdbc().queryForObject("select id from app_user where username=?",Long.class,username);wallet.code(id);return id;}
 ServiceProfiles.Profile profile(String kind){return new ServiceProfiles.Profile("测试"+key().substring(0,4),"技术与服务资料",new BigDecimal("2.00"),1,true,kind.equals("COACH")?"/assets/services/coach-mianmian.png":"/assets/services/peri-tianwang.png","女",3,24,168,52,"耐心细致","基础训练");}
 @Test void serviceNumbersPersistAndDeletedNumbersAreNotReused(){
  var first=catalog.create(admin,new CatalogAdmin.Create("CUE","圣泰",profile("CUE")));String code=(String)first.get("service_code");assertTrue(code.matches("G[0-9]+"));long id=Ledger.number(first.get("id"));catalog.assignCodes();assertEquals(code,business.row("select service_code from service_item where id=?",id).get("service_code"));catalog.remove(admin,id);
  assertTrue(commerce.items(true).stream().noneMatch(row->Ledger.number(row.get("id"))==id));var second=catalog.create(admin,new CatalogAdmin.Create("CUE","圣泰",profile("CUE")));assertEquals(Integer.parseInt(code.substring(1))+1,Integer.parseInt(((String)second.get("service_code")).substring(1)));
  var coach=catalog.create(admin,new CatalogAdmin.Create("COACH","圣泰",profile("COACH")));assertTrue(((String)coach.get("service_code")).startsWith("S"));catalog.update(admin,Ledger.number(coach.get("id")),new CatalogAdmin.Create("COACH","圣泰二队",profile("COACH")));assertEquals("圣泰二队",business.row("select brand from service_item where id=?",coach.get("id")).get("brand"));
 }
 @Test void activeInventoryCannotBeDeletedAndEndingTableIsDirectlyReusable(){
  var item=catalog.create(admin,new CatalogAdmin.Create("CUE","圣泰",profile("CUE")));long itemId=Ledger.number(item.get("id"));var play=business.open(admin,true,1,1,key(),"MOCK");long id=Ledger.number(play.get("id"));var service=commerce.bookPaid(admin,true,itemId,id,key(),1,"CASH");
  assertThrows(IllegalArgumentException.class,()->catalog.remove(admin,itemId));business.finish(admin,true,id);assertEquals("IDLE",business.row("select status from billiard_table where id=1").get("status"));business.open(admin,true,1,1,key(),"CASH");commerce.action(admin,true,Ledger.number(service.get("id")),"finish");catalog.remove(admin,itemId);assertEquals("SETTLED",business.row("select status from service_order where id=?",service.get("id")).get("status"));
 }
 @Test void packageEditsAndTableSpecificPricingKeepHistoricalSnapshots(){
  var before=business.row("select * from play_package where id=1");var old=business.open(admin,true,1,1,key(),"CASH");catalog.updatePackage(admin,1,new CatalogAdmin.PackageUpdate("新套餐名称",75,new BigDecimal("66.00")));
  assertEquals(before.get("name"),business.row("select package_name from play_order where id=?",old.get("id")).get("package_name"));commerce.price(admin,"TABLE",2,1,new BigDecimal("29.00"));assertEquals(new BigDecimal("29.00"),business.prices(2).stream().filter(r->Ledger.number(r.get("id"))==1).findFirst().get().get("price"));
  catalog.updatePackage(admin,1,new CatalogAdmin.PackageUpdate((String)before.get("name"),((Number)before.get("minutes")).intValue(),(BigDecimal)before.get("price")));
 }
 @Test void memberConsumptionGroupsTableAndServicesIntoOneOrder(){
  long uid=member();var play=business.open(uid,false,1,1,key(),"MOCK");long id=Ledger.number(play.get("id"));var cues=commerce.items(false).stream().filter(i->"CUE".equals(i.get("kind"))).limit(2).toList();BigDecimal total=Ledger.decimal(play.get("amount"));for(var item:cues){var service=commerce.bookPaid(admin,true,Ledger.number(item.get("id")),id,key(),2,"CASH");total=total.add(Ledger.decimal(service.get("amount")));}
  var auth=api.token(admin,"ADMIN");var detail=(Map<?,?>)api.memberDetail("Bearer "+auth.get("token"),uid,1);var rows=(List<Map<String,Object>>)detail.get("receipts");assertEquals(1,rows.size());assertEquals(total,rows.get(0).get("amount"));assertEquals("MIXED",rows.get(0).get("payment_method"));
 }
 @Test void monthAndYearBoardsClipCrossBoundarySessionsAndIncludeActiveTime(){
  long a=member(),b=member();var db=business.jdbc();LocalDateTime now=LocalDateTime.of(2026,11,5,12,0);
  var first=business.open(a,false,1,1,key(),"MOCK");business.finish(a,false,Ledger.number(first.get("id")));db.update("update play_order set started_at=?,ended_at=? where id=?",Timestamp.valueOf("2026-10-31 23:30:00"),Timestamp.valueOf("2026-11-01 00:30:00"),first.get("id"));
  var second=business.open(b,false,2,1,key(),"MOCK");business.finish(b,false,Ledger.number(second.get("id")));db.update("update play_order set started_at=?,ended_at=? where id=?",Timestamp.valueOf("2026-11-01 01:00:00"),Timestamp.valueOf("2026-11-01 02:00:00"),second.get("id"));
  var active=business.open(a,false,1,1,key(),"MOCK");db.update("update play_order set started_at=? where id=?",Timestamp.valueOf("2026-11-05 11:30:00"),active.get("id"));
  var month=leaderboard.readAt(a,"month",now);var me=(Map<?,?>)month.get("me");assertEquals(3600L,me.get("seconds"));var year=leaderboard.readAt(a,"year",now);assertEquals(5400L,((Map<?,?>)year.get("me")).get("seconds"));assertEquals(1,((Map<?,?>)year.get("me")).get("rank"));assertThrows(IllegalArgumentException.class,()->leaderboard.readAt(a,"day",now));
 }
 @Test void brandPriceAppliesToEveryModelAndPreservesOldOrder(){
  var old=business.open(admin,true,1,1,key(),"CASH");var previous=old.get("amount");
  long brand=business.jdbc().queryForObject("select min(id) from table_model where brand=(select brand from table_model where id=(select model_id from billiard_table where id=1))",Long.class);
  commerce.price(admin,"BRAND",brand,1,new BigDecimal("27.50"));
  var rows=business.jdbc().queryForList("select t.id from billiard_table t join table_model m on m.id=t.model_id where m.brand=(select brand from table_model where id=?)",brand);
  for(var row:rows)assertEquals(new BigDecimal("27.50"),business.prices(Ledger.number(row.get("id"))).stream().filter(r->Ledger.number(r.get("id"))==1).findFirst().orElseThrow().get("price"));
  assertEquals(previous,business.row("select amount from play_order where id=?",old.get("id")).get("amount"));commerce.price(admin,"BRAND",brand,1,null);
 }
 @Test void monthlyBillSeparatesMonthsAndDoesNotTreatPayrollEstimateAsExpense(){
  var auth=api.token(admin,"ADMIN");String header="Bearer "+auth.get("token");
  api.recordExpense(header,new Api.ExpenseRequest("PAYROLL","2020-02-10",new BigDecimal("100.00"),"工资测试"));
  var bill=(Map<?,?>)api.storeBill(header,"2020-02");assertEquals("2020-02",bill.get("month"));assertEquals(1,((List<?>)bill.get("expenses")).size());assertEquals(0,((BigDecimal)bill.get("income")).signum());
  assertTrue(((List<?>)((Map<?,?>)api.storeBill(header,"2020-03")).get("expenses")).isEmpty());business.jdbc().update("delete from store_expense where expense_date=?",java.sql.Date.valueOf("2020-02-10"));
 }

 @Test void pendingOrderFilterIncludesUnreturnedCuesAndExcludesFinishedPaidOrders(){
  var closed=business.open(admin,true,1,1,key(),"CASH");business.finish(admin,true,Ledger.number(closed.get("id")));
  var current=business.open(admin,true,2,1,key(),"CASH");long id=Ledger.number(current.get("id"));
  var cue=commerce.items(false).stream().filter(i->"CUE".equals(i.get("kind"))).findFirst().orElseThrow();
  var service=commerce.bookPaid(admin,true,Ledger.number(cue.get("id")),id,key(),1,"CASH");business.finish(admin,true,id);
  var day=LocalDate.now(ZoneId.of("Asia/Shanghai"));var report=ledger.datedOrders(day,day,1,true);
  assertEquals(1,((Number)((Map<?,?>)report.get("totals")).get("order_count")).intValue());
  assertEquals(id,Ledger.number(((List<Map<String,Object>>)report.get("rows")).get(0).get("id")));
  commerce.action(admin,true,Ledger.number(service.get("id")),"finish");
  assertEquals(0,((Number)((Map<?,?>)ledger.datedOrders(day,day,1,true).get("totals")).get("order_count")).intValue());
 }

 @Test void catalogRefreshDoesNotDuplicateOrEraseManagerEditsOnRestart()throws Exception{
  var db=business.jdbc();long id=db.queryForObject("select id from service_item where catalog_key='coach-qianqian'",Long.class);
  var before=business.row("select * from service_item where id=?",id);int count=db.queryForObject("select count(*) from service_item",Integer.class);
  db.update("update service_item set name=?,description=?,personal_rate=? where id=?","店长编辑的助教","保留自主编辑的文案",new BigDecimal("2.50"),id);
  var play=business.open(admin,true,1,1,key(),"CASH");refresh.seed();refresh.seed();
  assertEquals(count,db.queryForObject("select count(*) from service_item",Integer.class));
  var item=business.row("select * from service_item where id=?",id);assertEquals("店长编辑的助教",item.get("name"));assertEquals(new BigDecimal("2.50"),item.get("personal_rate"));assertEquals(play.get("amount"),business.row("select amount from play_order where id=?",play.get("id")).get("amount"));
  db.update("update service_item set name=?,description=?,personal_rate=? where id=?",before.get("name"),before.get("description"),before.get("personal_rate"),id);
 }

}


