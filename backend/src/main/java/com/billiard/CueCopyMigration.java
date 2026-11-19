package com.billiard;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.boot.*;
import java.math.BigDecimal;
@Component @Order(4)
public class CueCopyMigration implements ApplicationRunner {
 final Business b;
 CueCopyMigration(Business b){this.b=b;}
 public void run(ApplicationArguments args){
  for(var item:b.jdbc().queryForList("select id,brand,name,rate,description from service_item where kind='CUE' and deleted=false")){
   String old=String.valueOf(item.get("description"));
   if(!old.isBlank()&&!old.equals("null")&&!old.startsWith("演示库存"))continue;
   String name=String.valueOf(item.get("name")),brand=String.valueOf(item.get("brand"));
   String price=new BigDecimal(item.get("rate").toString()).stripTrailingZeros().toPlainString();
   String copy=brand+" · "+name+"。\n租金 ¥"+price+" / 小时，可按本次练习时长预约。\n到店可先查看实物，确认重量与握持手感后领取；使用后交前台验收。\n如需购买此型号，售价与配置请向门店咨询。";
   b.jdbc().update("update service_item set description=? where id=? and description=?",copy,item.get("id"),item.get("description"));
  }
 }
}
