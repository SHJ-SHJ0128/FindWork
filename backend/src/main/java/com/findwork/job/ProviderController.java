package com.findwork.job;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;

@RestController
@RequestMapping({"/api/providers", "/api"})
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class ProviderController {
    private final JobProviderService service;
    private final GmailLinkedInService gmail;
    private final JobAnalysisService analysis;

    public ProviderController(JobProviderService service, GmailLinkedInService gmail, JobAnalysisService analysis) {
        this.service = service;
        this.gmail = gmail;
        this.analysis = analysis;
    }

    @PostMapping("/greenhouse/import")
    public ProviderImportResult importGreenhouse(@Valid @RequestBody ProviderImportRequest request) {
        return service.importGreenhouse(request.board());
    }

    @GetMapping("/gmail/authorize")
    public ResponseEntity<?> authorizeGmail() {
        if (!gmail.oauthConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_HTML)
                    .body("""
                            <!doctype html><meta charset="utf-8"><title>FindWork Gmail 配置</title>
                            <style>body{font:16px system-ui;max-width:680px;margin:64px auto;padding:0 24px;color:#17202a}code,pre{background:#f2f0eb;padding:3px 6px;border-radius:4px}pre{padding:14px;overflow:auto}a{color:#315b48}</style>
                            <h1>需要先配置 Gmail OAuth</h1>
                            <p>请在 FindWork 根目录的未跟踪 <code>.env</code> 中填写：</p>
                            <pre>GMAIL_CLIENT_ID=你的Client ID
                            GMAIL_CLIENT_SECRET=你的Client Secret</pre>
                            <p>并将回调地址配置为 <code>http://127.0.0.1:8080/api/providers/gmail/callback</code>，然后重启后端。</p>
                            <p><a href="http://127.0.0.1:5173/">返回 FindWork</a></p>
                            """);
        }
        URI location = gmail.beginAuthorization();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/gmail/callback")
    public ResponseEntity<String> gmailCallback(@RequestParam(required = false) String code,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) String error) {
        if (error != null || !gmail.consumeState(state)) {
            return ResponseEntity.badRequest().body("Gmail 授权未完成，请返回 FindWork 重试。 ");
        }
        gmail.exchangeAuthorizationCode(code);
        return ResponseEntity.ok("Gmail 已连接。可以关闭此页面；LinkedIn 岗位提醒会由后台每天自动同步。");
    }

    @PostMapping("/gmail/linkedin/import")
    public GmailImportResult importLinkedInAlerts() {
        return gmail.importLinkedInAlerts();
    }

    @PostMapping("/ai/jobs/{id}/analyze")
    public Object analyzeJob(@PathVariable java.util.UUID id) {
        return analysis.analyze(id);
    }

    @PostMapping("/ai/jobs/analyze-pending")
    public JobAnalysisService.AnalysisRunResult analyzePending(@RequestParam(defaultValue = "3") int limit) {
        return analysis.analyzePending(limit);
    }
}
