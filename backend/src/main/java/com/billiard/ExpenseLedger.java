package com.billiard;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseLedger {
 final Business business;
 final ObjectMapper mapper;
 ExpenseLedger(Business business,ObjectMapper mapper){this.business=business;this.mapper=mapper;}
 public record Entry(String category,String date,BigDecimal amount,String note,Integer version){}
 private LocalDate validate(Entry entry){
  if(entry.category()==null||entry.category().isBlank()||entry.category().strip().length()>20||entry.category().codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("支出类别须为1至20个字符");
  Commerce.money(entry.amount());
  LocalDate date;
  try{date=LocalDate.parse(entry.date());}catch(Exception exception){throw new IllegalArgumentException("请选择有效的支出日期");}
  if(date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))||entry.note()==null||entry.note().isBlank()||entry.note().length()>200)throw new IllegalArgumentException("请选择已发生日期并填写支出说明");
  return date;
 }
 @Transactional public void create(long operator,Entry entry){
  var date=validate(entry);
  business.jdbc().update("insert into store_expense(category,expense_date,amount,note,created_by) values(?,?,?,?,?)",entry.category().strip(),Date.valueOf(date),entry.amount(),entry.note().strip(),operator);
 }
 @Transactional public void update(long operator,long id,Entry entry){
  var date=validate(entry);
  var before=lock(id,entry.version());
  business.jdbc().update("update store_expense set category=?,expense_date=?,amount=?,note=?,version=version+1 where id=?",entry.category().strip(),Date.valueOf(date),entry.amount(),entry.note().strip(),id);
  audit(operator,id,"UPDATE",before);
 }
 @Transactional public void cancel(long operator,long id,Integer version){
  var before=business.row("select * from store_expense where id=? for update",id);
  if(Boolean.TRUE.equals(before.get("cancelled")))return;
  checkVersion(before,version);
  business.jdbc().update("update store_expense set cancelled=true,version=version+1 where id=?",id);
  audit(operator,id,"CANCEL",before);
 }
 private Map<String,Object> lock(long id,Integer version){
  var row=business.row("select * from store_expense where id=? for update",id);
  if(Boolean.TRUE.equals(row.get("cancelled")))throw new IllegalArgumentException("该笔支出已撤销，请重新登记");
  checkVersion(row,version);
  return row;
 }
 private void checkVersion(Map<String,Object> row,Integer version){
  if(version==null||version!=((Number)row.get("version")).intValue())throw new IllegalArgumentException("该支出已更新，请刷新明细后重试");
 }
 private void audit(long operator,long id,String action,Map<String,Object> before){
  try{
   business.jdbc().update("insert into expense_history(expense_id,operator_id,action,before_value,after_value,created_at) values(?,?,?,?,?,?)",id,operator,action,mapper.writeValueAsString(before),mapper.writeValueAsString(business.row("select * from store_expense where id=?",id)),Commerce.now());
   business.log(operator,"EXPENSE_"+action,id);
  }catch(com.fasterxml.jackson.core.JsonProcessingException exception){throw new IllegalStateException("保存支出变更记录失败",exception);}
 }
}
