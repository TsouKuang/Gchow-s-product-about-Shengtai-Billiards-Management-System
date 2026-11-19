package com.billiard;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:wallet;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"})
class WalletTest {
 @Autowired org.springframework.jdbc.core.JdbcTemplate db; @Autowired Wallet wallet;@Autowired WalletCheckout checkout;@Autowired Business business;@Autowired Commerce commerce;@Autowired Ledger ledger;
 @Autowired Api api;
 long customer,admin;
 String key(){return UUID.randomUUID().toString();}
 @BeforeEach void setup(){
  db.update("delete from wallet_pin_recovery");
  db.update("delete from wallet_transaction");db.update("delete from wallet_account");db.update("delete from service_order");db.update("delete from play_order");db.update("update billiard_table set status='IDLE',active_order_id=null");
  customer=db.queryForObject("select id from app_user where username='demo-customer'",Long.class);admin=db.queryForObject("select id from app_user where username='admin'",Long.class);
  wallet.setPin(customer,"928416",null);
 }
 WalletCheckout.Request play(long table,String key){return new WalletCheckout.Request("PLAY",table,1,0,0,null,key,"928416");}
 @Test void scanPaymentRequiresCorrectPinAndReplaysWithoutDebitingWallet(){
  String authorization="Bearer "+api.token(customer,"CUSTOMER").get("token");
  String requestKey=key();
  var wrong=new WalletCheckout.Request("PLAY",1,1,0,0,null,requestKey,"123456");
  assertThrows(Wallet.PinError.class,()->api.payScan(authorization,wrong));
  assertEquals(0,db.queryForObject("select count(*) from play_order",Integer.class));
  api.payScan(authorization,play(1,requestKey));
  api.payScan(authorization,play(1,requestKey));
  assertEquals(1,db.queryForObject("select count(*) from play_order",Integer.class));
  assertEquals(0,db.queryForObject("select count(*) from wallet_transaction",Integer.class));
  assertEquals(new BigDecimal("0.00"),wallet.summary(customer).get("balance"));
 }
 @Test void pinChangeChecksOldPasswordWithoutChangingMoney(){
  String authorization="Bearer "+api.token(customer,"CUSTOMER").get("token");
  wallet.topup(customer,new BigDecimal("100"),"WECHAT",key());
  assertThrows(Wallet.PinError.class,()->api.changePin(authorization,new Api.PinChange("111111","123456")));
  api.changePin(authorization,new Api.PinChange("928416","123456"));
  wallet.authorize(customer,"123456");
  assertThrows(Wallet.PinError.class,()->wallet.authorize(customer,"928416"));
  assertThrows(Wallet.PinError.class,()->wallet.authorize(customer,"000000"));
  assertEquals(new BigDecimal("100.00"),wallet.summary(customer).get("balance"));
 }
 @Test void recoveryRequiresLoginIdentityAndPersistsIndependentLockout(){
  String loginPassword="RecoveryLogin123";
  db.update("update app_user set password_hash=? where id=?",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(loginPassword),customer);
  String authorization="Bearer "+api.token(customer,"CUSTOMER").get("token");
  wallet.topup(customer,new BigDecimal("100"),"WECHAT",key());
  for(int i=0;i<3;i++)assertThrows(Wallet.PinError.class,()->wallet.authorize(customer,"000000"));
  assertTrue((Boolean)wallet.summary(customer).get("locked"));
  for(int i=0;i<3;i++)assertThrows(Wallet.PinError.class,()->api.verifyRecovery(authorization,new Api.PinRecovery("wrong",null)));
  assertTrue((Boolean)wallet.summary(customer).get("recoveryLocked"));
  assertThrows(Wallet.PinError.class,()->api.recoverPin(authorization,new Api.PinRecovery(loginPassword,"654321")));
  db.update("update wallet_pin_recovery set locked_until=dateadd('MINUTE',-1,current_timestamp) where user_id=?",customer);
  api.recoverPin(authorization,new Api.PinRecovery(loginPassword,"654321"));
  wallet.authorize(customer,"654321");
  assertFalse((Boolean)wallet.summary(customer).get("locked"));
  assertEquals(new BigDecimal("100.00"),wallet.summary(customer).get("balance"));
  assertEquals(1,db.queryForObject("select count(*) from wallet_transaction where user_id=?",Integer.class,customer));
 }
 @Test void summaryReportsMonthlyTotalsAndRechargeCapability(){
  String authorization="Bearer "+api.token(customer,"CUSTOMER").get("token");
  wallet.topup(customer,new BigDecimal("200"),"WECHAT",key());
  var order=checkout.pay(customer,play(1,key()));
  var summary=wallet.summary(customer);
  assertEquals(new BigDecimal("200.00"),summary.get("monthIncome"));
  assertEquals(order.get("amount"),summary.get("monthExpense"));
  assertTrue((Boolean)((Map<?,?>)api.wallet(authorization)).get("topupEnabled"));
  boolean before=api.demo;
  try{
   api.demo=false;
   assertFalse((Boolean)((Map<?,?>)api.wallet(authorization)).get("topupEnabled"));
   assertThrows(IllegalArgumentException.class,()->api.topup(authorization,new Api.TopupRequest(BigDecimal.TEN,"WECHAT",key())));
  }finally{api.demo=before;}
 }
 @Test void topupAndPaymentRetriesDoNotDuplicateMoney(){
  String topup=key();wallet.topup(customer,new BigDecimal("200"),"WECHAT",topup);wallet.topup(customer,new BigDecimal("200.00"),"WECHAT",topup);
  assertThrows(IllegalArgumentException.class,()->wallet.topup(customer,new BigDecimal("300"),"WECHAT",topup));
  var request=play(1,key());wallet.authorize(customer,request.pin());var first=checkout.pay(customer,request);var replay=checkout.pay(customer,request);
  assertEquals(first.get("amount"),replay.get("amount"));assertEquals(2,db.queryForObject("select count(*) from wallet_transaction",Integer.class));
  assertEquals(new BigDecimal("200.00").subtract((BigDecimal)first.get("amount")),wallet.summary(customer).get("balance"));
 }
 @Test void threeWrongPasswordsPersistAndLockEvenWithCorrectPassword(){
  for(int i=1;i<=3;i++){final int attempt=i;var error=assertThrows(Wallet.PinError.class,()->wallet.authorize(customer,"000000"));assertTrue(error.getMessage().contains(attempt==3?"锁定":"还可尝试"));}
  assertTrue((Boolean)wallet.summary(customer).get("locked"));assertThrows(Wallet.PinError.class,()->wallet.authorize(customer,"928416"));
  db.update("update wallet_account set locked_until=dateadd('MINUTE',-1,current_timestamp) where user_id=?",customer);wallet.authorize(customer,"928416");
  assertFalse((Boolean)wallet.summary(customer).get("locked"));assertEquals(0,db.queryForObject("select failed_attempts from wallet_account where user_id=?",Integer.class,customer));
 }
 @Test void insufficientFundsRollbackOrdersAndServiceInventory(){
  assertThrows(IllegalArgumentException.class,()->checkout.pay(customer,play(1,key())));assertEquals("IDLE",business.row("select status from billiard_table where id=1").get("status"));
  var play=business.open(customer,false,1,1,key(),"MOCK");long id=Ledger.number(play.get("id"));long item=db.queryForObject("select min(id) from service_item where kind='CUE' and enabled=true",Long.class);
  assertThrows(IllegalArgumentException.class,()->checkout.pay(customer,new WalletCheckout.Request("SERVICE",0,0,id,item,2,key(),"928416")));
  assertEquals(0,db.queryForObject("select count(*) from service_order",Integer.class));
 }
 @Test void concurrentPurchasesCannotOverspend()throws Exception{
  wallet.topup(customer,new BigDecimal("50"),"ALIPAY",key());var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{List<Future<Boolean>> calls=new ArrayList<>();for(int table=1;table<=2;table++){long target=table;calls.add(pool.submit(()->{gate.await();try{checkout.pay(customer,play(target,key()));return true;}catch(IllegalArgumentException e){return false;}}));}gate.countDown();int successes=0;for(var call:calls)if(call.get(20,TimeUnit.SECONDS))successes++;assertEquals(1,successes);assertTrue(((BigDecimal)wallet.summary(customer).get("balance")).signum()>=0);}finally{pool.shutdownNow();}
 }
 @Test void publicCodesAreUniqueStableAndServiceCashIsIndependent(){
  var codes=new HashSet<String>();for(int i=0;i<80;i++){String name="code-"+key();db.update("insert into app_user(username,role) values(?,'CUSTOMER')",name);long id=db.queryForObject("select id from app_user where username=?",Long.class,name);String code=wallet.code(id);assertTrue(code.matches("[1-9][0-9]{5}"));assertTrue(codes.add(code));assertEquals(code,wallet.code(id));}
  var play=business.open(admin,true,1,1,key(),"MOCK");long id=Ledger.number(play.get("id"));long item=db.queryForObject("select min(id) from service_item where kind='CUE' and enabled=true",Long.class);
  var service=commerce.bookPaid(admin,true,item,id,key(),2,"CASH");assertEquals("CASH",service.get("payment_method"));assertNotNull(service.get("settled_at"));
  var bill=ledger.bill(admin,true,id);assertEquals(Ledger.decimal(play.get("amount")).add(Ledger.decimal(service.get("amount"))),bill.get("totalAmount"));assertEquals(bill.get("totalAmount"),bill.get("paidAmount"));
 }
}
