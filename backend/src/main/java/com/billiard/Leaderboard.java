package com.billiard;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
@Service
public class Leaderboard {
 final JdbcTemplate db;
 Leaderboard(JdbcTemplate db){this.db=db;}
 public Map<String,Object> read(long uid,String period){return readAt(uid,period,LocalDateTime.now(ZoneId.of("Asia/Shanghai")));}
 Map<String,Object> readAt(long uid,String period,LocalDateTime now){
  if(!List.of("month","year").contains(period))throw new IllegalArgumentException("请选择月度或年度排行榜");
  var from=period.equals("year")?now.toLocalDate().withDayOfYear(1).atStartOfDay():now.toLocalDate().withDayOfMonth(1).atStartOfDay();
  var until=period.equals("year")?from.plusYears(1):from.plusMonths(1);
  var plays=db.queryForList("select p.user_id,p.started_at,p.ended_at from play_order p join app_user u on u.id=p.user_id where u.role='CUSTOMER' and u.username<>'demo-customer' and p.started_at<? and (p.ended_at is null or p.ended_at>?)",Timestamp.valueOf(until),Timestamp.valueOf(from));
  var seconds=new HashMap<Long,Long>();
  for(var play:plays){var start=((Timestamp)play.get("started_at")).toLocalDateTime();var end=play.get("ended_at")==null?now:((Timestamp)play.get("ended_at")).toLocalDateTime();if(start.isBefore(from))start=from;if(end.isAfter(until))end=until;if(end.isAfter(now))end=now;long elapsed=Math.max(0,Duration.between(start,end).getSeconds());if(elapsed>0)seconds.merge(((Number)play.get("user_id")).longValue(),elapsed,Long::sum);}
  var users=db.queryForList("select u.id,u.customer_code,coalesce(p.nickname,'圣泰球友') as nickname from app_user u left join customer_profile p on p.user_id=u.id where u.role='CUSTOMER' and u.username<>'demo-customer'");
  var ranked=new ArrayList<Map<String,Object>>();
  for(var user:users){long id=((Number)user.get("id")).longValue();long elapsed=seconds.getOrDefault(id,0L);if(elapsed==0)continue;var row=new LinkedHashMap<String,Object>();row.put("customerCode",user.get("customer_code"));row.put("nickname",user.get("nickname"));row.put("seconds",elapsed);row.put("minutes",elapsed/60);row.put("isMe",id==uid);ranked.add(row);}
  ranked.sort(Comparator.<Map<String,Object>>comparingLong(r->((Number)r.get("seconds")).longValue()).reversed().thenComparing(r->String.valueOf(r.get("customerCode"))));
  Map<String,Object> me=Map.of("rank",0,"seconds",0,"minutes",0);for(int i=0;i<ranked.size();i++){ranked.get(i).put("rank",i+1);if(Boolean.TRUE.equals(ranked.get(i).get("isMe")))me=ranked.get(i);}
  var result=new LinkedHashMap<String,Object>();result.put("period",period);result.put("label",period.equals("month")?from.toLocalDate().toString().substring(0,7):String.valueOf(from.getYear()));result.put("title",period.equals("month")?"月度球王":"年度球王");result.put("rows",ranked.stream().limit(100).toList());result.put("me",me);result.put("updatedAt",now.toString());return result;
 }
}
