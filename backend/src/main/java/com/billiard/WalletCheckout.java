package com.billiard;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class WalletCheckout {
 final Wallet wallet;final Business business;final Commerce commerce;final Ledger ledger;final org.springframework.jdbc.core.JdbcTemplate db;
 WalletCheckout(Wallet wallet,Business business,Commerce commerce,Ledger ledger,org.springframework.jdbc.core.JdbcTemplate db){this.wallet=wallet;this.business=business;this.commerce=commerce;this.ledger=ledger;this.db=db;}
 public record Request(String kind,long tableId,long packageId,long playOrderId,long itemId,Integer hours,String requestKey,String pin){}
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public Map<String,Object> pay(long uid,Request request){
  Wallet.key(request.requestKey());var account=wallet.lock(uid);
  String fingerprint=String.join(":",String.valueOf(request.kind()),String.valueOf(request.tableId()),String.valueOf(request.packageId()),String.valueOf(request.playOrderId()),String.valueOf(request.itemId()),String.valueOf(request.hours()));
  if(wallet.existing(uid,request.requestKey(),fingerprint)!=null){var tx=db.queryForMap("select amount from wallet_transaction where user_id=? and request_key=?",uid,request.requestKey());return Map.of("amount",((BigDecimal)tx.get("amount")).negate(),"wallet",wallet.summary(uid),"replayed",true);}
  Map<String,Object> order;BigDecimal amount;String title;
  switch(request.kind()==null?"":request.kind()){
   case "PLAY":order=business.open(uid,false,request.tableId(),request.packageId(),request.requestKey(),"WALLET");amount=Ledger.decimal(order.get("amount"));title="球桌开台 · "+order.get("package_name");break;
   case "SERVICE":order=commerce.bookPaid(uid,false,request.itemId(),request.playOrderId(),request.requestKey(),request.hours(),"WALLET");amount=Ledger.decimal(order.get("amount"));title="服务预约 · "+order.get("item_name");break;
   case "SETTLE":var bill=ledger.bill(uid,false,request.playOrderId());amount=Ledger.decimal(bill.get("unpaidAmount"));if(amount.signum()<=0)throw new IllegalArgumentException("本局没有待付费用，请刷新账单");order=ledger.settle(uid,false,request.playOrderId(),"WALLET");title="本局服务结算";break;
   default:throw new IllegalArgumentException("支付类型无效");
  }
  BigDecimal balance=(BigDecimal)account.get("balance");if(balance.compareTo(amount)<0)throw new IllegalArgumentException("圣泰钱包余额不足，请先充值");
  wallet.record(uid,request.requestKey(),fingerprint,"PAYMENT","WALLET",amount.negate(),balance.subtract(amount),title);
  return Map.of("amount",amount,"wallet",wallet.summary(uid),"order",order);
 }
}
