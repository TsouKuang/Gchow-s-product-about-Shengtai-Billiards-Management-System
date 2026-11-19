package com.billiard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
@Service
public class CatalogRefresh {
 final Business b;final CatalogAdmin catalog;
 CatalogRefresh(Business b,CatalogAdmin catalog){this.b=b;this.catalog=catalog;}
 @Transactional public void seed()throws Exception{
  if(b.jdbc().queryForObject("select count(*) from app_migration where id='services-v10-young'",Integer.class)>0)return;
  var root=new ObjectMapper().readTree(getClass().getResourceAsStream("/service-catalog-v10.json"));
  for(var row:root.get("portraits"))b.jdbc().update("update service_item set image_path=?,age=? where catalog_key=? and kind='COACH'",row.get("image_path").asText(),row.get("age").asInt(),row.get("key").asText());
  long admin=b.jdbc().queryForObject("select min(id) from app_user where role='ADMIN'",Long.class);
  for(var item:root.get("newItems")){
   String key=item.get("key").asText();
   if(b.jdbc().queryForObject("select count(*) from service_item where catalog_key=?",Integer.class,key)>0)continue;
   boolean coach=item.get("kind").asText().equals("COACH");
   var profile=new ServiceProfiles.Profile(item.get("name").asText(),item.get("description").asText(),coach?null:item.get("rate").decimalValue(),item.get("stock").asInt(),item.get("enabled").asBoolean(),item.get("image_path").asText(),coach?item.get("gender").asText():null,coach?item.get("star_level").asInt():null,coach?item.get("age").asInt():null,coach?item.get("height_cm").asInt():null,coach?item.get("weight_kg").asInt():null,coach?item.get("personality").asText():null,coach?item.get("specialties").asText():null);
   var created=catalog.create(admin,new CatalogAdmin.Create(item.get("kind").asText(),item.get("brand").asText(),profile));
   b.jdbc().update("update service_item set catalog_key=?,source_url=? where id=?",key,item.get("source_url").asText(),created.get("id"));
  }
  b.jdbc().update("insert into app_migration(id) values('services-v10-young')");
 }
}
