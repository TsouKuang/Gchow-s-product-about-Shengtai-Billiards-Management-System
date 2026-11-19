package com.billiard;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdmin {
 final Business business;final ServiceProfiles profiles;
 CatalogAdmin(Business business,ServiceProfiles profiles){this.business=business;this.profiles=profiles;}
 String allocate(String kind){
  var db=business.jdbc();var row=business.row("select next_value from catalog_sequence where kind=? for update",kind);
  long next=((Number)row.get("next_value")).longValue();db.update("update catalog_sequence set next_value=? where kind=?",next+1,kind);
  return (kind.equals("COACH")?"S":"G")+next;
 }
 @Transactional public void assignCodes(){
  for(var item:business.jdbc().queryForList("select id,kind from service_item where service_code is null order by case when catalog_key is null then 1 else 0 end,id"))
   business.jdbc().update("update service_item set service_code=? where id=? and service_code is null",allocate((String)item.get("kind")),item.get("id"));
 }
 public record Create(String kind,String brand,ServiceProfiles.Profile profile){}
 @Transactional public void update(long uid,long id,Create request){
  var item=business.row("select * from service_item where id=? for update",id);
  if(Boolean.TRUE.equals(item.get("deleted"))||!item.get("kind").equals(request.kind())||request.brand()==null||request.brand().isBlank()||request.brand().strip().length()>50||request.profile()==null)throw new IllegalArgumentException("服务类型、品牌或资料无效");
  profiles.save(uid,id,request.profile());business.jdbc().update("update service_item set brand=? where id=?",request.brand().strip(),id);
 }
 @Transactional public Map<String,Object> create(long uid,Create request){
  String kind=request.kind(),brand=request.brand();
  if(!List.of("CUE","COACH").contains(kind)||brand==null||brand.isBlank()||brand.strip().length()>50||request.profile()==null)throw new IllegalArgumentException("请填写有效的类型、品牌与资料");
  var p=request.profile();String key="custom-"+UUID.randomUUID();String code=allocate(kind);
  business.jdbc().update("insert into service_item(kind,brand,name,description,rate,stock,enabled,source_url,catalog_key,service_code,deleted) values(?,?,?,?,?,0,false,'',?,?,false)",kind,brand.strip(),p.name()==null?"新增服务":p.name(),"",BigDecimal.ONE,key,code);
  long id=((Number)business.row("select id from service_item where catalog_key=?",key).get("id")).longValue();
  profiles.save(uid,id,p);business.log(uid,"SERVICE_CREATE",id);return business.row("select * from service_item where id=?",id);
 }
 @Transactional public void remove(long uid,long id){
  var item=business.row("select * from service_item where id=? for update",id);
  if(Boolean.TRUE.equals(item.get("deleted")))return;
  int active=business.jdbc().queryForObject("select count(*) from service_order where item_id=? and status in ('REQUESTED','ACTIVE')",Integer.class,id);
  if(active>0)throw new IllegalArgumentException("该服务仍有预约或正在使用的订单，请处理完成后再删除");
  business.jdbc().update("update service_item set enabled=false,deleted=true where id=?",id);business.log(uid,"SERVICE_DELETE",id);
 }
 public record PackageUpdate(String name,int minutes,BigDecimal price){}
 @Transactional public void updatePackage(long uid,long id,PackageUpdate p){
  if(p.name()==null||p.name().isBlank()||p.name().strip().length()>80||p.minutes()<1||p.minutes()>1440)throw new IllegalArgumentException("请填写有效套餐名称与1至1440分钟时长");
  Commerce.money(p.price());business.row("select id from play_package where id=? for update",id);
  business.jdbc().update("update play_package set name=?,minutes=?,price=? where id=?",p.name().strip(),p.minutes(),p.price(),id);business.log(uid,"PACKAGE_EDIT",id);
 }
}
