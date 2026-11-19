package com.billiard;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:assignment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
class CustomerAssignmentTest {
 @Autowired Business business;
 @Autowired Commerce commerce;
 @Autowired Ledger ledger;
 @Autowired FrontDeskAccount frontDesk;
 @Autowired Api api;
 long admin,customer;
 @BeforeEach void prepare(){
  business.jdbc().update("delete from personal_history_hide");
  business.jdbc().update("delete from service_order");
  business.jdbc().update("delete from play_order");
  business.jdbc().update("update billiard_table set status='IDLE',active_order_id=null");
  admin=business.jdbc().queryForObject("select id from app_user where username='admin'",Long.class);
  customer=business.jdbc().queryForObject("select id from app_user where username='demo-customer'",Long.class);
 }
 long number(Object value){return ((Number)value).longValue();}
 long open(){return number(business.open(admin,true,1,1,UUID.randomUUID().toString(),"MOCK").get("id"));}
 long item(String key){return business.jdbc().queryForObject("select id from service_item where catalog_key=?",Long.class,key);}
 @Test void unattendedOrdersBelongToTheSharedAccountAndLegacyReceiptsMoveWithTheirSession(){
  long play=open();
  var cue=commerce.book(admin,true,item("peri-tianwang"),play,UUID.randomUUID().toString(),2,true);
  assertEquals(frontDesk.id(),number(cue.get("user_id")));
  assertEquals(FrontDeskAccount.NAME,ledger.bill(admin,true,play).get("accountName"));

  business.jdbc().update("update play_order set user_id=? where id=?",admin,play);
  business.jdbc().update("update service_order set user_id=? where play_order_id=?",admin,play);
  frontDesk.run(null);
  frontDesk.run(null);

  assertEquals(frontDesk.id(),number(business.row("select user_id from play_order where id=?",play).get("user_id")));
  assertEquals(frontDesk.id(),number(business.row("select user_id from service_order where id=?",cue.get("id")).get("user_id")));
 }
 @Test void frontDeskAccountAppearsInMembersAndOpensItsReceipts(){
  long play=open();
  String authorization="Bearer "+api.token(admin,"ADMIN").get("token");
  var members=(List<Map<String,Object>>)api.members(authorization);
  var account=members.stream().filter(row->number(row.get("id"))==frontDesk.id()).findFirst().orElseThrow();
  assertEquals(FrontDeskAccount.NAME,account.get("username"));
  assertEquals(FrontDeskAccount.ROLE,account.get("role"));
  assertEquals("前台账户",account.get("tier"));
  assertEquals(1,number(account.get("visits")));
  var detail=(Map<String,Object>)api.memberDetail(authorization,frontDesk.id(),1);
  var receipts=(List<Map<String,Object>>)detail.get("receipts");
  assertEquals(play,number(receipts.get(0).get("id")));
 }
 @Test void assigningAfterPrepaymentTransfersAllServicesWithoutChangingPayments(){
  long play=open();
  var coach=commerce.book(admin,true,item("coach-tangtang"),play,UUID.randomUUID().toString(),2,true);
  var cue=commerce.book(admin,true,item("peri-tianwang"),play,UUID.randomUUID().toString(),2,true);
  assertEquals(true,ledger.bill(admin,true,play).get("canAssignCustomer"));
  ledger.assignCustomer(admin,true,play,customer);
  assertEquals(customer,number(business.row("select user_id from play_order where id=?",play).get("user_id")));
  for(var before:List.of(coach,cue)){
   var after=business.row("select * from service_order where id=?",before.get("id"));
   assertEquals(customer,number(after.get("user_id")));
   for(String field:List.of("amount","payment_method","settled_at","mock_prepaid","status","started_at","booked_hours"))assertEquals(before.get(field),after.get(field),field);
  }
  assertEquals(2,ledger.services(customer,false).size());
  assertEquals(false,ledger.bill(customer,false,play).get("canAssignCustomer"));
  ledger.assignCustomer(admin,true,play,customer);
  assertThrows(IllegalArgumentException.class,()->ledger.assignCustomer(admin,false,play,customer));
 }
 @Test void simultaneousBookingAndAssignmentKeepOneCustomerAcrossTheSession()throws Exception{
  long play=open();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{
   var booking=pool.submit(()->{gate.await();return commerce.book(admin,true,item("coach-tangtang"),play,UUID.randomUUID().toString(),1,true);});
   var assignment=pool.submit(()->{gate.await();return ledger.assignCustomer(admin,true,play,customer);});
   gate.countDown();booking.get(15,TimeUnit.SECONDS);assignment.get(15,TimeUnit.SECONDS);
   assertEquals(customer,number(business.row("select user_id from play_order where id=?",play).get("user_id")));
   assertEquals(1,business.jdbc().queryForObject("select count(*) from service_order where play_order_id=? and user_id=?",Integer.class,play,customer));
  }finally{pool.shutdownNow();}
 }
}
