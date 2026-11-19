package com.billiard;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:profiles;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class ServiceProfilesTest {
 @Autowired Business b;@Autowired Commerce c;@Autowired ServiceProfiles profiles;@Autowired MockMvc mvc; long playId;
 long user(){return b.jdbc().queryForObject("select id from app_user where username='demo-customer'",Long.class);}
 long coach(String key){return b.jdbc().queryForObject("select id from service_item where catalog_key=?",Long.class,key);}
 @BeforeEach void clear(){b.jdbc().update("delete from service_order");b.jdbc().update("delete from play_order");b.jdbc().update("update billiard_table set status='IDLE',active_order_id=null");playId=((Number)b.open(user(),false,28,1,UUID.randomUUID().toString(),"MOCK").get("id")).longValue();b.jdbc().update("update coach_grade set rate=1 where stars=3");}
 @Test void catalogHasUniqueImagesAndGenderDistribution(){var items=c.items(false);assertEquals(54,items.size());assertEquals(36,items.stream().filter(i->i.get("kind").equals("CUE")).count());assertEquals(14,items.stream().filter(i->"女".equals(i.get("gender"))).count());assertEquals(4,items.stream().filter(i->"男".equals(i.get("gender"))).count());assertEquals(54,items.stream().map(i->i.get("image_path")).distinct().count());for(var item:items)assertTrue(getClass().getResource(item.get("image_path").toString().replace("/assets/","/static/assets/"))!=null);}
 @Test void gradePriceAppliesToNewBookingsAndPreservesSnapshot(){long first=coach("coach-tangtang"),second=coach("coach-mianmian");c.book(user(),false,first,playId,UUID.randomUUID().toString());profiles.grade(user(),3,new BigDecimal("1.80"));c.book(user(),false,second,playId,UUID.randomUUID().toString());assertEquals(new BigDecimal("1.00"),b.row("select rate from service_order where item_id=?",first).get("rate"));assertEquals(new BigDecimal("1.80"),b.row("select rate from service_order where item_id=?",second).get("rate"));assertEquals(new BigDecimal("1.80"),c.items(false).stream().filter(i->((Number)i.get("id")).longValue()==first).findFirst().orElseThrow().get("rate"));}
 @Test void profileValidationAndSeedNeverOverwriteEdits()throws Exception{long id=coach("coach-tangtang");var p=new ServiceProfiles.Profile("糖糖","已修改的门店介绍",null,1,true,"/assets/services/coach-tangtang.png","女",4,24,165,49,"耐心","中式八球");profiles.save(user(),id,p);profiles.seed();var row=b.row("select * from service_item where id=?",id);assertEquals("已修改的门店介绍",row.get("description"));assertEquals(4,((Number)row.get("star_level")).intValue());assertEquals(new BigDecimal("1.50"),c.items(false).stream().filter(i->((Number)i.get("id")).longValue()==id).findFirst().orElseThrow().get("rate"));var invalid=new ServiceProfiles.Profile("糖糖","介绍",BigDecimal.ONE,1,true,"/assets/services/coach-tangtang.png","女",4,17,165,49,"耐心","中式八球");assertThrows(IllegalArgumentException.class,()->profiles.save(user(),id,invalid));b.jdbc().update("update service_item set star_level=3 where id=?",id);}
 @Test void bookedCoachHoursDetermineChargeEvenWhenEndedEarly(){
  var order=c.book(user(),false,coach("coach-tangtang"),playId,UUID.randomUUID().toString(),2);
  assertEquals(2,((Number)order.get("booked_hours")).intValue());
  long id=((Number)order.get("id")).longValue();
  c.action(user(),false,id,"finish");
  assertEquals(new BigDecimal("120.00"),b.row("select amount from service_order where id=?",id).get("amount"));
 }
 @Test void prepaidCueStartsWithoutApprovalAndCannotBeChargedAgain(){
  long cue=b.jdbc().queryForObject("select min(id) from service_item where kind='CUE'",Long.class);
  String key=UUID.randomUUID().toString();
  var order=c.book(user(),false,cue,playId,key,2,true);
  long id=((Number)order.get("id")).longValue();
  assertEquals("ACTIVE",order.get("status"));
  assertEquals(((BigDecimal)order.get("rate")).multiply(BigDecimal.valueOf(2)),order.get("amount"));
  assertEquals(id,((Number)c.book(user(),false,cue,playId,key,2,true).get("id")).longValue());
  c.action(user(),true,id,"finish");
  assertEquals("SETTLED",b.row("select status from service_order where id=?",id).get("status"));
 }
 @Test void gradeEditingRequiresAdministrator()throws Exception{mvc.perform(put("/api/coach-grades/3").contentType("application/json").content("{\"rate\":2}")).andExpect(status().isUnauthorized());String body=mvc.perform(post("/api/auth/demo")).andReturn().getResponse().getContentAsString();String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();mvc.perform(put("/api/coach-grades/3").header("Authorization","Bearer "+token).contentType("application/json").content("{\"rate\":2}")).andExpect(status().isForbidden());}
}

