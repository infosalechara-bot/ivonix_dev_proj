package com.ivonix.pulse.ontology.appforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class AppForgeService {
    private static final Pattern IDENT=Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final int DEFAULT_MAX_APPS=25;
    private static final int DEFAULT_MAX_TABLES=250;
    private static final int DEFAULT_MAX_CONCURRENT=2;
    private final JdbcTemplate db; private final ObjectMapper json;
    public AppForgeService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}

    /**
     * App metadata, generated DDL, RLS, indexes, and registry rows form one
     * database transaction. Admission is serialized per tenant so concurrent
     * builders cannot race past the tenant quotas.
     */
    @Transactional
    public UUID createApp(UUID org,UUID user,String name,String description,List<ModelDef> models){
        member(org,user);validateName(name);if(models==null||models.isEmpty()||models.size()>30)throw new IllegalArgumentException("1-30 models required");
        int fieldCount=0;for(ModelDef m:models){validateModel(m);fieldCount+=m.fields().size();}
        if(fieldCount>512)throw new IllegalArgumentException("Maximum 512 fields per app");
        db.execute("set local statement_timeout='5000ms'");
        db.execute("set local lock_timeout='2000ms'");
        db.queryForObject("select pg_advisory_xact_lock(hashtextextended(?::text,0))",Object.class,org.toString());
        Limits limits=limits(org);
        int apps=db.queryForObject("select count(*) from public.app_definitions where organization_id=? and provisioning_status in ('PROVISIONING','READY')",Integer.class,org);
        int tables=db.queryForObject("select count(*) from public.app_generated_tables t join public.app_definitions a on a.id=t.app_id where a.organization_id=? and a.provisioning_status in ('PROVISIONING','READY')",Integer.class,org);
        int provisioning=db.queryForObject("select count(*) from public.app_definitions where organization_id=? and provisioning_status='PROVISIONING'",Integer.class,org);
        if(apps>=limits.maxApps)throw new IllegalStateException("AppForge tenant app quota exceeded");
        if(tables+models.size()>limits.maxTables)throw new IllegalStateException("AppForge tenant table quota exceeded");
        if(provisioning>=limits.maxConcurrent)throw new IllegalStateException("AppForge tenant provisioning capacity exhausted");
        UUID app=UUID.randomUUID();
        try{db.update("insert into public.app_definitions(id,organization_id,name,description,data_models,created_by,provisioning_status) values(?,?,?,?,?::jsonb,?,?)",app,org,name.trim(),description,json.writeValueAsString(models),user,"PROVISIONING");}catch(Exception e){throw new IllegalArgumentException("Invalid app definition",e);}
        for(ModelDef m:models){
            String table="app_"+app.toString().replace("-","").substring(0,12)+"_"+m.name().toLowerCase(Locale.ROOT);
            StringBuilder sql=new StringBuilder("create table if not exists ").append(q(table)).append(" (id uuid primary key default gen_random_uuid(), organization_id uuid not null references public.organizations(id) on delete cascade");
            for(FieldDef f:m.fields())sql.append(", ").append(q(f.name())).append(' ').append(sqlType(f.type()));
            sql.append(", created_at timestamptz not null default now())");
            db.execute(sql.toString());
            db.execute("alter table "+q(table)+" enable row level security");
            db.execute("create policy "+q("appforge_"+table+"_tenant")+" on "+q(table)+" for all to authenticated using (public.is_org_member(organization_id)) with check (public.is_org_member(organization_id))");
            db.execute("create index if not exists "+q(table+"_org_created_idx")+" on "+q(table)+" (organization_id,created_at desc)");
            db.update("insert into public.app_generated_tables(app_id,table_name,schema) values(?,?,?::jsonb)",app,table,json.writeValueAsString(m));
        }
        db.update("update public.app_definitions set provisioning_status='READY' where id=? and organization_id=? and provisioning_status='PROVISIONING'",app,org);
        return app;
    }
    public List<Map<String,Object>> list(UUID app,UUID org,UUID user,String table){member(org,user);authorizedTable(app,org,table);return db.queryForList("select * from "+q(table)+" where organization_id=? order by created_at desc limit 500",org);}
    public void insert(UUID app,UUID org,UUID user,String table,Map<String,Object> data){member(org,user);authorizedTable(app,org,table);if(data==null||data.isEmpty())throw new IllegalArgumentException("Data required");List<String>cols=new ArrayList<>();List<Object>vals=new ArrayList<>();if(data.size()>64)throw new IllegalArgumentException("Maximum 64 fields per insert");for(var e:data.entrySet()){validateName(e.getKey());if(e.getKey().equals("id")||e.getKey().equals("organization_id")||e.getKey().equals("created_at"))continue;cols.add(q(e.getKey()));vals.add(e.getValue());}if(cols.isEmpty())throw new IllegalArgumentException("No writable fields");cols.add("organization_id");vals.add(org);String ph=String.join(",",Collections.nCopies(cols.size(),"?"));db.update("insert into "+q(table)+" ("+String.join(",",cols)+") values("+ph+")",vals.toArray());}
    private void authorizedTable(UUID app,UUID org,String table){Boolean ok=db.queryForObject("select exists(select 1 from public.app_generated_tables t join public.app_definitions a on a.id=t.app_id where t.app_id=? and t.table_name=? and a.organization_id=? and a.provisioning_status='READY')",Boolean.class,app,table,org);if(!Boolean.TRUE.equals(ok))throw new SecurityException("Generated table access denied");}
    private void member(UUID o,UUID u){Boolean ok=db.queryForObject("select public.is_org_member(?)",Boolean.class,o);if(!Boolean.TRUE.equals(ok)){Integer n=db.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,o,u);if(n==null||n<1)throw new SecurityException("Organization access denied");}}
    private Limits limits(UUID org){return db.query("select max_apps,max_generated_tables,max_concurrent_provisioning from public.appforge_org_limits where organization_id=?",rs->{if(!rs.next())return new Limits(DEFAULT_MAX_APPS,DEFAULT_MAX_TABLES,DEFAULT_MAX_CONCURRENT);return new Limits(rs.getInt(1),rs.getInt(2),rs.getInt(3));},org);}
    private void validateModel(ModelDef m){if(m==null||m.name()==null)throw new IllegalArgumentException("Model name required");validateName(m.name());if(m.fields()==null||m.fields().size()>64)throw new IllegalArgumentException("Invalid fields");Set<String>s=new HashSet<>();for(FieldDef f:m.fields()){if(f==null)throw new IllegalArgumentException("Invalid field");validateName(f.name());if(!s.add(f.name()))throw new IllegalArgumentException("Duplicate field");}}
    private void validateName(String n){if(n==null||!IDENT.matcher(n).matches())throw new IllegalArgumentException("Invalid identifier");}
    private String sqlType(String t){return switch(t){case "text"->"text";case "number"->"numeric";case "date"->"date";case "boolean"->"boolean";case "timestamp"->"timestamptz";case "json"->"jsonb";default->throw new IllegalArgumentException("Unsupported field type");};}
    private String q(String s){validateName(s);return '"'+s+'"';}
    private record Limits(int maxApps,int maxTables,int maxConcurrent){}
    public record ModelDef(String name,List<FieldDef> fields){}public record FieldDef(String name,String type){}
}
