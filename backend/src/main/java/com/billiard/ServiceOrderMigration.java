package com.billiard;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Component @Order(1)
public class ServiceOrderMigration implements ApplicationRunner {
 final JdbcTemplate db;
 ServiceOrderMigration(JdbcTemplate db){this.db=db;}
 public void run(ApplicationArguments args)throws Exception{
  boolean hasPersonalRate;
  try(var c=db.getDataSource().getConnection();var rs=c.getMetaData().getColumns(c.getCatalog(),null,"service_item","personal_rate")){hasPersonalRate=rs.next();}
  if(!hasPersonalRate)db.execute("alter table service_item add column personal_rate DECIMAL(10,2)");
  var columns=new LinkedHashMap<String,String>();columns.put("play_order_id","BIGINT");columns.put("created_by","BIGINT");columns.put("settled_at","TIMESTAMP");columns.put("payment_method","VARCHAR(20)");columns.put("booked_hours","INT");columns.put("mock_prepaid","BOOLEAN NOT NULL DEFAULT FALSE");
  for(var column:columns.entrySet()){boolean exists;try(var c=db.getDataSource().getConnection();var rs=c.getMetaData().getColumns(c.getCatalog(),null,"service_order",column.getKey())){exists=rs.next();}if(!exists)db.execute("alter table service_order add column "+column.getKey()+" "+column.getValue());}
 }
}
