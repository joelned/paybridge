package com.paybridge.Controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class TestController {
    @GetMapping("/public")
    public String publicEndpoint() {
        return "✅ This endpoint is public";
    }

    @GetMapping("/protected")
    public String protectedEndpoint(Principal principal) {
        return "🔒 Protected! Hello, " + principal.getName();
    }

    @GetMapping("/admin")
    public String adminEndpoint(Principal principal) {
        return "👑 Admin area for: " + principal.getName();
    }
}
