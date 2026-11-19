package com.billiard;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
class FrontDeskAccount implements ApplicationRunner {
 static final String ROLE = "FRONT_DESK";
 static final String NAME = "前台消费账户";
 private final JdbcTemplate db;

 FrontDeskAccount(JdbcTemplate db) {
  this.db = db;
 }

 long id() {
  return db.queryForObject("select id from app_user where username=? and role=?", Long.class, NAME, ROLE);
 }

 @Override
 @Transactional
 public void run(ApplicationArguments args) {
  db.update("insert into app_user(username, role) values (?, ?) on duplicate key update username=username", NAME, ROLE);
  long account = id();
  db.update("""
   update service_order set user_id=?
   where user_id in (select id from app_user where role='ADMIN')
     and play_order_id in (
       select p.id from play_order p join app_user u on u.id=p.user_id
       where p.source='FRONT_DESK' and u.role='ADMIN'
     )
   """, account);
  db.update("""
   update play_order set user_id=?
   where source='FRONT_DESK' and user_id in (select id from app_user where role='ADMIN')
   """, account);
 }
}
