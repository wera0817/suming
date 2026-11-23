package cn.guanxiang.service;

import cn.guanxiang.mapper.AppStateMapper;
import cn.guanxiang.mapper.AdminSessionMapper;
import cn.guanxiang.security.Crypto;
import cn.guanxiang.common.Problem;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final AppStateMapper state;
    private final AdminSessionMapper sessions;
    private final boolean secure;
    private final String username;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    private final String passwordHash;
    private final Map<String,Window> limits=new ConcurrentHashMap<>();
    private record Window(long end,int count) {}
    public AuthService(AppStateMapper state,AdminSessionMapper sessions, @Value("${app.secure-cookies}") boolean secure,
        @Value("${app.admin-user}") String username,
        @Value("${app.admin-password}") String password) {
        this.state=state; this.sessions=sessions; this.secure=secure; this.username=username;
        passwordHash=state.find("admin-password").orElseGet(()->{
            if(password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
                throw new IllegalStateException("ADMIN_PASSWORD needs at least 12 characters and at most 72 UTF-8 bytes");
            String hash=encoder.encode(password); state.save("admin-password",hash); return hash;
        });
        sessions.deleteExpired();
    }
    public void limit(String key,int max,int seconds) {
        long now=System.currentTimeMillis();
        if(limits.size()>2000) limits.entrySet().removeIf(e->e.getValue().end<now);
        Window value=limits.compute(key,(k,old)-> old==null||old.end<now?new Window(now+seconds*1000L,1):new Window(old.end,old.count+1));
        if(value.count>max) throw new Problem(429,"RATE_LIMIT","请求较频繁，请稍后再试");
    }
    public String cookie(HttpServletRequest req,String name) {
        if(req.getCookies()!=null) for(Cookie cookie:req.getCookies()) if(cookie.getName().equals(name)&&cookie.getValue().matches("[a-f0-9]{64}")) return cookie.getValue();
        return null;
    }
    public void setCookie(HttpServletResponse response,String name,String value,long seconds) {
        response.addHeader("Set-Cookie",ResponseCookie.from(name,value).httpOnly(true).secure(secure).sameSite("Strict").path("/").maxAge(seconds).build().toString());
    }
    public String owner(HttpServletRequest request) {
        String token=cookie(request,"gx_visitor");
        if(token==null) throw new Problem(401,"VISITOR_REQUIRED","请刷新页面后重试");
        return Crypto.hash(token);
    }
    public void visitor(HttpServletRequest request,HttpServletResponse response) {
        if(cookie(request,"gx_visitor")==null) setCookie(response,"gx_visitor",Crypto.token(),365*86400L);
    }
    public void requireAdmin(HttpServletRequest request) {
        String token=cookie(request,"gx_admin");
        if(token==null || !sessions.existsActive(Crypto.hash(token)))
            throw new Problem(401,"ADMIN_REQUIRED","请先登录管理后台");
    }
    public void login(HttpServletRequest request,HttpServletResponse response,String user,String password) {
        limit("login:"+request.getRemoteAddr(),5,900);
        boolean matches=encoder.matches(password,passwordHash);
        if(!username.equals(user)||!matches) throw new Problem(401,"LOGIN_FAILED","账号或密码不正确");
        String token=Crypto.token();
        sessions.deleteExpired();
        sessions.insert(Crypto.hash(token),Timestamp.from(Instant.now().plusSeconds(28800)));
        setCookie(response,"gx_admin",token,28800);
    }
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        String token=cookie(request,"gx_admin"); if(token!=null) sessions.delete(Crypto.hash(token));
        setCookie(response,"gx_admin","",0);
    }
}
