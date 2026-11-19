package com.billiard;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:operations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class OperationsTest {
 @Autowired Business business;
 @Autowired Commerce commerce;
 @Autowired ServiceProfiles profiles;
 @Autowired ExpenseLedger expenses;
 @Autowired Api api;
 @Autowired MockMvc mvc;
 long user;
 @BeforeEach void reset(){
  var db=business.jdbc();
  db.update("delete from service_order");db.update("delete from play_order");db.update("update billiard_table set status='IDLE',active_order_id=null");
  db.update("delete from store_expense");db.update("delete from expense_history");
  db.update("update coach_grade set rate=1,hourly_rate=null where stars=3");
  user=db.queryForObject("select id from app_user where username='demo-customer'",Long.class);
 }
 String key(){return UUID.randomUUID().toString();}
 long play(){return Ledger.number(business.open(user,false,1,1,key(),"MOCK").get("id"));}
 @Test void expiryClosesAtDeadlineAndPreservesUnreturnedCueAndHistory(){
  long play=play();var db=business.jdbc();
  long coach=db.queryForObject("select id from service_item where catalog_key='coach-mianmian'",Long.class);
  long cue=db.queryForObject("select min(id) from service_item where kind='CUE' and enabled=true",Long.class);
  long coachOrder=Ledger.number(commerce.book(user,false,coach,play,key(),2).get("id"));
  long cueOrder=Ledger.number(commerce.book(user,false,cue,play,key()).get("id"));
  commerce.action(user,true,cueOrder,"start");
  var deadline=new Timestamp(System.currentTimeMillis()-10000);
  db.update("update play_order set expected_end_at=? where id=?",deadline,play);
  business.expire(play);business.expire(play);
  assertEquals("COMPLETED",business.row("select status from play_order where id=?",play).get("status"));
  assertEquals(deadline,business.row("select ended_at from play_order where id=?",play).get("ended_at"));
  assertEquals("IDLE",business.row("select status from billiard_table where id=1").get("status"));
  assertEquals("COMPLETED",business.row("select status from service_order where id=?",coachOrder).get("status"));
  assertEquals("ACTIVE",business.row("select status from service_order where id=?",cueOrder).get("status"));
  assertEquals(1,db.queryForObject("select count(*) from operation_log where business_id=? and action='PACKAGE_EXPIRED'",Integer.class,play));
  long next=play();business.expire(play);
  assertEquals(next,Ledger.number(business.row("select active_order_id from billiard_table where id=1").get("active_order_id")));
 }
 @Test void futurePackageIsNotExpired(){long id=play();business.expire(id);assertEquals("ACTIVE",business.row("select status from play_order where id=?",id).get("status"));}
 @Test void hourlyPriceIsExactAndPersonalOverrideIsIgnored(){
  long id=play();long coach=business.jdbc().queryForObject("select id from service_item where catalog_key='coach-mianmian'",Long.class);
  business.jdbc().update("update service_item set personal_rate=99 where id=?",coach);
  profiles.gradeHourly(user,3,new BigDecimal("488.80"));
  var order=commerce.book(user,false,coach,id,key(),2);
  assertEquals(new BigDecimal("488.80"),order.get("hourly_rate"));
  assertEquals(new BigDecimal("977.60"),Commerce.bookedAmount(order,1));
  profiles.gradeHourly(user,3,new BigDecimal("388.00"));
  commerce.action(user,true,Ledger.number(order.get("id")),"finish");
  assertEquals(new BigDecimal("977.60"),business.row("select amount from service_order where id=?",order.get("id")).get("amount"));
 }
 @Test void expenseCorrectionCancellationAndStaleVersion(){
  var first=new ExpenseLedger.Entry("RENT","2020-02-10",new BigDecimal("100.00"),"房租",null);
  expenses.create(user,first);
  long id=business.jdbc().queryForObject("select max(id) from store_expense",Long.class);
  var edited=new ExpenseLedger.Entry("RENT","2020-02-11",new BigDecimal("80.00"),"更正房租",0);
  expenses.update(user,id,edited);
  assertThrows(IllegalArgumentException.class,()->expenses.update(user,id,edited));
  expenses.cancel(user,id,1);expenses.cancel(user,id,1);
  assertEquals(2,business.jdbc().queryForObject("select count(*) from expense_history where expense_id=?",Integer.class,id));
  assertEquals(0,business.jdbc().queryForObject("select count(*) from store_expense where cancelled=false",Integer.class));
  assertThrows(IllegalArgumentException.class,()->expenses.update(user,id,new ExpenseLedger.Entry("RENT","2020-02-11",BigDecimal.ONE,"重写",2)));
 }
 @Test void expenseMutationsRequireAdmin()throws Exception {
  String token=(String)api.token(user,"CUSTOMER").get("token");
  mvc.perform(put("/api/expenses/1").header("Authorization","Bearer "+token).contentType("application/json").content("{\"category\":\"RENT\",\"date\":\"2020-02-10\",\"amount\":1,\"note\":\"test\",\"version\":0}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/expenses/1/cancel").contentType("application/json").content("{\"version\":0}")).andExpect(status().isUnauthorized());
 }
}
