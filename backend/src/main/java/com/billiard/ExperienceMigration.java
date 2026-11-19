package com.billiard;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Component @Order(3)
public class ExperienceMigration implements ApplicationRunner {
 final JdbcTemplate db;final CatalogAdmin catalog;
 ExperienceMigration(JdbcTemplate db,CatalogAdmin catalog){this.db=db;this.catalog=catalog;}
 public void run(ApplicationArguments args)throws Exception{
  for(var entry:java.util.Map.of("service_code","VARCHAR(20) UNIQUE","deleted","BOOLEAN NOT NULL DEFAULT FALSE").entrySet()){
   boolean exists;try(var c=db.getDataSource().getConnection();var r=c.getMetaData().getColumns(c.getCatalog(),null,"service_item",entry.getKey())){exists=r.next();}
   if(!exists)db.execute("alter table service_item add column "+entry.getKey()+" "+entry.getValue());
  }
  db.execute("create table if not exists catalog_sequence(kind VARCHAR(10) PRIMARY KEY,next_value BIGINT NOT NULL)");
  for(String kind:java.util.List.of("COACH","CUE"))db.update("insert into catalog_sequence(kind,next_value) values(?,100) on duplicate key update kind=kind",kind);
  catalog.assignCodes();
  db.update("update billiard_table set status='IDLE' where status='CLEANING' and active_order_id is null");
  db.update("update customer_profile set gender='不显示' where gender='未填写'");
 }
}
