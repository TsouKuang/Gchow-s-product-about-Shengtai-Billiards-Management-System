package com.billiard;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
class BusinessTest {
 @Autowired Business b;
 long user(){return b.jdbc().queryForObject("select id from app_user where username='demo-customer'",Long.class);}
 @BeforeEach void clear(){b.jdbc().update("update billiard_table set status='IDLE',active_order_id=null");b.jdbc().update("delete from play_order");b.jdbc().update("update play_package set price=38 where id=1");}
 @Test void duplicateAndSnapshot(){String key=UUID.randomUUID().toString();var o=b.open(user(),false,1,1,key,"MOCK");var again=b.open(user(),false,1,1,key,"MOCK");assertEquals(o.get("id"),again.get("id"));assertEquals(1,b.jdbc().queryForObject("select count(*) from play_order",Integer.class));b.jdbc().update("update play_package set price=40 where id=1");assertEquals(new java.math.BigDecimal("38.00"),b.row("select * from play_order").get("amount"));}
 @Test void concurrentOpen()throws Exception{var pool=Executors.newFixedThreadPool(6);try{var gate=new CountDownLatch(1);List<Future<Boolean>> jobs=new ArrayList<>();long uid=user();for(int i=0;i<6;i++)jobs.add(pool.submit(()->{gate.await();try{b.open(uid,false,2,1,UUID.randomUUID().toString(),"MOCK");return true;}catch(IllegalArgumentException e){return false;}}));gate.countDown();int ok=0;for(var f:jobs)if(f.get(20,TimeUnit.SECONDS))ok++;assertEquals(1,ok);assertEquals(1,b.jdbc().queryForObject("select count(*) from play_order",Integer.class));}finally{pool.shutdownNow();}}
 @Test void ownershipAndDirectReuse(){var o=b.open(user(),false,1,1,UUID.randomUUID().toString(),"MOCK");long id=((Number)o.get("id")).longValue();assertThrows(IllegalArgumentException.class,()->b.finish(999,false,id));b.finish(user(),false,id);b.finish(user(),false,id);assertEquals("IDLE",b.row("select * from billiard_table where id=1").get("status"));b.status(user(),1,"IDLE");assertEquals("IDLE",b.row("select * from billiard_table where id=1").get("status"));}
 @Test void maintenanceAndPayment(){b.status(user(),1,"MAINTENANCE");assertThrows(IllegalArgumentException.class,()->b.open(user(),false,1,1,UUID.randomUUID().toString(),"MOCK"));assertThrows(IllegalArgumentException.class,()->b.open(user(),false,2,1,UUID.randomUUID().toString(),"CASH"));assertEquals(0,b.jdbc().queryForObject("select count(*) from play_order",Integer.class));}
}
