package izanami.example.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

@RestController()
public class LoginController {

    private final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);

    @PostMapping("/api/login")
    public ResponseEntity login(@RequestBody LoginForm loginForm, HttpServletResponse response) {
        LOGGER.info("Logging user {}", loginForm.email);
        response.addCookie(new Cookie("clientId", loginForm.email));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/me")
    public ResponseEntity<LoginForm> me(@CookieValue(value = "clientId", required = false) String clientId) {
        if (clientId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        } else {
            return ResponseEntity.ok(new LoginForm(clientId));
        }
    }

    public static class LoginForm {
        @NotNull
        public String email;


        public LoginForm() {
        }

        public LoginForm(String email) {
            this.email = email;
        }
    }
}
