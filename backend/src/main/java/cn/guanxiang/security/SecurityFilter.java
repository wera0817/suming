package cn.guanxiang.security;

import cn.guanxiang.common.JsonCodec;
import cn.guanxiang.common.Problem;
import cn.guanxiang.service.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final JsonCodec json;
    private final Set<String> origins;

    public SecurityFilter(AuthService auth,JsonCodec json,@Value("${app.allowed-origins}") String origins) {
        this.auth=auth; this.json=json; this.origins=new HashSet<>(Arrays.asList(origins.split(",")));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Referrer-Policy","same-origin");
        if(!request.getRequestURI().startsWith("/api/")) { chain.doFilter(request,response); return; }
        response.setHeader("Cache-Control","no-store");
        try {
            if(!Set.of("GET","HEAD").contains(request.getMethod())) {
                if(!origins.contains(request.getHeader("Origin"))) throw new Problem(403,"ORIGIN_REJECTED","请求来源未获授权");
                if(!Optional.ofNullable(request.getContentType()).orElse("").startsWith("multipart/") && request.getContentLengthLong()>65536)
                    throw new Problem(413,"BODY_TOO_LARGE","请求内容过长");
            }
            String path=request.getRequestURI();
            if(path.startsWith("/api/admin/")&&!path.equals("/api/admin/login")) auth.requireAdmin(request);
            chain.doFilter(request,response);
        } catch(Problem e) {
            response.setStatus(e.status); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(json.encode(Map.of("code",e.code,"message",e.getMessage())));
        }
    }
}
