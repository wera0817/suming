package cn.guanxiang.controller;

import cn.guanxiang.dto.ConsultationRequests;

import cn.guanxiang.domain.Meihua;
import cn.guanxiang.dto.ConsultationRequests.*;
import cn.guanxiang.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConsultationController {
    private final AuthService auth;
    private final ProviderService providers;
    private final ConsultationService consultations;
    private final ReportService reports;

    public ConsultationController(AuthService auth,ProviderService providers,ConsultationService consultations,ReportService reports) {
        this.auth=auth; this.providers=providers; this.consultations=consultations; this.reports=reports;
    }
    @GetMapping("/session")
    public Map<String,Object> session(HttpServletRequest req,HttpServletResponse res) {
        auth.visitor(req,res);
        return Map.of("configured",providers.ready("text"),"visionConfigured",providers.ready("vision"),"algorithm",Meihua.VERSION);
    }
    @PostMapping("/chart")
    public Meihua.Chart chart(@RequestBody @Valid Numbers input) { return consultations.chart(input.numbers()); }
    @GetMapping("/reports")
    public Object reports(HttpServletRequest req) { return reports.list(auth.owner(req)); }
    @GetMapping("/reports/{id}")
    public Object report(@PathVariable String id,HttpServletRequest req) { return reports.get(id,auth.owner(req)); }
    @DeleteMapping("/reports/{id}")
    public Object delete(@PathVariable String id,HttpServletRequest req) {
        reports.delete(id,auth.owner(req));
        return Map.of("ok",true);
    }
    @PostMapping("/reports")
    public Object create(@RequestBody @Valid Create input,HttpServletRequest req) {
        String owner=auth.owner(req); auth.limit("create:"+owner,12,600);
        return consultations.createMeihua(owner,input.question(),input.background(),input.chart().numbers());
    }
    @PostMapping(value="/reports/photo",consumes="multipart/form-data")
    public Object photo(@RequestParam String kind,@RequestParam String question,
        @RequestParam(defaultValue="") String background,@RequestParam boolean consent,@RequestPart MultipartFile image,HttpServletRequest req) {
        String owner=auth.owner(req); auth.limit("create:"+owner,12,600);
        return consultations.createPhoto(owner,kind,question,background,consent,image);
    }
    @PostMapping(value="/reports/{id}/generate",produces="text/event-stream")
    public SseEmitter generate(@PathVariable String id,@RequestBody @Valid Follow input,HttpServletRequest req) {
        String owner=auth.owner(req); auth.limit("generate:"+owner,20,600);
        return consultations.generate(id,owner,input.message());
    }
}
