package com.billiard;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@RestController
@RequestMapping("/api")
public class Api implements ApplicationRunner {
 final Business b;final Map<String,Session> sessions=new ConcurrentHashMap<>();final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 @Value("${app.demo-enabled}") boolean demo;
 @Value("${app.admin-password}") String password;
 @Value("${app.wechat-appid:}") String wechatAppid;
 @Value("${app.wechat-secret:}") String wechatSecret;
 record Session(long id,String role,Instant expiry){}
 final Commerce commerce;
 final ServiceProfiles profiles;
 final Ledger ledger; final Wallet wallet; final WalletCheckout checkout;final CatalogAdmin catalog;final Leaderboard leaderboard;final CatalogRefresh catalogRefresh;
 Api(Business b,Commerce commerce,ServiceProfiles profiles,Ledger ledger,Wallet wallet,WalletCheckout checkout,CatalogAdmin catalog,Leaderboard leaderboard,CatalogRefresh catalogRefresh){this.b=b;this.commerce=commerce;this.profiles=profiles;this.ledger=ledger;this.wallet=wallet;this.checkout=checkout;this.catalog=catalog;this.leaderboard=leaderboard;this.catalogRefresh=catalogRefresh;}
 public void run(ApplicationArguments args){
  if(b.jdbc().queryForObject("select count(*) from app_user where role='ADMIN'",Integer.class)==0)b.jdbc().update("insert into app_user(username,password_hash,role) values(?,?,?)","admin",encoder.encode(password),"ADMIN");
  if(demo&&b.jdbc().queryForObject("select count(*) from app_user where username='demo-customer'",Integer.class)==0)b.jdbc().update("insert into app_user(username,role) values('demo-customer','CUSTOMER')");
  if(b.jdbc().queryForObject("select count(*) from billiard_table",Integer.class)==0)for(int i=1;i<=8;i++)b.jdbc().update("insert into billiard_table(table_no,type,status) values(?,?,?)",String.format("T%02d",i),i<=6?"中式八球":"斯诺克","IDLE");
  if(b.jdbc().queryForObject("select count(*) from play_package",Integer.class)==0){b.jdbc().update("insert into play_package(name,minutes,price,enabled) values('畅玩一小时',60,38,true)");b.jdbc().update("insert into play_package(name,minutes,price,enabled) values('双人两小时',120,68,true)");b.jdbc().update("insert into play_package(name,minutes,price,enabled) values('尽兴三小时',180,98,true)");}
  if(demo){commerce.seed();try{profiles.seed();}catch(Exception e){throw new IllegalStateException("服务目录升级失败",e);}}catalog.assignCodes();try{catalogRefresh.seed();}catch(Exception e){throw new IllegalStateException("服务目录更新失败",e);}
 }
 Session auth(String header,boolean admin){var s=sessions.get(header.replace("Bearer ",""));if(s==null||s.expiry().isBefore(Instant.now()))throw new AuthError("登录已失效",401);if(admin&&!s.role().equals("ADMIN"))throw new AuthError("需要管理员权限",403);return s;}
 Map<String,Object> token(long id,String role){wallet.code(id);String t=UUID.randomUUID().toString();sessions.put(t,new Session(id,role,Instant.now().plusSeconds(28800)));return Map.of("token",t,"role",role);}
 @PostMapping("/auth/admin") Object login(@RequestBody Map<String,String> r){var u=b.row("select * from app_user where username=? and role='ADMIN'",r.getOrDefault("username",""));if(!encoder.matches(r.getOrDefault("password",""),(String)u.get("password_hash")))throw new AuthError("账号或密码错误",401);return token(((Number)u.get("id")).longValue(),"ADMIN");}
 @PostMapping("/auth/demo") Object demo(){if(!demo)throw new AuthError("当前登录入口未开放",403);var u=b.row("select * from app_user where username='demo-customer'");return token(((Number)u.get("id")).longValue(),"CUSTOMER");}
 record MemberCredentials(String username,String password){}
 @PostMapping("/auth/member/register") Object registerMember(@RequestBody MemberCredentials r){
  String name=memberName(r);
  try{b.jdbc().update("insert into app_user(username,password_hash,role) values(?,?,'CUSTOMER')",name,encoder.encode(r.password()));}
  catch(org.springframework.dao.DuplicateKeyException e){throw new AuthError("该账号已注册",409);}
  return loginMember(r);
 }
 @PostMapping("/auth/member/login") Object loginMember(@RequestBody MemberCredentials r){
  String name=memberName(r);
  var users=b.jdbc().queryForList("select id,password_hash from app_user where username=? and role='CUSTOMER' and password_hash is not null",name);
  if(users.isEmpty()||!encoder.matches(r.password(),(String)users.get(0).get("password_hash")))throw new AuthError("账号或密码错误",401);
  return token(((Number)users.get(0).get("id")).longValue(),"CUSTOMER");
 }
 private String memberName(MemberCredentials r){
  String name=r.username()==null?"":r.username().strip().toLowerCase(java.util.Locale.ROOT);
  if(!name.matches("[a-z0-9_]{4,24}")||r.password()==null||r.password().length()<8||r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
   throw new IllegalArgumentException("账号须为4至24位字母、数字或下划线，密码至少8位且不超过72字节");
  return name;
 }
 @GetMapping("/admin/members") Object members(@RequestHeader(value="Authorization",defaultValue="")String h){
  auth(h,true);
  var tiers=memberTiers();
  return b.jdbc().queryForList("""
   select u.id,u.customer_code,u.username,u.role,p.nickname,coalesce(s.visits,0) as visits,
     coalesce(s.table_spend,0) as table_spend,coalesce(s.service_spend,0) as service_spend,
     coalesce(s.total_spend,0) as total_spend,coalesce(s.cash_spend,0) as cash_spend,
     coalesce(s.wallet_spend,0) as wallet_spend,coalesce(s.mock_spend,0) as mock_spend
   from app_user u left join customer_profile p on p.user_id=u.id
   left join (
    select user_id,sum(case when kind='PLAY' then 1 else 0 end) as visits,
     sum(case when kind='PLAY' then amount else 0 end) as table_spend,
     sum(case when kind<>'PLAY' then amount else 0 end) as service_spend,
     sum(amount) as total_spend,
     sum(case when payment_method='CASH' then amount else 0 end) as cash_spend,
     sum(case when payment_method='WALLET' then amount else 0 end) as wallet_spend,sum(case when payment_method='MOCK' then amount else 0 end) as mock_spend
    from (
     select user_id,'PLAY' as kind,amount,payment_method from play_order where payment_method in ('CASH','MOCK','WALLET')
     union all
     select user_id,kind,amount,payment_method from service_order
     where payment_method in ('CASH','MOCK','WALLET') and settled_at is not null
    ) receipts group by user_id
   ) s on s.user_id=u.id
   where u.role in ('CUSTOMER','FRONT_DESK') and u.username<>'demo-customer'
   order by total_spend desc,u.id
   """).stream().map(row->{
    row.put("tier",FrontDeskAccount.ROLE.equals(row.get("role"))?"前台账户":memberTier(((Number)row.get(demo?"total_spend":"cash_spend")).doubleValue(),tiers).name());
    return row;
   }).toList();
 }
 private Map<String,Object> memberAccount(long id){
  var rows=b.jdbc().queryForList("""
   select u.id,u.customer_code,u.username,u.role,p.nickname,p.age,p.gender,
     case when u.password_hash is not null then 1 else 0 end as password_account
   from app_user u left join customer_profile p on p.user_id=u.id
   where u.id=? and u.role in ('CUSTOMER','FRONT_DESK') and u.username<>'demo-customer'
   """,id);
  if(rows.isEmpty())throw new AuthError("会员不存在",404);
  return rows.get(0);
 }
 @GetMapping("/admin/members/{id}") Object memberDetail(@RequestHeader(value="Authorization",defaultValue="")String h,
     @PathVariable long id,@RequestParam(defaultValue="1") int page){
  auth(h,true);
  if(page<1||page>1000000)throw new IllegalArgumentException("页码无效");
  var account=memberAccount(id);
  var receipts=b.jdbc().queryForList("""
   select o.id,'PLAY' as kind,o.order_no,o.package_name as title,t.table_no,o.status,o.minutes,o.started_at as paid_at,
     o.amount as table_amount,o.amount+coalesce(s.service_amount,0) as amount,
     coalesce(s.service_amount,0) as service_amount,coalesce(s.service_count,0) as service_count,
     case when s.methods>1 or s.only_method<>o.payment_method then 'MIXED' else o.payment_method end as payment_method
   from play_order o join billiard_table t on t.id=o.table_id
   left join (select play_order_id,sum(amount) as service_amount,count(*) as service_count,
     count(distinct payment_method) as methods,max(payment_method) as only_method
     from service_order where settled_at is not null group by play_order_id) s on s.play_order_id=o.id
   where o.user_id=? and o.payment_method in ('CASH','MOCK','WALLET')
   order by o.started_at desc,o.id desc limit 26 offset ?
   """,id,(page-1)*25);
  return Map.of("account",account,"receipts",receipts.stream().limit(25).toList(),"hasMore",receipts.size()>25);

 }
 @PutMapping("/admin/members/{id}/profile") void editMember(@RequestHeader(value="Authorization",defaultValue="")String h,
     @PathVariable long id,@RequestBody ProfileUpdate profile){
  var admin=auth(h,true);
  var account=memberAccount(id);
  if(FrontDeskAccount.ROLE.equals(account.get("role")))throw new IllegalArgumentException("前台消费账户资料由系统维护");
  saveProfile(id,profile);
  b.log(admin.id(),"MEMBER_PROFILE_EDIT",id);
 }
 record PasswordReset(String password){}
 @PutMapping("/admin/members/{id}/password") void resetMemberPassword(@RequestHeader(value="Authorization",defaultValue="")String h,
     @PathVariable long id,@RequestBody PasswordReset reset){
  var admin=auth(h,true);
  var member=memberAccount(id);
  if(((Number)member.get("password_account")).intValue()!=1)throw new IllegalArgumentException("该会员使用微信身份登录");
  memberName(new MemberCredentials((String)member.get("username"),reset.password()));
  b.jdbc().update("update app_user set password_hash=? where id=? and role='CUSTOMER'",encoder.encode(reset.password()),id);
  sessions.entrySet().removeIf(entry->entry.getValue().id()==id);
  b.log(admin.id(),"MEMBER_PASSWORD_RESET",id);
 }
 private record Tier(String name,int minimum,String icon) {}
 private static final java.util.List<Tier> TIERS=java.util.List.of(
     new Tier("青铜",0,"bronze"),new Tier("白银",500,"silver"),new Tier("黄金",2000,"gold"),
     new Tier("钻石",5000,"diamond"),new Tier("黑金",10000,"black-gold"));

 private final com.fasterxml.jackson.databind.ObjectMapper tierJson=new com.fasterxml.jackson.databind.ObjectMapper();
 private List<Tier> memberTiers(){
  var rows=b.jdbc().queryForList("select tiers_json from membership_settings where id=1",String.class);
  if(rows.isEmpty())return TIERS;
  try{return List.of(tierJson.readValue(rows.get(0),Tier[].class));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("会员等级配置读取失败",e);}
 }
 @GetMapping("/admin/member-tiers") Object readMemberTiers(@RequestHeader(value="Authorization",defaultValue="") String h){
  auth(h,true);
  return memberTiers();
 }
 @PutMapping("/admin/member-tiers") Object saveMemberTiers(@RequestHeader(value="Authorization",defaultValue="") String h,
     @RequestBody List<Tier> tiers) throws com.fasterxml.jackson.core.JsonProcessingException {
  var admin=auth(h,true);
  if(tiers.size()!=TIERS.size())throw new IllegalArgumentException("请设置全部五个等级");
  int previous=-1;
  for(int index=0;index<tiers.size();index++){
   Tier tier=tiers.get(index);
   if(tier==null||!TIERS.get(index).name().equals(tier.name())||tier.minimum()<=previous||tier.minimum()>10000000||
      (index==0&&tier.minimum()!=0)||TIERS.stream().noneMatch(option->option.icon().equals(tier.icon())))
    throw new IllegalArgumentException("青铜须从 0 元起，消费门槛须逐级递增，并选择有效图标");
   previous=tier.minimum();
  }
  String json=tierJson.writeValueAsString(tiers);
  b.jdbc().update("insert into membership_settings(id,tiers_json) values(1,?) on duplicate key update tiers_json=?",json,json);
  b.log(admin.id(),"MEMBER_TIERS_EDIT",1);
  return tiers;
 }

 private static Tier memberTier(double amount,List<Tier> tiers){
  return tiers.stream().filter(tier->amount>=tier.minimum()).reduce((lower,higher)->higher).orElse(tiers.get(0));
 }

 private Map<String,Object> membership(java.math.BigDecimal amount,boolean demo){
  var tiers=memberTiers();
  Tier tier=memberTier(amount.doubleValue(),tiers);
  int index=tiers.indexOf(tier);
  Tier next=index+1<tiers.size()?tiers.get(index+1):null;
  var step=next==null?java.math.BigDecimal.valueOf(500):java.math.BigDecimal.valueOf(next.minimum()-tier.minimum(),1);
  var within=amount.subtract(java.math.BigDecimal.valueOf(tier.minimum()));
  int levelInTier=within.divideToIntegralValue(step).min(java.math.BigDecimal.valueOf(9)).intValue();
  int level=index*10+levelInTier+1;
  var nextLevelAmount=java.math.BigDecimal.valueOf(tier.minimum()).add(step.multiply(java.math.BigDecimal.valueOf(levelInTier+1)));
  var levelRemaining=level==50?java.math.BigDecimal.ZERO:nextLevelAmount.subtract(amount);
  int levelPercent=level==50?100:100-levelRemaining.multiply(java.math.BigDecimal.valueOf(100))
      .divide(step,0,java.math.RoundingMode.UP).intValue();
  int tierTarget=next==null?tier.minimum()+4500:next.minimum();
  int tierPercent=level==50?100:(int)(within.doubleValue()*100/(tierTarget-tier.minimum()));
  var tierRemaining=level==50?java.math.BigDecimal.ZERO:java.math.BigDecimal.valueOf(tierTarget).subtract(amount);
  return Map.of("tier",tier.name(),"level",level,"levelPercent",levelPercent,"levelRemaining",levelRemaining,
      "tierPercent",tierPercent,"tierRemaining",tierRemaining,"nextTier",next==null?"":next.name(),
      "spend",amount,"includesMock",demo,"tiers",tiers);
 }

 @PostMapping("/auth/wechat") Object wechat(@RequestBody Map<String,String> request) throws Exception {
  if(wechatAppid.isBlank()||wechatSecret.isBlank())throw new IllegalArgumentException("尚未配置小程序AppID和后端AppSecret");
  String code=request.getOrDefault("code","");if(code.isBlank()||code.length()>256)throw new IllegalArgumentException("微信登录凭证无效");
  var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
  String query="appid="+encode(wechatAppid)+"&secret="+encode(wechatSecret)+"&js_code="+encode(code)+"&grant_type=authorization_code";
  var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.weixin.qq.com/sns/jscode2session?"+query)).timeout(java.time.Duration.ofSeconds(10)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
  var data=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
  if(response.statusCode()!=200||!data.hasNonNull("openid"))throw new AuthError("微信登录失败，请重新登录",401);
  String identity=wechatAppid+":"+data.get("openid").asText();String username="wx:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  try{b.jdbc().update("insert into app_user(username,role) values(?,'CUSTOMER')",username);}catch(org.springframework.dao.DuplicateKeyException ignored){}
  var user=b.row("select * from app_user where username=?",username);return token(((Number)user.get("id")).longValue(),"CUSTOMER");
 }
 static String encode(String s){return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}
 @PostMapping("/auth/logout") void logout(@RequestHeader(value="Authorization",defaultValue="")String h){sessions.remove(h.replace("Bearer ",""));}
 @GetMapping("/me") Object me(@RequestHeader(value="Authorization",defaultValue="")String h){
  var session=auth(h,false);
  var profile=b.jdbc().queryForList("select nickname,age,gender from customer_profile where user_id=?",session.id());
  var spending=b.jdbc().queryForMap("""
   select coalesce(sum(case when counted=1 then amount else 0 end),0) as total_spent,
     coalesce(sum(case when payment_method in ('CASH','WALLET') and eligible=1 then amount else 0 end),0) as eligible_spent
   from (
    select amount,payment_method,1 as eligible,1 as counted from play_order where user_id=? and payment_method in ('CASH','MOCK','WALLET')
    union all
    select amount,payment_method,case when status='SETTLED' then 1 else 0 end,
     case when settled_at is not null then 1 else 0 end
    from service_order where user_id=? and payment_method in ('CASH','MOCK','WALLET')
      and (settled_at is not null or (payment_method='CASH' and status='SETTLED'))
   ) paid
   """,session.id(),session.id());
  var playedSeconds=b.jdbc().queryForObject("""
   select coalesce(sum(timestampdiff(SECOND,started_at,ended_at)),0)
   from play_order where user_id=? and status='COMPLETED' and ended_at>=started_at
   """,Long.class,session.id());
  return Map.of("customerCode",wallet.code(session.id()),"id",session.id(),"role",session.role(),"playedMinutes",playedSeconds/60,"profile",profile.isEmpty()?Map.of():profile.get(0),"totalSpent",spending.get("total_spent"),
      "membership",membership((java.math.BigDecimal)spending.get(demo?"total_spent":"eligible_spent"),demo));
 }
 record ProfileUpdate(String nickname,Integer age,String gender){}
 @PutMapping("/me/profile") Object updateProfile(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody ProfileUpdate r){
  var session=auth(h,false);
  if(!"CUSTOMER".equals(session.role()))throw new AuthError("仅顾客账号可以修改球友资料",403);
  saveProfile(session.id(),r);
  return me(h);
 }
 private void saveProfile(long id,ProfileUpdate r){
  String nickname=r.nickname()==null?"":r.nickname().strip();
  if(nickname.isEmpty()||nickname.length()>24||nickname.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("昵称须为1至24个字符");
  if(r.age()!=null&&(r.age()<1||r.age()>120))throw new IllegalArgumentException("年龄须为1至120的整数");
  String gender=r.gender()==null||"未填写".equals(r.gender())?"不显示":r.gender();
  if(!List.of("不显示","男","女").contains(gender))throw new IllegalArgumentException("请选择有效性别");
  b.jdbc().update("insert into customer_profile(user_id,nickname,age,gender) values(?,?,?,?) on duplicate key update nickname=?,age=?,gender=?",id,nickname,r.age(),gender,nickname,r.age(),gender);
 }

 private java.math.BigDecimal receiptsBetween(java.time.LocalDate from,java.time.LocalDate until,String method){
  var start=java.sql.Timestamp.valueOf(from.atStartOfDay());
  var end=java.sql.Timestamp.valueOf(until.atStartOfDay());
  var tables=b.jdbc().queryForObject("select coalesce(sum(amount),0) from play_order where payment_method=? and created_at>=? and created_at<?",java.math.BigDecimal.class,method,start,end);
  var services=b.jdbc().queryForObject("select coalesce(sum(amount),0) from service_order where payment_method=? and settled_at>=? and settled_at<?",java.math.BigDecimal.class,method,start,end);
  return tables.add(services);
 }
 private Map<String,Object> incomeBetween(java.time.LocalDate from,java.time.LocalDate until){
  var cash=receiptsBetween(from,until,"CASH");
  var mock=demo?receiptsBetween(from,until,"MOCK"):java.math.BigDecimal.ZERO;var walletIncome=receiptsBetween(from,until,"WALLET");
  return Map.of("income",cash.add(mock).add(walletIncome),"walletIncome",walletIncome,"cashIncome",cash,"mockIncome",mock,"includesMock",demo);
 }
 private java.time.LocalDate businessToday(){return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));}
 @GetMapping("/workbench-summary") Object workbenchSummary(@RequestHeader(value="Authorization",defaultValue="")String h){
  auth(h,true);var today=businessToday();return incomeBetween(today,today.plusDays(1));
 }
 @GetMapping("/monthly-finance") Object monthlyFinance(@RequestHeader(value="Authorization",defaultValue="")String h){
  return monthFinance(h,java.time.YearMonth.from(businessToday()));
 }
 @GetMapping("/admin/store-bill") Object storeBill(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestParam String month){return monthFinance(h,java.time.YearMonth.parse(month));}
 private Object monthFinance(String h,java.time.YearMonth selected){
  auth(h,true);var month=selected.atDay(1);var next=month.plusMonths(1);
  var result=new java.util.LinkedHashMap<>(incomeBetween(month,next));
  result.put("month",month.toString().substring(0,7));
  result.put("expenses",b.jdbc().queryForList("select * from store_expense where cancelled=false and expense_date>=? and expense_date<? order by expense_date desc,id desc",java.sql.Date.valueOf(month),java.sql.Date.valueOf(next)));
  result.put("cancelledExpenses",b.jdbc().queryForList("select * from store_expense where cancelled=true and expense_date>=? and expense_date<? order by expense_date desc,id desc",java.sql.Date.valueOf(month),java.sql.Date.valueOf(next)));
  var payroll=serviceStatistics(java.sql.Timestamp.valueOf(month.atStartOfDay()),java.sql.Timestamp.valueOf(next.atStartOfDay()),true);
  result.put("payrollTotal",payroll.stream().filter(row->row.containsKey("estimated_salary"))
      .map(row->(java.math.BigDecimal)row.get("estimated_salary")).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add));
  result.put("payrollDate",next.toString());result.put("payroll",payroll.stream().filter(row->"COACH".equals(row.get("kind"))).toList());
  return result;
 }
 @GetMapping("/admin/account") Object adminAccount(@RequestHeader(value="Authorization",defaultValue="") String h){
  var session=auth(h,true);
  var stores=b.jdbc().queryForList("select * from brand_store where id=1");
  var store=stores.isEmpty()?Map.of("name","永州圣泰台球俱乐部","province","湖南省","city","永州市",
      "address","","manager","","phone",""):stores.get(0);
  return Map.of("account",b.row("select id,username,role from app_user where id=?",session.id()),
      "store",store,"finance",monthlyFinance(h));
 }
 record AdminCredentials(String username,String currentPassword,String newPassword){}
 @PutMapping("/admin/account/credentials") void editAdminCredentials(
     @RequestHeader(value="Authorization",defaultValue="") String h,@RequestBody AdminCredentials change){
  var session=auth(h,true);
  var account=b.row("select username,password_hash from app_user where id=?",session.id());
  String oldHash=(String)account.get("password_hash");
  if(change.currentPassword()==null||!encoder.matches(change.currentPassword(),oldHash))
   throw new IllegalArgumentException("当前密码不正确");
  String username=memberName(new MemberCredentials(change.username(),
      change.newPassword()==null||change.newPassword().isEmpty()?change.currentPassword():change.newPassword()));
  String hash=change.newPassword()==null||change.newPassword().isEmpty()?oldHash:encoder.encode(change.newPassword());
  try{
   if(b.jdbc().update("update app_user set username=?,password_hash=? where id=? and password_hash=? and username=?",
       username,hash,session.id(),oldHash,account.get("username"))!=1)
    throw new IllegalArgumentException("账号已更新，请重新登录后再修改");
  }catch(org.springframework.dao.DuplicateKeyException e){throw new IllegalArgumentException("该登录名已被使用");}
  sessions.entrySet().removeIf(entry->entry.getValue().id()==session.id());
  b.log(session.id(),"ADMIN_CREDENTIALS_EDIT",session.id());
 }
 record StoreDetails(String name,String province,String city,String address,String manager,String phone){}
 private String storeField(String value,int limit){
  if(value==null||value.isBlank()||value.strip().length()>limit||value.codePoints().anyMatch(Character::isISOControl))
   throw new IllegalArgumentException("请填写完整的门店资料");
  return value.strip();
 }
 @PutMapping("/admin/account/store") void saveAccountStore(
     @RequestHeader(value="Authorization",defaultValue="") String h,@RequestBody StoreDetails store){
  var admin=auth(h,true);
  var name=storeField(store.name(),80);
  var province=storeField(store.province(),40);
  var city=storeField(store.city(),40);
  var address=storeField(store.address(),180);
  var manager=storeField(store.manager(),40);
  var phone=storeField(store.phone(),32);
  b.jdbc().update("""
   insert into brand_store(id,name,province,city,address,manager,phone) values(1,?,?,?,?,?,?)
   on duplicate key update name=?,province=?,city=?,address=?,manager=?,phone=?
   """,name,province,city,address,manager,phone,name,province,city,address,manager,phone);
  b.log(admin.id(),"BRAND_STORE_SAVE",1);
 }
 @org.springframework.beans.factory.annotation.Autowired ExpenseLedger expenseLedger;
 record ExpenseRequest(String category,String date,java.math.BigDecimal amount,String note){}
 @PostMapping("/expenses") void recordExpense(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody ExpenseRequest r){var admin=auth(h,true);expenseLedger.create(admin.id(),new ExpenseLedger.Entry(r.category(),r.date(),r.amount(),r.note(),null));}
 @PutMapping("/expenses/{id}") void editExpense(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody ExpenseLedger.Entry r){var admin=auth(h,true);expenseLedger.update(admin.id(),id,r);}
 record ExpenseVersion(Integer version){}
 @PostMapping("/expenses/{id}/cancel") void cancelExpense(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody ExpenseVersion r){var admin=auth(h,true);expenseLedger.cancel(admin.id(),id,r.version());}
 @GetMapping("/statistics") Object statistics(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestParam String from,@RequestParam String to){auth(h,true);java.time.LocalDate start,end;try{start=java.time.LocalDate.parse(from);end=java.time.LocalDate.parse(to);}catch(Exception e){throw new IllegalArgumentException("请选择有效日期");}if(end.isBefore(start)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>366)throw new IllegalArgumentException("日期范围须在367天以内");var begin=java.sql.Timestamp.valueOf(start.atStartOfDay());var until=java.sql.Timestamp.valueOf(end.plusDays(1).atStartOfDay());var receipts=b.jdbc().queryForList("select receipt_day,kind,payment_method,sum(amount) as amount,count(*) as orders_count from (select cast(created_at as date) as receipt_day,'PLAY' as kind,payment_method,amount from play_order where created_at>=? and created_at<? union all select cast(settled_at as date) as receipt_day,kind,payment_method,amount from service_order where settled_at>=? and settled_at<?) receipt group by receipt_day,kind,payment_method order by receipt_day",begin,until,begin,until);return Map.of("items",serviceStatistics(begin,until,start.getDayOfMonth()==1&&end.equals(start.withDayOfMonth(start.lengthOfMonth()))),"receipts",receipts,"pending",b.jdbc().queryForList("select kind,count(*) as orders_count,coalesce(sum(amount),0) as amount from service_order where status='COMPLETED' group by kind"),"running",b.jdbc().queryForList("select kind,status,count(*) as orders_count from service_order where status in ('ACTIVE','REQUESTED') group by kind,status"));}
 private List<Map<String,Object>> serviceStatistics(java.sql.Timestamp begin,java.sql.Timestamp until,boolean payrollMonth){
  var rows=b.jdbc().queryForList("""
   select i.id,i.service_code,i.kind,i.name,i.brand,i.stock,i.enabled,i.deleted,
          coalesce(u.uses,0) as uses,coalesce(u.minutes,0) as minutes,
          coalesce(p.wallet,0) as wallet,coalesce(p.cash,0) as cash,coalesce(p.mock,0) as mock
   from service_item i
   left join (select item_id,count(*) as uses,sum(billed_minutes) as minutes
              from service_order where ended_at>=? and ended_at<? and status in ('COMPLETED','SETTLED')
              group by item_id) u on u.item_id=i.id
   left join (select item_id,sum(case when payment_method='CASH' then amount else 0 end) as cash,
                            sum(case when payment_method='MOCK' then amount else 0 end) as mock,
                            sum(case when payment_method='WALLET' then amount else 0 end) as wallet
              from service_order where settled_at>=? and settled_at<?
              group by item_id) p on p.item_id=i.id
   where i.catalog_key is not null or u.item_id is not null or p.item_id is not null
   order by i.kind,i.id
   """,begin,until,begin,until);
  for(var row:rows){
   if(payrollMonth&&"COACH".equals(row.get("kind"))&&Boolean.TRUE.equals(row.get("enabled"))&&!Boolean.TRUE.equals(row.get("deleted"))){
    var sales=new java.math.BigDecimal(row.get("cash").toString()).add(new java.math.BigDecimal(row.get("wallet").toString()));
    if(demo)sales=sales.add(new java.math.BigDecimal(row.get("mock").toString()));
    var commission=sales.multiply(new java.math.BigDecimal("0.40")).setScale(2,java.math.RoundingMode.HALF_UP);
    row.put("base_salary",3000);
    row.put("commission",commission);
    row.put("estimated_salary",commission.add(new java.math.BigDecimal("3000")));
   }
  }
  return rows;
 }

 @GetMapping("/health") Object health(){return Map.of("status","UP","demo",demo,"wechatLoginConfigured",!wechatAppid.isBlank()&&!wechatSecret.isBlank());}
 @GetMapping("/tables") Object tables(){return b.jdbc().queryForList("select t.*,m.brand,m.model_name,m.image_path,m.source_url,o.started_at,o.expected_end_at,o.package_name from billiard_table t left join table_model m on t.model_id=m.id left join play_order o on t.active_order_id=o.id order by t.id");}
 @GetMapping("/packages") Object packages(@RequestParam(required=false) Long tableId){return tableId==null?b.jdbc().queryForList("select * from play_package where enabled=true order by minutes"):b.prices(tableId);}
 record PriceRequest(String scope,long scopeId,long packageId,java.math.BigDecimal price){}
 @GetMapping("/prices") Object prices(@RequestHeader(value="Authorization",defaultValue="")String h){auth(h,true);return b.jdbc().queryForList("select * from package_price");}
 @PutMapping("/prices") void price(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PriceRequest r){var s=auth(h,true);commerce.price(s.id(),r.scope(),r.scopeId(),r.packageId(),r.price());}
 @GetMapping("/services") Object services(){return commerce.items(false);}
 @GetMapping("/coach-grades") Object grades(){return b.jdbc().queryForList("select * from coach_grade order by stars");}
 record GradeRequest(java.math.BigDecimal rate,java.math.BigDecimal hourlyRate){}
 @PutMapping("/coach-grades/{stars}") void grade(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable int stars,@RequestBody GradeRequest r){var s=auth(h,true);if(r.hourlyRate()!=null)profiles.gradeHourly(s.id(),stars,r.hourlyRate());else profiles.grade(s.id(),stars,r.rate());}
 @PutMapping("/services/{id}/profile") void profile(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody ServiceProfiles.Profile r){var s=auth(h,true);profiles.save(s.id(),id,r);}
 @GetMapping("/admin/services") Object adminServices(@RequestHeader(value="Authorization",defaultValue="")String h){auth(h,true);return commerce.items(true);}
 record ServiceEdit(String name,String description,java.math.BigDecimal rate,int stock,boolean enabled){}
 @PutMapping("/services/{id}") void editService(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody ServiceEdit r){var s=auth(h,true);commerce.edit(s.id(),id,r.name(),r.description(),r.rate(),r.stock(),r.enabled());}
 @GetMapping("/service-orders") Object serviceOrders(@RequestHeader(value="Authorization",defaultValue="")String h){var s=auth(h,false);return ledger.services(s.id(),s.role().equals("ADMIN"));}
 record BookingRequest(long itemId,long playOrderId,String requestKey,Integer hours,boolean mockPrepaid,String paymentMethod){}
 @PostMapping("/service-orders") Object book(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody BookingRequest r){var s=auth(h,false);if(r.mockPrepaid()&&!demo)throw new AuthError("此收款方式暂不可用",403);String method=r.paymentMethod()!=null?r.paymentMethod():r.mockPrepaid()?"MOCK":null;if("WALLET".equals(method))throw new IllegalArgumentException("请使用钱包支付入口");if("MOCK".equals(method)&&!demo)throw new IllegalArgumentException("此收款方式暂不可用");return commerce.bookPaid(s.id(),s.role().equals("ADMIN"),r.itemId(),r.playOrderId(),r.requestKey(),r.hours(),method);}
 @PostMapping("/service-orders/{id}/{action}") Object serviceAction(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@PathVariable String action){var s=auth(h,false);return commerce.action(s.id(),s.role().equals("ADMIN"),id,action);}
 @GetMapping("/admin/order-attention") Object orderAttention(@RequestHeader(value="Authorization",defaultValue="")String h){auth(h,true);return b.jdbc().queryForList("select p.id,t.table_no,count(*) as pending_cues from play_order p join billiard_table t on t.id=p.table_id join service_order s on s.play_order_id=p.id where s.kind='CUE' and s.status in ('ACTIVE','REQUESTED') group by p.id,t.table_no order by p.id");}
 @GetMapping("/admin/orders-by-date") Object datedOrders(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestParam String from,@RequestParam String to,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="false") boolean pendingOnly){
  auth(h,true);
  java.time.LocalDate start,end;
  try{start=java.time.LocalDate.parse(from);end=java.time.LocalDate.parse(to);}
  catch(java.time.DateTimeException e){throw new IllegalArgumentException("请选择有效日期");}
  if(start.getYear()<2000||end.getYear()>9998||end.isBefore(start)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>366)
   throw new IllegalArgumentException("请选择正确的起止日期，范围最多367天");
  return ledger.datedOrders(start,end,page,pendingOnly);
 }
 @GetMapping("/orders") Object orders(@RequestHeader(value="Authorization",defaultValue="")String h){var s=auth(h,false);return ledger.orders(s.id(),s.role().equals("ADMIN"));}
 @GetMapping("/orders/{id}/bill") Object bill(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id){var s=auth(h,false);return ledger.bill(s.id(),s.role().equals("ADMIN"),id);}
 record CustomerRequest(long customerId){}
 @PostMapping("/orders/{id}/customer") Object customer(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody CustomerRequest r){var s=auth(h,true);return ledger.assignCustomer(s.id(),true,id,r.customerId());}
 record SettlementRequest(String paymentMethod){}
 @PostMapping("/orders/{id}/settle") Object settle(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody SettlementRequest r){var s=auth(h,false);if("WALLET".equals(r.paymentMethod()))throw new IllegalArgumentException("请使用钱包支付入口");return ledger.settle(s.id(),s.role().equals("ADMIN"),id,r.paymentMethod());}
 record HideRequest(String kind,List<Long> ids,boolean all){}
 @PostMapping("/history/hide") Object hide(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody HideRequest r){var s=auth(h,false);return ledger.hide(s.id(),r.kind(),r.ids(),r.all());}
 @GetMapping("/brands") Object brands(){return b.jdbc().queryForList("select min(id) as id,brand as name from table_model group by brand order by min(id)");}
 @GetMapping("/models") Object models(){return b.jdbc().queryForList("select * from table_model order by id");}
 record TableModelRequest(long modelId){}
 @PostMapping("/tables/{id}/model") void model(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody TableModelRequest r){var u=auth(h,true);var m=b.row("select * from table_model where id=?",r.modelId());var t=b.row("select * from billiard_table where id=?",id);if("IN_USE".equals(t.get("status")))throw new IllegalArgumentException("请先结束使用再修改型号");b.jdbc().update("update billiard_table set model_id=?,type=? where id=?",r.modelId(),m.get("type"),id);b.log(u.id(),"TABLE_MODEL",id);}
 record OpenRequest(long tableId,long packageId,String requestKey,String paymentMethod){}
 @PostMapping("/orders/open") Object open(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody OpenRequest r){var s=auth(h,false);String method=r.paymentMethod()==null?"MOCK":r.paymentMethod();if("WALLET".equals(method))throw new IllegalArgumentException("请使用钱包支付入口");if(!demo&&method.equals("MOCK"))throw new IllegalArgumentException("请到前台确认收款后开台");return b.open(s.id(),s.role().equals("ADMIN"),r.tableId(),r.packageId(),r.requestKey(),method);}
 @PostMapping("/orders/{id}/finish") void finish(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id){var s=auth(h,false);b.finish(s.id(),s.role().equals("ADMIN"),id);}
 @PostMapping("/tables/{id}/status") void status(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody Map<String,String> r){var s=auth(h,true);b.status(s.id(),id,r.get("status"));}
 record PackageRequest(String name,int minutes,java.math.BigDecimal price){}
 @PostMapping("/packages") void addPackage(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PackageRequest r){var s=auth(h,true);if(r.name()==null||r.name().isBlank()||r.name().length()>80||r.minutes()<1||r.minutes()>1440||r.price()==null||r.price().signum()<=0||r.price().compareTo(new java.math.BigDecimal("99999"))>0||r.price().scale()>2)throw new IllegalArgumentException("请填写有效名称、1—1440分钟时长和两位小数以内正数价格");b.jdbc().update("insert into play_package(name,minutes,price,enabled) values(?,?,?,true)",r.name(),r.minutes(),r.price());b.log(s.id(),"PACKAGE_ADD",0);}
 @GetMapping("/admin/packages") Object packageCatalog(@RequestHeader(value="Authorization",defaultValue="")String h){
  auth(h,true);
  return b.jdbc().queryForList("select * from play_package order by enabled desc,minutes,id");
 }
 @PostMapping("/packages/{id}/enable") void enablePackage(
     @RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id){
  var admin=auth(h,true);
  b.row("select id from play_package where id=?",id);
  b.jdbc().update("update play_package set enabled=true where id=?",id);
  b.log(admin.id(),"PACKAGE_ENABLE",id);
 }
 @PostMapping("/packages/{id}/disable") void disable(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id){var s=auth(h,true);b.jdbc().update("update play_package set enabled=false where id=?",id);b.log(s.id(),"PACKAGE_DISABLE",id);}
 private Session customer(String h){var s=auth(h,false);if(!"CUSTOMER".equals(s.role()))throw new AuthError("请使用顾客账号打开圣泰钱包",403);return s;}
 @GetMapping("/wallet") Object wallet(@RequestHeader(value="Authorization",defaultValue="")String h){var result=wallet.summary(customer(h).id());result.put("topupEnabled",demo);return result;}
 record PinRequest(String pin,String loginPassword){}
 @PutMapping("/wallet/pin") Object pin(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PinRequest r){long uid=customer(h).id();if(Boolean.TRUE.equals(wallet.summary(uid).get("pinSet"))){
   wallet.authorizeRecovery(uid,r.loginPassword());wallet.recoverPin(uid,r.loginPassword(),r.pin());
  }else wallet.setPin(uid,r.pin(),r.loginPassword());
  return wallet.summary(uid);}
 record PinChange(String oldPin,String newPin){}
 record PinRecovery(String loginPassword,String newPin){}
 @PostMapping("/wallet/pin/verify") void verifyPin(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PinRequest r){
  wallet.authorize(customer(h).id(),r.pin());
 }
 @PutMapping("/wallet/pin/change") Object changePin(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PinChange r){
  long uid=customer(h).id();
  wallet.authorize(uid,r.oldPin());
  wallet.changePin(uid,r.oldPin(),r.newPin());
  return wallet.summary(uid);
 }
 @PostMapping("/wallet/pin/recovery/verify") void verifyRecovery(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PinRecovery r){
  wallet.authorizeRecovery(customer(h).id(),r.loginPassword());
 }
 @PutMapping("/wallet/pin/recover") Object recoverPin(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody PinRecovery r){
  long uid=customer(h).id();
  wallet.authorizeRecovery(uid,r.loginPassword());
  wallet.recoverPin(uid,r.loginPassword(),r.newPin());
  return wallet.summary(uid);
 }
 record TopupRequest(java.math.BigDecimal amount,String channel,String requestKey){}
 @PostMapping("/wallet/topup") Object topup(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody TopupRequest r){long uid=customer(h).id();if(!demo)throw new IllegalArgumentException("充值通道尚未接入");return wallet.topup(uid,r.amount(),r.channel(),r.requestKey());}
 @PostMapping("/wallet/pay") Object payWallet(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody WalletCheckout.Request r){long uid=customer(h).id();wallet.authorize(uid,r.pin());return checkout.pay(uid,r);}
 @PostMapping("/scan/pay") Object payScan(@RequestHeader(value="Authorization",defaultValue="")String h,
     @RequestBody WalletCheckout.Request request){
  long uid=customer(h).id();
  if(!demo)throw new IllegalArgumentException("请通过微信付款并由前台核对到账");
  wallet.authorize(uid,request.pin());
  return switch(request.kind()==null?"":request.kind()){
   case "PLAY" -> b.open(uid,false,request.tableId(),request.packageId(),request.requestKey(),"MOCK");
   case "SERVICE" -> commerce.bookPaid(uid,false,request.itemId(),request.playOrderId(),request.requestKey(),request.hours(),"MOCK");
   case "SETTLE" -> ledger.settle(uid,false,request.playOrderId(),"MOCK");
   default -> throw new IllegalArgumentException("支付类型无效");
  };
 }
 @GetMapping("/leaderboard") Object leaderboard(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestParam(defaultValue="month")String period){return leaderboard.read(auth(h,false).id(),period);}
 @PostMapping("/admin/services") Object createService(@RequestHeader(value="Authorization",defaultValue="")String h,@RequestBody CatalogAdmin.Create request){return catalog.create(auth(h,true).id(),request);}
 @PutMapping("/admin/services/{id}") void updateService(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody CatalogAdmin.Create request){catalog.update(auth(h,true).id(),id,request);}
 @DeleteMapping("/admin/services/{id}") void deleteService(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id){catalog.remove(auth(h,true).id(),id);}
 @PutMapping("/packages/{id}") void updatePackage(@RequestHeader(value="Authorization",defaultValue="")String h,@PathVariable long id,@RequestBody CatalogAdmin.PackageUpdate request){catalog.updatePackage(auth(h,true).id(),id,request);}
 static class AuthError extends RuntimeException{final int status;AuthError(String m,int s){super(m);status=s;}}
 @ExceptionHandler(AuthError.class) ResponseEntity<?> authError(AuthError e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> error(Exception e){return ResponseEntity.badRequest().body(Map.of("message","操作失败，请刷新后重试；如持续失败请查看服务日志"));}
}

