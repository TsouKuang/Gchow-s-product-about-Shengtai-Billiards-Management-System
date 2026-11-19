package com.billiard;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.boot.*;
import org.springframework.transaction.annotation.Transactional;

@Component @Order(0)
public class ServiceProfiles implements ApplicationRunner {
 final Business b;final Commerce commerce;
 ServiceProfiles(Business b,Commerce commerce){this.b=b;this.commerce=commerce;}
 public void run(ApplicationArguments args)throws Exception {
  var columns=new LinkedHashMap<String,String>();columns.put("catalog_key","VARCHAR(60)");columns.put("image_path","VARCHAR(255)");columns.put("gender","VARCHAR(4)");columns.put("star_level","INT");columns.put("age","INT");columns.put("height_cm","INT");columns.put("weight_kg","INT");columns.put("personality","VARCHAR(120)");columns.put("specialties","VARCHAR(200)");
  for(var entry:columns.entrySet()){boolean exists;try(var c=b.jdbc().getDataSource().getConnection();var rs=c.getMetaData().getColumns(c.getCatalog(),null,"service_item",entry.getKey())){exists=rs.next();}if(!exists)b.jdbc().execute("alter table service_item add column "+entry.getKey()+" "+entry.getValue());}
  String[] names={"三星助教","四星高级助教","五星金牌助教","六星首席助教"};String[] rates={"1.00","1.50","2.00","3.00"};
  for(int n=0;n<4;n++)if(b.jdbc().queryForObject("select count(*) from coach_grade where stars=?",Integer.class,n+3)==0)b.jdbc().update("insert into coach_grade(stars,title,rate) values(?,?,?)",n+3,names[n],new BigDecimal(rates[n]));
 }
 @Transactional public void seed()throws Exception {
  if(b.jdbc().queryForObject("select count(*) from app_migration where id='services-v08'",Integer.class)>0)return;
  var mapper=new ObjectMapper();var rows=mapper.readTree(getClass().getResourceAsStream("/service-catalog-v08.json"));
  for(var item:rows){String key=item.path("key").asText(),kind=item.path("kind").asText();long id;
   var existing=b.jdbc().queryForList("select id from service_item where brand=? and name=? and kind=?",item.path("brand").asText(),item.path("name").asText(),kind);
   if(existing.isEmpty()){b.jdbc().update("insert into service_item(kind,brand,name,description,rate,stock,enabled,source_url) values(?,?,?,?,?,?,?,?)",kind,item.path("brand").asText(),item.path("name").asText(),item.path("description").asText(),item.path("rate").decimalValue(),item.path("stock").asInt(),true,item.path("source_url").asText());id=b.jdbc().queryForObject("select max(id) from service_item",Long.class);}
   else id=((Number)existing.get(0).get("id")).longValue();
   b.jdbc().update("update service_item set catalog_key=?,image_path=?,gender=?,star_level=?,age=?,height_cm=?,weight_kg=?,personality=?,specialties=? where id=?",key,item.path("image_path").asText(),kind.equals("COACH")?item.path("gender").asText():null,kind.equals("COACH")?item.path("star_level").asInt():null,kind.equals("COACH")?item.path("age").asInt():null,kind.equals("COACH")?item.path("height_cm").asInt():null,kind.equals("COACH")?item.path("weight_kg").asInt():null,kind.equals("COACH")?item.path("personality").asText():null,kind.equals("COACH")?item.path("specialties").asText():null,id);
  }
  // Keep legacy IDs and historical references; only retire the old generic placeholders.
  b.jdbc().update("update service_item set enabled=false where catalog_key is null and (kind='COACH' or name='门店型号待确认')");
  b.jdbc().update("insert into app_migration(id) values('services-v08')");
 }
 public record Profile(String name,String description,BigDecimal rate,int stock,boolean enabled,String imagePath,String gender,Integer starLevel,Integer age,Integer heightCm,Integer weightKg,String personality,String specialties){}
 @Transactional public void save(long uid,long id,Profile p){
  var item=b.row("select * from service_item where id=? for update",id);String path=p.imagePath();if(path==null||!path.matches("/assets/(services|uploads)/[a-z0-9-]+\\.(png|jpg|webp)"))throw new IllegalArgumentException("请上传有效的服务图片");
  boolean coach=item.get("kind").equals("COACH");BigDecimal rate=p.rate();
  if(coach){if(p.gender()==null||!List.of("男","女").contains(p.gender())||p.starLevel()==null||p.starLevel()<3||p.starLevel()>6||p.age()==null||p.age()<18||p.age()>75||p.heightCm()==null||p.heightCm()<130||p.heightCm()>230||p.weightKg()==null||p.weightKg()<35||p.weightKg()>180||p.personality()==null||p.personality().isBlank()||p.personality().length()>120||p.specialties()==null||p.specialties().isBlank()||p.specialties().length()>200)throw new IllegalArgumentException("请填写有效的成年助教基本信息、三星至六星等级、性格及擅长项目");rate=(BigDecimal)b.row("select rate from coach_grade where stars=?",p.starLevel()).get("rate");}
  commerce.edit(uid,id,p.name(),p.description(),rate,p.stock(),p.enabled());
  if(coach)b.jdbc().update("update service_item set personal_rate=null where id=?",id);
  b.jdbc().update("update service_item set image_path=?,gender=?,star_level=?,age=?,height_cm=?,weight_kg=?,personality=?,specialties=? where id=?",path,coach?p.gender():null,coach?p.starLevel():null,coach?p.age():null,coach?p.heightCm():null,coach?p.weightKg():null,coach?p.personality():null,coach?p.specialties():null,id);b.log(uid,"SERVICE_PROFILE",id);
 }
 @Transactional public void grade(long uid,int stars,BigDecimal rate){Commerce.money(rate);b.row("select * from coach_grade where stars=? for update",stars);b.jdbc().update("update coach_grade set rate=?,hourly_rate=null where stars=?",rate,stars);b.log(uid,"COACH_GRADE_PRICE",stars);}
 @Transactional public void gradeHourly(long uid,int stars,BigDecimal hourly){
  Commerce.money(hourly);
  b.row("select * from coach_grade where stars=? for update",stars);
  var minute=hourly.divide(BigDecimal.valueOf(60),2,java.math.RoundingMode.HALF_UP);
  if(minute.signum()==0)minute=new BigDecimal("0.01");
  b.jdbc().update("update coach_grade set rate=?,hourly_rate=? where stars=?",minute,hourly,stars);
  b.log(uid,"COACH_GRADE_PRICE",stars);
 }
}

