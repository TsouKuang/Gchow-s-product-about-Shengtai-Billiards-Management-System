package com.billiard;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
@Component @Order(-1)
public class Catalog implements ApplicationRunner {
 private final JdbcTemplate db;
 @Value("${app.demo-enabled}") boolean demo;
 public Catalog(JdbcTemplate db){this.db=db;}
 public void run(ApplicationArguments args) throws Exception {
  boolean exists=false;
  try(var c=db.getDataSource().getConnection();var rs=c.getMetaData().getColumns(c.getCatalog(),null,"billiard_table","model_id")){exists=rs.next();}
  if(!exists)db.execute("ALTER TABLE billiard_table ADD COLUMN model_id BIGINT");
  Object[][] models={
   {1L,"中式八球","乔氏","金腿 Q8","/assets/catalog/joy-gold-cutout.png","https://www.joy147.com/newsinfo/106285.html"},
   {2L,"中式八球","乔氏","玫瑰金腿","/assets/catalog/joy-rose-cutout.png","https://www.joy147.com/gsjj"},
   {3L,"中式八球","康溪盛世","KX-08","/assets/catalog/kangxi-kx08-cutout.png","https://www.kxss147.com/zh/products/view/?c=kx08"},
   {4L,"中式八球","康溪盛世","KX-11","/assets/catalog/kangxi-blaze-cutout.png","https://www.kxss147.com/zh/products/view/?c=kx11"},
   {5L,"美式九球","星牌","XW136-9B","/assets/catalog/star-nine-cutout.png","https://www.xingpaishop.com/product/xw136-9b"},
   {6L,"斯诺克","星牌","S101","/assets/catalog/star-s101-cutout.png","https://www.xingpaishop.com/product/xw101-12s-2"}};
  for(var m:models){if(db.queryForObject("select count(*) from table_model where id=?",Integer.class,m[0])==0)db.update("insert into table_model(id,type,brand,model_name,image_path,source_url) values(?,?,?,?,?,?)",m);}
  if(demo)for(int i=1;i<=28;i++){
   long mid=(i==7||i==8||i>=27)?6:((i>=21&&i<=24)?5:(new long[]{1,1,2,2,3,4}[(i-1)%6]));
   var model=db.queryForMap("select * from table_model where id=?",mid);String no=String.format("T%02d",i);
   if(db.queryForObject("select count(*) from billiard_table where table_no=?",Integer.class,no)==0)db.update("insert into billiard_table(table_no,type,status,model_id) values(?,?,'IDLE',?)",no,model.get("type"),mid);
   else db.update("update billiard_table set model_id=? where table_no=? and model_id is null",mid,no);
  }
 }
}
