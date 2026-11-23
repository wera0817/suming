package cn.guanxiang.controller;

import cn.guanxiang.dto.AdminRequests;

import cn.guanxiang.dto.AdminRequests.*;
import cn.guanxiang.dto.KnowledgeDoc;
import cn.guanxiang.dto.ProviderSettings;
import cn.guanxiang.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService auth;
    private final ProviderService providers;
    private final KnowledgeService knowledge;
    private final ProviderTestService tests;
    private final RunLogService logs;

    public AdminController(AuthService auth,ProviderService providers,KnowledgeService knowledge,ProviderTestService tests,RunLogService logs) {
        this.auth=auth; this.providers=providers; this.knowledge=knowledge; this.tests=tests; this.logs=logs;
    }
    @PostMapping("/login")
    public Object login(@RequestBody @Valid Login input,HttpServletRequest req,HttpServletResponse res) {
        auth.login(req,res,input.username(),input.password()); return Map.of("ok",true);
    }
    @GetMapping("/session")
    public Object session() { return Map.of("ok",true); }
    @PostMapping("/logout")
    public Object logout(HttpServletRequest req,HttpServletResponse res) {
        auth.logout(req,res); return Map.of("ok",true);
    }
    @GetMapping("/settings")
    public Object settings() { return providers.view(); }
    @PutMapping("/settings")
    public Object settings(@RequestBody @Valid ProviderSettings input) { providers.save(input); return providers.view(); }
    @PostMapping("/test")
    public Object test(@RequestBody @Valid Test input,HttpServletRequest req) {
        auth.limit("test:"+req.getRemoteAddr(),10,600);
        return tests.test(input.kind());
    }
    @GetMapping("/knowledge")
    public Object knowledge() { return knowledge.all(); }
    @PutMapping("/knowledge")
    public Object save(@RequestBody @Valid KnowledgeDoc doc) { knowledge.save(doc); return Map.of("ok",true); }
    @GetMapping("/logs")
    public Object logs() { return logs.list(); }
}
