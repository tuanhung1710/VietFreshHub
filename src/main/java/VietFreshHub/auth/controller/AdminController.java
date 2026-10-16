package VietFreshHub.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    // Cả /admin và /admin/admin (getHomeUrl của AuthController dùng /admin/admin).
    @GetMapping({"/admin", "/admin/admin"})
    public String showAdminPage() {
        return "admin/admin";
    }
}
