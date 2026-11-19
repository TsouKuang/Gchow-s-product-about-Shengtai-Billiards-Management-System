package com.billiard;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:commerce;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
class CommerceTest {
 @Autowired Business b;@Autowired Commerce c;
 long playId;
 Object book(long uid,long item,String key){return c.book(uid,false,item,playId,key);}
 long user(){return b.jdbc().queryForObject("select id from app_user where username='demo-customer'",Long.class);}
 long cue(){return b.jdbc().queryForObject("select min(id) from service_item where kind='CUE'",Long.class);}
 @BeforeEach void clear(){b.jdbc().update("update billiard_table set status='IDLE',active_order_id=null");b.jdbc().update("delete from service_order");b.jdbc().update("delete from play_order");b.jdbc().update("delete from package_price where scope_type='TABLE'");b.jdbc().update("update service_item set stock=1,rate=24,enabled=true where id=?",cue());playId=((Number)b.open(user(),false,28,1,UUID.randomUUID().toString(),"MOCK").get("id")).longValue();}
 @Test void tablePriceAndHistoricalSnapshot(){c.price(user(),"MODEL",1,1,new BigDecimal("38.00"));c.price(user(),"TABLE",1,1,new BigDecimal("66.00"));var o=b.open(user(),false,1,1,UUID.randomUUID().toString(),"MOCK");assertEquals(new BigDecimal("66.00"),o.get("amount"));assertEquals(new BigDecimal("38.00"),b.prices(2).get(0).get("price"));c.price(user(),"TABLE",1,1,new BigDecimal("77.00"));assertEquals(new BigDecimal("66.00"),b.row("select * from play_order where id=?",o.get("id")).get("amount"));c.price(user(),"TABLE",1,1,null);assertEquals(new BigDecimal("38.00"),b.prices(1).get(0).get("price"));}
 @Test void inventoryConcurrencyAndCancellation()throws Exception{var pool=Executors.newFixedThreadPool(4);long uid=user(),iid=cue();try{var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<Boolean>>();for(int i=0;i<4;i++)jobs.add(pool.submit(()->{gate.await();try{book(uid,iid,UUID.randomUUID().toString());return true;}catch(IllegalArgumentException e){return false;}}));gate.countDown();int count=0;for(var j:jobs)if(j.get(15,TimeUnit.SECONDS))count++;assertEquals(1,count);long id=b.jdbc().queryForObject("select id from service_order",Long.class);assertThrows(IllegalArgumentException.class,()->c.action(999,false,id,"cancel"));c.action(uid,false,id,"cancel");assertDoesNotThrow(()->book(uid,iid,UUID.randomUUID().toString()));}finally{pool.shutdownNow();}}
 @Test void lifecycleAndRateSnapshot(){String key=UUID.randomUUID().toString();book(user(),cue(),key);book(user(),cue(),key);assertEquals(1,b.jdbc().queryForObject("select count(*) from service_order",Integer.class));long id=b.jdbc().queryForObject("select id from service_order",Long.class);assertThrows(IllegalArgumentException.class,()->c.action(user(),false,id,"start"));assertThrows(IllegalArgumentException.class,()->c.action(user(),true,id,"settle"));b.jdbc().update("update service_item set rate=80 where id=?",cue());c.action(user(),true,id,"start");c.action(user(),true,id,"start");c.action(user(),true,id,"finish");c.action(user(),true,id,"finish");var o=b.row("select * from service_order where id=?",id);assertEquals(new BigDecimal("24.00"),o.get("amount"));c.action(user(),true,id,"settle");c.action(user(),true,id,"settle");assertEquals("SETTLED",b.row("select * from service_order where id=?",id).get("status"));}
 @Test void minuteAndHourBoundaries(){Instant start=Instant.parse("2026-11-03T00:00:00Z");assertEquals(1,Commerce.minutes(start,start.plusSeconds(60)));assertEquals(2,Commerce.minutes(start,start.plusMillis(60001)));assertEquals(new BigDecimal("4.00"),Commerce.amount("COACH",new BigDecimal("2"),2));assertEquals(new BigDecimal("24.00"),Commerce.amount("CUE",new BigDecimal("24"),60));assertEquals(new BigDecimal("48.00"),Commerce.amount("CUE",new BigDecimal("24"),61));}
}

