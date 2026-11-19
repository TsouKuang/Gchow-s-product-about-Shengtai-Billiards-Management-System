package com.billiard;

import java.math.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.*;

@Service
public class Wallet {
 final JdbcTemplate db; final SecureRandom random=new SecureRandom(); final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 Wallet(JdbcTemplate db){this.db=db;}
 public String code(long uid){
  String current=db.queryForObject("select customer_code from app_user where id=?",String.class,uid);if(current!=null)return current;
  for(int attempt=0;attempt<100;attempt++)try{
   String value=String.valueOf(100000+random.nextInt(900000));
   db.update("update app_user set customer_code=? where id=? and customer_code is null",value,uid);
   return db.queryForObject("select customer_code from app_user where id=?",String.class,uid);
  }catch(org.springframework.dao.DuplicateKeyException collision){}
  throw new IllegalStateException("暂时无法生成球友编号，请重试");
 }
 Map<String,Object> lock(long uid){
  db.update("insert into wallet_account(user_id,balance,failed_attempts) values(?,0,0) on duplicate key update user_id=user_id",uid);
  return db.queryForMap("select * from wallet_account where user_id=? for update",uid);
 }
 public Map<String,Object> summary(long uid){
  var rows=db.queryForList("select balance,pin_hash,failed_attempts,locked_until from wallet_account where user_id=?",uid);
  var result=new LinkedHashMap<String,Object>();result.put("balance",rows.isEmpty()?new BigDecimal("0.00"):rows.get(0).get("balance"));
  result.put("pinSet",!rows.isEmpty()&&rows.get(0).get("pin_hash")!=null);
  Timestamp until=rows.isEmpty()?null:(Timestamp)rows.get(0).get("locked_until");
  result.put("lockedUntil",until);result.put("locked",until!=null&&until.after(Commerce.now()));
  result.put("recoverySupported",db.queryForObject("select password_hash is not null from app_user where id=?",Boolean.class,uid));
  var recovery=db.queryForList("select locked_until from wallet_pin_recovery where user_id=?",uid);
  Timestamp recoveryUntil=recovery.isEmpty()?null:(Timestamp)recovery.get(0).get("locked_until");
  result.put("recoveryLocked",recoveryUntil!=null&&recoveryUntil.after(Commerce.now()));
  var from=Timestamp.valueOf(Commerce.now().toLocalDateTime().withDayOfMonth(1).toLocalDate().atStartOfDay());
  var month=db.queryForMap("select coalesce(sum(case when amount>0 then amount else 0 end),0) as income,coalesce(sum(case when amount<0 then -amount else 0 end),0) as expense from wallet_transaction where user_id=? and created_at>=?",uid,from);
  result.put("monthIncome",month.get("income"));
  result.put("monthExpense",month.get("expense"));
  result.put("transactions",db.queryForList("select id,kind,channel,amount,balance_after,title,created_at from wallet_transaction where user_id=? order by id desc limit 50",uid));return result;
 }
 public static class PinError extends IllegalArgumentException {PinError(String message){super(message);}}
 @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=PinError.class)
 public void authorize(long uid,String pin){
  var account=lock(uid);Timestamp until=(Timestamp)account.get("locked_until"),now=Commerce.now();
  if(until!=null&&until.after(now))throw new PinError("支付密码已锁定，请在 "+until.toLocalDateTime().toLocalTime().withNano(0)+" 后重试");
  if(account.get("pin_hash")==null)throw new PinError("请先设置六位支付密码");
  if(pin==null||!pin.matches("[0-9]{6}")||!encoder.matches(pin,(String)account.get("pin_hash"))){
   int failures=until!=null?1:((Number)account.get("failed_attempts")).intValue()+1;
   db.update("update wallet_account set failed_attempts=?,locked_until=? where user_id=?",failures,failures>=3?Timestamp.valueOf(now.toLocalDateTime().plusMinutes(15)):null,uid);
   throw new PinError(failures>=3?"连续输错三次，钱包支付已锁定15分钟":"支付密码错误，还可尝试 "+(3-failures)+" 次");
  }
  db.update("update wallet_account set failed_attempts=0,locked_until=null where user_id=?",uid);
 }
 @Transactional public void setPin(long uid,String pin,String loginPassword){
  if(pin==null||!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("支付密码须为六位数字");
  var account=lock(uid);
  if(account.get("pin_hash")!=null){
   var hash=db.queryForObject("select password_hash from app_user where id=?",String.class,uid);
   if(hash==null||loginPassword==null||!encoder.matches(loginPassword,hash))throw new IllegalArgumentException("请填写正确的登录密码以重设支付密码");
   var until=(Timestamp)account.get("locked_until");if(until!=null&&until.after(Commerce.now()))throw new IllegalArgumentException("钱包支付锁定期间不能重设密码，请稍后再试");
  }
  db.update("update wallet_account set pin_hash=?,failed_attempts=0,locked_until=null where user_id=?",encoder.encode(pin),uid);
 }
 @Transactional
 public void changePin(long uid,String oldPin,String newPin){
  validatePin(newPin);
  var account=lock(uid);
  var until=(Timestamp)account.get("locked_until");
  if(until!=null&&until.after(Commerce.now()))throw new PinError("支付密码暂时锁定，请稍后重试或找回密码");
  if(oldPin==null||account.get("pin_hash")==null||!encoder.matches(oldPin,(String)account.get("pin_hash")))
   throw new PinError("原支付密码已失效，请重新验证");
  db.update("update wallet_account set pin_hash=?,failed_attempts=0,locked_until=null where user_id=?",encoder.encode(newPin),uid);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=PinError.class)
 public void authorizeRecovery(long uid,String loginPassword){
  db.update("insert into wallet_pin_recovery(user_id,failed_attempts) values(?,0) on duplicate key update user_id=user_id",uid);
  var recovery=db.queryForMap("select * from wallet_pin_recovery where user_id=? for update",uid);
  var until=(Timestamp)recovery.get("locked_until");
  var now=Commerce.now();
  if(until!=null&&until.after(now))throw new PinError("身份验证暂时锁定，请15分钟后重试");
  String hash=db.queryForObject("select password_hash from app_user where id=?",String.class,uid);
  if(hash==null)throw new IllegalArgumentException("该账号请到门店前台核验身份后找回支付密码");
  if(loginPassword==null||loginPassword.length()>72||!encoder.matches(loginPassword,hash)){
   int failures=until!=null?1:((Number)recovery.get("failed_attempts")).intValue()+1;
   db.update("update wallet_pin_recovery set failed_attempts=?,locked_until=? where user_id=?",failures,
    failures>=3?Timestamp.valueOf(now.toLocalDateTime().plusMinutes(15)):null,uid);
   throw new PinError(failures>=3?"身份验证暂时锁定，请15分钟后重试":"登录密码错误，还可尝试 "+(3-failures)+" 次");
  }
  db.update("update wallet_pin_recovery set failed_attempts=0,locked_until=null where user_id=?",uid);
 }
 @Transactional
 public void recoverPin(long uid,String loginPassword,String newPin){
  validatePin(newPin);
  lock(uid);
  String hash=db.queryForObject("select password_hash from app_user where id=?",String.class,uid);
  if(hash==null||loginPassword==null||!encoder.matches(loginPassword,hash))throw new IllegalArgumentException("身份验证已失效，请重新验证登录密码");
  db.update("update wallet_account set pin_hash=?,failed_attempts=0,locked_until=null where user_id=?",encoder.encode(newPin),uid);
 }
 private void validatePin(String pin){
  if(pin==null||!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("支付密码须为六位数字");
 }
 static void key(String key){if(key==null||!key.matches("[A-Za-z0-9-]{8,80}"))throw new IllegalArgumentException("请求编号无效");}
 @Transactional public Map<String,Object> topup(long uid,BigDecimal amount,String channel,String key){
  key(key);if(amount==null||amount.scale()>2||amount.compareTo(new BigDecimal("1"))<0||amount.compareTo(new BigDecimal("5000"))>0)throw new IllegalArgumentException("单次充值支持1至5000元，最多两位小数");
  if(!List.of("WECHAT","ALIPAY").contains(channel))throw new IllegalArgumentException("请选择微信或支付宝");
  var account=lock(uid);String fingerprint="TOPUP:"+channel+":"+amount.stripTrailingZeros().toPlainString();
  var old=existing(uid,key,fingerprint);if(old!=null)return old;
  BigDecimal after=((BigDecimal)account.get("balance")).add(amount);if(after.compareTo(new BigDecimal("99999"))>0)throw new IllegalArgumentException("钱包余额最高99999元");
  record(uid,key,fingerprint,"TOPUP",channel,amount,after,"圣泰钱包充值");return summary(uid);
 }
 Map<String,Object> existing(long uid,String key,String fingerprint){
  var rows=db.queryForList("select fingerprint from wallet_transaction where user_id=? and request_key=?",uid,key);
  if(rows.isEmpty())return null;if(!fingerprint.equals(rows.get(0).get("fingerprint")))throw new IllegalArgumentException("请求编号已被其他交易使用");return summary(uid);
 }
 void record(long uid,String key,String fingerprint,String kind,String channel,BigDecimal amount,BigDecimal after,String title){
  db.update("update wallet_account set balance=? where user_id=?",after,uid);
  db.update("insert into wallet_transaction(user_id,request_key,fingerprint,kind,channel,amount,balance_after,title,created_at) values(?,?,?,?,?,?,?,?,?)",uid,key,fingerprint,kind,channel,amount,after,title,Commerce.now());
 }
}
