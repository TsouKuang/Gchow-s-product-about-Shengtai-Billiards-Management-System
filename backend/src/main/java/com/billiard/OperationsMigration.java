package com.billiard;

import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @Order(5)
public class OperationsMigration implements ApplicationRunner {
 final JdbcTemplate db;
 OperationsMigration(JdbcTemplate db){this.db=db;}
 public void run(ApplicationArguments args)throws Exception {
  add("coach_grade","hourly_rate","DECIMAL(10,2)");
  add("service_order","hourly_rate","DECIMAL(10,2)");
  add("store_expense","cancelled","BOOLEAN NOT NULL DEFAULT FALSE");
  add("store_expense","version","INT NOT NULL DEFAULT 0");
  db.execute("create table if not exists expense_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,expense_id BIGINT NOT NULL,operator_id BIGINT NOT NULL,action VARCHAR(20) NOT NULL,before_value VARCHAR(4000),after_value VARCHAR(4000),created_at TIMESTAMP NOT NULL)");
 }
 void add(String table,String column,String type)throws Exception {
  try(var connection=db.getDataSource().getConnection();var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,table,column)){
   if(!columns.next())db.execute("alter table "+table+" add column "+column+" "+type);
  }
 }
}
