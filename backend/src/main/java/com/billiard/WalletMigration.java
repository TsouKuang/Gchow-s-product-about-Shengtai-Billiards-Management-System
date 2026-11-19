package com.billiard;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Component @Order(2)
public class WalletMigration implements ApplicationRunner {
 final JdbcTemplate db;final Wallet wallet;
 WalletMigration(JdbcTemplate db,Wallet wallet){this.db=db;this.wallet=wallet;}
 public void run(ApplicationArguments args)throws Exception{
  boolean exists;try(var c=db.getDataSource().getConnection();var rs=c.getMetaData().getColumns(c.getCatalog(),null,"app_user","customer_code")){exists=rs.next();}
  if(!exists)db.execute("alter table app_user add column customer_code VARCHAR(6) UNIQUE");
  db.execute("create table if not exists wallet_account(user_id BIGINT PRIMARY KEY,balance DECIMAL(12,2) NOT NULL DEFAULT 0,pin_hash VARCHAR(100),failed_attempts INT NOT NULL DEFAULT 0,locked_until TIMESTAMP NULL)");
  db.execute("create table if not exists wallet_transaction(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,request_key VARCHAR(80) NOT NULL,fingerprint VARCHAR(200) NOT NULL,kind VARCHAR(12) NOT NULL,channel VARCHAR(20) NOT NULL,amount DECIMAL(12,2) NOT NULL,balance_after DECIMAL(12,2) NOT NULL,title VARCHAR(160) NOT NULL,created_at TIMESTAMP NOT NULL,UNIQUE(user_id,request_key))");
  db.execute("create table if not exists wallet_pin_recovery(user_id BIGINT PRIMARY KEY,failed_attempts INT NOT NULL DEFAULT 0,locked_until TIMESTAMP NULL)");
  for(long uid:db.queryForList("select id from app_user where customer_code is null",Long.class))wallet.code(uid);
 }
}
