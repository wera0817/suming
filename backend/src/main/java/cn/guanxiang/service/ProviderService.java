package cn.guanxiang.service;

import cn.guanxiang.common.JsonCodec;
import cn.guanxiang.mapper.AppStateMapper;
import cn.guanxiang.dto.ProviderSettings;
import cn.guanxiang.dto.ProviderConnection;
import cn.guanxiang.security.Crypto;
import cn.guanxiang.common.Problem;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;

@Service
public class ProviderService {
    private final AppStateMapper state;
    private final JsonCodec json;
    private final Crypto crypto;
    public ProviderService(AppStateMapper state,JsonCodec json,Crypto crypto) { this.state=state; this.json=json; this.crypto=crypto; }
    public synchronized ProviderSettings saved() { return state.find("providers").map(v->json.decode(v,ProviderSettings.class)).orElse(null); }
    public boolean ready(String kind) { ProviderSettings s=saved(); return s!=null && (!kind.equals("text") ? !s.vision().model().isBlank() : !s.text().model().isBlank()); }
    public synchronized Map<String,Object> view() {
        ProviderSettings s=saved(); if(s==null) return Map.of("configured",false);
        return Map.of("configured",true,"text",publicConnection(s.text()),"vision",publicConnection(s.vision()),"separateVision",s.separateVision(),"maxTokens",s.maxTokens(),"timeoutSeconds",s.timeoutSeconds(),"tokenParameter",s.tokenParameter());
    }
    private Map<String,Object> publicConnection(ProviderConnection c) { return Map.of("name",c.name(),"baseUrl",c.baseUrl(),"apiKey","","model",c.model(),"hasKey",!c.apiKey().isBlank()); }
    public synchronized void save(ProviderSettings input) {
        ProviderSettings old=saved();
        ProviderConnection text=encrypted(input.text(),old==null?null:old.text());
        ProviderConnection vision;
        if(input.separateVision()) vision=encrypted(input.vision(),old==null?null:old.vision());
        else vision=new ProviderConnection(text.name(),text.baseUrl(),text.apiKey(),input.vision().model());
        state.save("providers",json.encode(new ProviderSettings(text,vision,input.separateVision(),input.maxTokens(),input.timeoutSeconds(),input.tokenParameter())));
    }
    private ProviderConnection encrypted(ProviderConnection c,ProviderConnection old) {
        URI uri;
        try { uri=URI.create(c.baseUrl()); } catch(Exception e) { throw Problem.bad("API 地址无效"); }
        if(!Set.of("http","https").contains(Optional.ofNullable(uri.getScheme()).orElse(""))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
            throw Problem.bad("API 地址应为 http/https Base URL，不含密钥、查询参数或片段");
        if(!uri.getScheme().equals("https")&&!Set.of("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost()))
            throw Problem.bad("非本机 API 地址必须使用 HTTPS");
        String key=c.apiKey().isBlank()?(old==null?"":old.apiKey()):crypto.encrypt(c.apiKey().trim());
        if(key.isBlank()) throw Problem.bad("首次配置需要填写 API Key");
        return new ProviderConnection(c.name().trim(),c.baseUrl().replaceAll("/+$",""),key,c.model().trim());
    }
    public ProviderConnection connection(ProviderSettings s,String kind) {
        ProviderConnection c=kind.equals("vision")?s.vision():s.text();
        return new ProviderConnection(c.name(),c.baseUrl(),crypto.decrypt(c.apiKey()),c.model());
    }
    public ProviderSettings required() {
        ProviderSettings s=saved(); if(s==null) throw new Problem(503,"API_NOT_CONFIGURED","尚未配置 AI 服务，请在管理后台设置 API"); return s;
    }
}
