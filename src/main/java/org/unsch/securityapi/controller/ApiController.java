package org.unsch.securityapi.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    @GetMapping("/public/ping")
    public Map<String, Object> publicPing() {
        return Map.of("status", "ok", "public", true);
    }

    @GetMapping("/private/ping")
    public Map<String, Object> privatePing(Authentication authentication) {
        return Map.of("status", "ok", "user", authentication.getName());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/ping")
    public Map<String, Object> adminPing() {
        return Map.of("status", "ok", "role", "ADMIN");
    }
}
