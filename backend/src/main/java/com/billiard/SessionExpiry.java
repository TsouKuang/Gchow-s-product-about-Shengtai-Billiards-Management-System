package com.billiard;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SessionExpiry {
 private static final Logger log=LoggerFactory.getLogger(SessionExpiry.class);
 final Business business;
 SessionExpiry(Business business){this.business=business;}
 @Scheduled(initialDelay=30000,fixedDelay=5000)
 public void expireDue(){
  for(var row:business.jdbc().queryForList("select id from play_order where status='ACTIVE' and expected_end_at<=?",Commerce.now())){
   long id=((Number)row.get("id")).longValue();
   try{business.expire(id);}catch(Exception exception){log.error("Failed to expire session {}",id,exception);}
  }
 }
}
