package com.musebeauty.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api")
@Validated
class ApiController {
  private final JdbcTemplate db;
  private final SupabaseStorageService storage;
  ApiController(JdbcTemplate db, SupabaseStorageService storage) { this.db=db; this.storage=storage; }

  record NewsletterSignup(@Email @NotBlank @Size(max=254) String email) {}
  record ProductImageUploadRequest(@NotBlank @Pattern(regexp="image/(jpeg|png|webp|avif)") String contentType) {}
  record ProductWrite(@NotBlank @Size(max=100) String brand, @NotBlank @Size(max=180) String name,
    @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=200) String slug, @Size(max=4000) String description, @Size(max=4000) String ingredients,
    @Pattern(regexp="^(https://[^\\s]+|/(?!/)[^\\s]*)?$") @Size(max=2000) String imageUrl, @Size(max=40) String tag, Long categoryId, @NotNull @Min(0) @Max(10000000) Integer priceMinor,
    @NotBlank @Size(max=80) String sku, @Size(max=80) String optionName, @Size(max=120) String optionValue, @NotNull @Min(0) @Max(1000000) Integer stock) {}
  record VariantWrite(@NotBlank @Size(max=80) String sku, @NotBlank @Size(max=80) String optionName,
    @NotBlank @Size(max=120) String optionValue, @NotNull @Min(0) @Max(10000000) Integer priceMinor,
    @NotNull @Min(0) @Max(1000000) Integer stock) {}
  record VariantUpdate(@NotBlank @Size(max=80) String sku, @NotBlank @Size(max=80) String optionName,
    @NotBlank @Size(max=120) String optionValue, @NotNull @Min(0) @Max(10000000) Integer priceMinor) {}
  record ProductUpdate(@NotBlank @Size(max=100) String brand,@NotBlank @Size(max=180) String name,
    @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=200) String slug,
    @Size(max=4000) String description,@Size(max=4000) String ingredients,
    @Pattern(regexp="^(https://[^\\s]+|/(?!/)[^\\s]*)?$") @Size(max=2000) String imageUrl,
    @Size(max=40) String tag,Long categoryId) {}
  record LineWrite(@NotNull Long variantId, @Min(1) @Max(99) int quantity) {}
  record AddressWrite(@NotBlank @Size(max=160) String recipientName, @NotBlank @Size(max=180) String line1, @Size(max=180) String line2,
    @NotBlank @Size(max=100) String city, @Size(max=100) String region, @NotBlank @Size(max=24) String postalCode,
    @NotBlank @Pattern(regexp="[A-Za-z]{2}") String countryCode, boolean isDefault) {}
  record ContentPageWrite(@NotBlank @Pattern(regexp="[^<>]*") @Size(max=160) String title,
    @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=120) String slug,
    @Pattern(regexp="[^<>]*") @Size(max=100) String eyebrow, @NotBlank @Pattern(regexp="[^<>]*") @Size(max=30000) String body, boolean published,
    @Pattern(regexp="[^<>]*") @Size(max=80) String footerGroup, @NotNull @Min(0) @Max(100000) Integer displayOrder) {}
  record ContentPage(long id, String title, String slug, String eyebrow, String body, boolean published,
    String footerGroup, int displayOrder, java.time.Instant createdAt, java.time.Instant updatedAt) {}
  record FooterHelpLink(@NotBlank @Pattern(regexp="[^<>]*") @Size(max=100) String label,
    @NotBlank @Pattern(regexp="^(https://[^\\s<>]+|/(?!/)[^\\s<>]*)$") @Size(max=2048) String url,
    @NotNull @Min(0) @Max(100000) Integer displayOrder) {}
  record StorefrontWrite(@NotBlank @Pattern(regexp="^https://[^\\s<>]+$") @Size(max=2048) String websiteUrl,
    @NotBlank @Email @Size(max=254) String customerEmail, @NotBlank @Pattern(regexp="[^<>]*") @Size(max=2000) String postalAddress,
    @NotNull @Size(max=30) List<@Valid FooterHelpLink> footerHelpLinks) {}
  record Storefront(String websiteUrl, String customerEmail, String postalAddress, List<FooterHelpLink> footerHelpLinks) {}

  @PostMapping("/newsletter") Map<String,String> newsletter(@Valid @RequestBody NewsletterSignup s){db.update("insert into newsletter_subscriptions(email) values(lower(trim(?))) on conflict(email) do update set active=true,subscribed_at=now()",s.email());return Map.of("status","subscribed");}

  @GetMapping("/content/pages") List<ContentPage> publicContentPages(){return db.query("select id,title,slug,eyebrow,body,published,footer_group,display_order,created_at,updated_at from content_pages where published=true order by footer_group nulls last,display_order,id",this::contentPage);}
  @GetMapping("/content/pages/{slug}") ContentPage publicContentPage(@PathVariable @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug){return contentPageBySlug(slug,true);}
  @GetMapping("/admin/content/pages") List<ContentPage> adminContentPages(){return db.query("select id,title,slug,eyebrow,body,published,footer_group,display_order,created_at,updated_at from content_pages order by display_order,id",this::contentPage);}
  @PostMapping("/admin/content/pages") @ResponseStatus(HttpStatus.CREATED) @Transactional ContentPage createContentPage(@Valid @RequestBody ContentPageWrite page){
    Long id=db.queryForObject("insert into content_pages(title,slug,eyebrow,body,published,footer_group,display_order) values(?,?,?,?,?,?,?) returning id",Long.class,page.title().trim(),page.slug(),blankToNull(page.eyebrow()),page.body(),page.published(),blankToNull(page.footerGroup()),page.displayOrder());
    return contentPageById(id);
  }
  @PutMapping("/admin/content/pages/{id}") @Transactional ContentPage updateContentPage(@PathVariable @Positive long id,@Valid @RequestBody ContentPageWrite page){
    if(db.update("update content_pages set title=?,slug=?,eyebrow=?,body=?,published=?,footer_group=?,display_order=?,updated_at=now() where id=?",page.title().trim(),page.slug(),blankToNull(page.eyebrow()),page.body(),page.published(),blankToNull(page.footerGroup()),page.displayOrder(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Content page not found");
    return contentPageById(id);
  }
  @DeleteMapping("/admin/content/pages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteContentPage(@PathVariable @Positive long id){if(db.update("delete from content_pages where id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Content page not found");}
  private ContentPage contentPageById(long id){return db.queryForObject("select id,title,slug,eyebrow,body,published,footer_group,display_order,created_at,updated_at from content_pages where id=?",this::contentPage,id);}
  private ContentPage contentPageBySlug(String slug,boolean publishedOnly){var rows=db.query("select id,title,slug,eyebrow,body,published,footer_group,display_order,created_at,updated_at from content_pages where slug=? and (?=false or published=true)",this::contentPage,slug,publishedOnly);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Content page not found");return rows.get(0);}
  private ContentPage contentPage(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new ContentPage(rs.getLong("id"),rs.getString("title"),rs.getString("slug"),rs.getString("eyebrow"),rs.getString("body"),rs.getBoolean("published"),rs.getString("footer_group"),rs.getInt("display_order"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}
  private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
  @GetMapping("/content/storefront") Storefront publicStorefront(){return storefront();}
  @PutMapping("/admin/content/storefront") @Transactional Storefront updateStorefront(@Valid @RequestBody StorefrontWrite data){
    db.update("update storefront_settings set website_url=?,customer_email=?,postal_address=?,updated_at=now() where id=true",data.websiteUrl().trim(),data.customerEmail().trim().toLowerCase(),data.postalAddress().trim());
    db.update("delete from storefront_help_links");
    for(FooterHelpLink link:data.footerHelpLinks())db.update("insert into storefront_help_links(label,url,display_order) values(?,?,?)",link.label().trim(),link.url().trim(),link.displayOrder());
    return storefront();
  }
  private Storefront storefront(){var settings=db.queryForMap("select website_url,customer_email,postal_address from storefront_settings where id=true");List<FooterHelpLink> links=db.query("select label,url,display_order from storefront_help_links order by display_order,id",(rs,row)->new FooterHelpLink(rs.getString("label"),rs.getString("url"),rs.getInt("display_order")));return new Storefront((String)settings.get("website_url"),(String)settings.get("customer_email"),(String)settings.get("postal_address"),links);}

  @GetMapping("/catalog/categories") List<Map<String,Object>> categories() { return db.queryForList("select id,name,slug from categories where active=true order by name"); }
  @GetMapping("/catalog/products") Map<String,Object> products(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="24") @Min(1) @Max(100) int size,
    @RequestParam(required=false) String q,@RequestParam(required=false) String category,@RequestParam(defaultValue="featured") String sort) {
    if(!List.of("featured","price_asc","price_desc","name_asc").contains(sort)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported sort value");
    String order=switch(sort){case "price_asc"->"v.price_minor asc,p.id";case "price_desc"->"v.price_minor desc,p.id";case "name_asc"->"p.name asc";default->"p.created_at desc,p.id";};
    String where="p.active=true and v.active=true and (cast(? as text) is null or p.name ilike '%'||?||'%' or p.brand ilike '%'||?||'%') and (cast(? as text) is null or c.slug=? or c.name ilike ? )";
    Object[] args={q,q,q,category,category,category==null?null:"%"+category+"%"};
    Long total=db.queryForObject("select count(*) from products p join product_variants v on v.product_id=p.id left join categories c on c.id=p.category_id where "+where,Long.class,args);
    List<Map<String,Object>> items=db.queryForList("select p.id,p.brand,p.name,p.slug,p.description,p.image_url as \"imageUrl\",p.tag,c.name as category,c.slug as \"categorySlug\",v.id as \"variantId\",v.sku,v.option_name as \"optionName\",v.option_value as \"optionValue\",v.price_minor as \"priceMinor\",v.currency,i.quantity as stock from products p join product_variants v on v.product_id=p.id join inventory i on i.variant_id=v.id left join categories c on c.id=p.category_id where "+where+" order by "+order+" limit ? offset ?",append(args,size,(long)page*size));
    return Map.of("items",items,"page",page,"size",size,"total",total,"pages",(total+size-1)/size);
  }
  private Object[] append(Object[] args,Object...tail){Object[] out=java.util.Arrays.copyOf(args,args.length+tail.length);System.arraycopy(tail,0,out,args.length,tail.length);return out;}
  @GetMapping("/catalog/products/{slug}") Map<String,Object> product(@PathVariable String slug) { var rows=db.queryForList("select p.id,p.brand,p.name,p.slug,p.description,p.ingredients,p.image_url as \"imageUrl\",p.tag,c.name as category,v.id as \"variantId\",v.sku,v.option_name as \"optionName\",v.option_value as \"optionValue\",v.price_minor as \"priceMinor\",v.currency,i.quantity as stock from products p join product_variants v on v.product_id=p.id join inventory i on i.variant_id=v.id left join categories c on c.id=p.category_id where p.slug=? and p.active=true and v.active=true",slug); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"); return Map.of("product",rows.get(0),"variants",rows); }

  @GetMapping("/account/me") Map<String,Object> me(@AuthenticationPrincipal AppPrincipal p){return db.queryForMap("select id,auth_user_id as \"authUserId\",email,first_name as \"firstName\",last_name as \"lastName\",role,active,created_at as \"createdAt\" from customers where id=? and auth_user_id=?",p.id(),p.authUserId());}
  @GetMapping("/account/addresses") List<Map<String,Object>> addresses(@AuthenticationPrincipal AppPrincipal p){return db.queryForList("select id,recipient_name as \"recipientName\",line1,line2,city,region,postal_code as \"postalCode\",country_code as \"countryCode\",is_default as \"isDefault\" from addresses where customer_id=? order by is_default desc,id",p.id());}
  @PostMapping("/account/addresses") @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,Object> addAddress(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody AddressWrite a){ if(a.isDefault())db.update("update addresses set is_default=false where customer_id=?",p.id()); Long id=db.queryForObject("insert into addresses(customer_id,recipient_name,line1,line2,city,region,postal_code,country_code,is_default) values(?,?,?,?,?,?,?,?,?) returning id",Long.class,p.id(),a.recipientName(),a.line1(),a.line2(),a.city(),a.region(),a.postalCode(),a.countryCode().toUpperCase(),a.isDefault()); return db.queryForMap("select id,recipient_name as \"recipientName\",line1,line2,city,region,postal_code as \"postalCode\",country_code as \"countryCode\",is_default as \"isDefault\" from addresses where id=? and customer_id=?",id,p.id()); }
  @DeleteMapping("/account/addresses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteAddress(@AuthenticationPrincipal AppPrincipal p,@PathVariable long id){if(db.update("delete from addresses where id=? and customer_id=?",id,p.id())==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Address not found");}

  @GetMapping("/basket") Map<String,Object> basket(@AuthenticationPrincipal AppPrincipal p){return basketData(p.id());}
  private Map<String,Object> basketData(long id){List<Map<String,Object>> items=db.queryForList("select v.id as \"variantId\",p.id as \"productId\",p.name,p.brand,p.image_url as \"imageUrl\",v.sku,v.option_name as \"optionName\",v.option_value as \"optionValue\",v.price_minor as \"priceMinor\",v.currency,bl.quantity,i.quantity as stock from baskets b join basket_lines bl on bl.basket_id=b.id join product_variants v on v.id=bl.variant_id join products p on p.id=v.product_id join inventory i on i.variant_id=v.id where b.customer_id=? and p.active=true and v.active=true order by p.name",id); int total=items.stream().mapToInt(x->((Number)x.get("priceMinor")).intValue()*((Number)x.get("quantity")).intValue()).sum();return Map.of("items",items,"subtotalMinor",total,"currency","GBP");}
  @PutMapping("/basket/lines") Map<String,Object> putLine(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody LineWrite l){var v=db.query("select id from product_variants where id=? and active=true",(rs,n)->rs.getLong(1),l.variantId());if(v.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product variant not found");db.update("insert into basket_lines(basket_id,variant_id,quantity) select id,?,? from baskets where customer_id=? on conflict(basket_id,variant_id) do update set quantity=excluded.quantity",l.variantId(),l.quantity(),p.id());return basketData(p.id());}
  @DeleteMapping("/basket/lines/{variantId}") Map<String,Object> removeLine(@AuthenticationPrincipal AppPrincipal p,@PathVariable long variantId){db.update("delete from basket_lines bl using baskets b where bl.basket_id=b.id and b.customer_id=? and bl.variant_id=?",p.id(),variantId);return basketData(p.id());}

  @PostMapping("/orders") @Transactional @ResponseStatus(HttpStatus.CREATED) Map<String,Object> placeOrder(@AuthenticationPrincipal AppPrincipal p,@RequestParam long addressId){
    var addr=db.query("select recipient_name,line1,line2,city,region,postal_code,country_code from addresses where customer_id=? and id=?",(rs,n)->Map.of("recipientName",rs.getString(1),"line1",rs.getString(2),"line2",rs.getString(3)==null?"":rs.getString(3),"city",rs.getString(4),"region",rs.getString(5)==null?"":rs.getString(5),"postalCode",rs.getString(6),"countryCode",rs.getString(7)),p.id(),addressId); if(addr.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Address not found");
    Long basketId=db.queryForObject("select id from baskets where customer_id=?",Long.class,p.id()); var lines=db.query("select v.id,v.sku,v.option_name,v.option_value,v.price_minor,bl.quantity,p.name,p.brand,i.quantity from basket_lines bl join product_variants v on v.id=bl.variant_id join products p on p.id=v.product_id join inventory i on i.variant_id=v.id where bl.basket_id=? and p.active=true and v.active=true for update of i",(rs,n)->new Object[]{rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getString(7),rs.getString(8),rs.getInt(9)},basketId);
    if(lines.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Basket is empty"); int total=0; for(Object[] l:lines){if((Integer)l[8]<(Integer)l[5])throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock for "+l[6]); total+=(Integer)l[4]*(Integer)l[5];}
    Long oid=db.queryForObject("insert into orders(customer_id,status,subtotal_minor,address_snapshot) values(?,'PLACED',?,?::jsonb) returning id",Long.class,p.id(),total,new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(addr).toString());
    for(Object[] l:lines){db.update("insert into order_lines(order_id,variant_id,product_name,brand,sku,option_name,option_value,unit_price_minor,quantity) values(?,?,?,?,?,?,?,?,?)",oid,l[0],l[6],l[7],l[1],l[2],l[3],l[4],l[5]);db.update("update inventory set quantity=quantity-?,updated_at=now() where variant_id=?",l[5],l[0]);}
    db.update("delete from basket_lines where basket_id=?",basketId);return orderDetails(p.id(),oid);
  }
  private Map<String,Object> orderDetails(long customerId,long orderId){var order=db.queryForList("select id,status,subtotal_minor as \"subtotalMinor\",currency,address_snapshot::text as address,created_at as \"createdAt\" from orders where id=? and customer_id=?",orderId,customerId);if(order.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");try{order.get(0).put("address",new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)order.get(0).get("address")));}catch(Exception e){throw new IllegalStateException("Invalid persisted order address",e);}return Map.of("order",order.get(0),"items",db.queryForList("select product_name as \"productName\",brand,sku,option_name as \"optionName\",option_value as \"optionValue\",unit_price_minor as \"unitPriceMinor\",quantity from order_lines where order_id=?",orderId));}
  @GetMapping("/orders") List<Map<String,Object>> orders(@AuthenticationPrincipal AppPrincipal p){return db.queryForList("select id,status,subtotal_minor as \"subtotalMinor\",currency,created_at as \"createdAt\" from orders where customer_id=? order by created_at desc limit 100",p.id());}
  @GetMapping("/orders/{id}") Map<String,Object> order(@AuthenticationPrincipal AppPrincipal p,@PathVariable long id){return orderDetails(p.id(),id);}

  @GetMapping("/admin/products") Map<String,Object> adminProducts(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="50") @Min(1) @Max(100) int size){List<Map<String,Object>> items=db.queryForList("select p.id,p.brand,p.name,p.slug,p.description,p.ingredients,p.image_url as \"imageUrl\",p.tag,p.category_id as \"categoryId\",p.active,v.id as \"variantId\",v.sku,v.option_name as \"optionName\",v.option_value as \"optionValue\",v.price_minor as \"priceMinor\",i.quantity as stock from products p left join product_variants v on v.product_id=p.id left join inventory i on i.variant_id=v.id order by p.id limit ? offset ?",size,(long)page*size);Long total=db.queryForObject("select count(*) from products",Long.class);return Map.of("items",items,"page",page,"size",size,"total",total);}
  @PostMapping("/admin/storage/product-images/upload-url") Map<String,Object> productImageUploadUrl(@Valid @RequestBody ProductImageUploadRequest request){return storage.createProductImageUpload(request.contentType());}
  @PostMapping("/admin/products") @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,Object> createProduct(@Valid @RequestBody ProductWrite x){Long id=db.queryForObject("insert into products(brand,name,slug,description,ingredients,image_url,tag,category_id) values(?,?,?,?,?,?,?,?) returning id",Long.class,x.brand(),x.name(),x.slug(),x.description(),x.ingredients(),x.imageUrl(),x.tag(),x.categoryId());Long variant=db.queryForObject("insert into product_variants(product_id,sku,option_name,option_value,price_minor) values(?,?,?,?,?) returning id",Long.class,id,x.sku(),x.optionName()==null?"Standard":x.optionName(),x.optionValue()==null?"One size":x.optionValue(),x.priceMinor());db.update("insert into inventory(variant_id,quantity) values(?,?)",variant,x.stock());return Map.of("id",id,"variantId",variant);}
  @PostMapping("/admin/products/{id}/variants") @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,Object> addVariant(@PathVariable long id,@Valid @RequestBody VariantWrite x){if(db.queryForObject("select count(*) from products where id=?",Integer.class,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");Long variant=db.queryForObject("insert into product_variants(product_id,sku,option_name,option_value,price_minor) values(?,?,?,?,?) returning id",Long.class,id,x.sku(),x.optionName(),x.optionValue(),x.priceMinor());db.update("insert into inventory(variant_id,quantity) values(?,?)",variant,x.stock());return Map.of("variantId",variant);}
  @PutMapping("/admin/products/{productId}/variants/{variantId}") Map<String,Object> updateVariant(@PathVariable long productId,@PathVariable long variantId,@Valid @RequestBody VariantUpdate x){if(db.update("update product_variants set sku=?,option_name=?,option_value=?,price_minor=? where id=? and product_id=?",x.sku(),x.optionName(),x.optionValue(),x.priceMinor(),variantId,productId)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product variant not found");return Map.of("variantId",variantId,"sku",x.sku(),"optionName",x.optionName(),"optionValue",x.optionValue(),"priceMinor",x.priceMinor());}
  @PutMapping("/admin/products/{id}") Map<String,Object> updateProduct(@PathVariable long id,@Valid @RequestBody ProductUpdate x){if(db.update("update products set brand=?,name=?,slug=?,description=?,ingredients=?,image_url=?,tag=?,category_id=?,updated_at=now() where id=?",x.brand(),x.name(),x.slug(),x.description(),x.ingredients(),x.imageUrl(),x.tag(),x.categoryId(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");return Map.of("id",id,"updated",true);}
  @PatchMapping("/admin/products/{id}/active") Map<String,Object> activate(@PathVariable long id,@RequestBody Map<String,Boolean> body){Boolean active=body.get("active");if(active==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"active is required");if(db.update("update products set active=?,updated_at=now() where id=?",active,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");return Map.of("id",id,"active",active);}
  @PutMapping("/admin/inventory/{variantId}") Map<String,Object> inventory(@PathVariable long variantId,@RequestBody Map<String,Integer> body){Integer quantity=body.get("quantity");if(quantity==null||quantity<0||quantity>1000000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"quantity must be between 0 and 1000000");if(db.update("update inventory set quantity=?,updated_at=now() where variant_id=?",quantity,variantId)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Variant not found");return Map.of("variantId",variantId,"quantity",quantity);}
  @GetMapping("/admin/orders") List<Map<String,Object>> adminOrders(){return db.queryForList("select o.id,o.customer_id as \"customerId\",c.email,o.status,o.subtotal_minor as \"subtotalMinor\",o.currency,o.created_at as \"createdAt\" from orders o join customers c on c.id=o.customer_id order by o.created_at desc limit 200");}
  @PatchMapping("/admin/orders/{id}/status") @Transactional Map<String,Object> changeOrder(@PathVariable long id,@RequestBody Map<String,String> body){String status=body.get("status");if(status==null||!List.of("CONFIRMED","CANCELLED","FULFILLED").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported order status");if(db.update("update orders set status=? where id=? and status='PLACED'",status,id)==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order not found or no longer awaiting review");if("CANCELLED".equals(status))db.update("update inventory i set quantity=i.quantity+ol.quantity,updated_at=now() from order_lines ol where ol.order_id=? and ol.variant_id=i.variant_id",id);return Map.of("id",id,"status",status);}
}
