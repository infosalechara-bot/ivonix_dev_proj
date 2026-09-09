package com.ivonix.pulse.ontology.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth){this.auth=auth;}
  @PostMapping("/login") public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,HttpServletRequest http){return ResponseEntity.ok(auth.login(request,http));}
  @PostMapping("/refresh") public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request,HttpServletRequest http){return ResponseEntity.ok(auth.refresh(request.refreshToken(),http));}
  @PostMapping("/logout") public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request,HttpServletRequest http){auth.revoke(request.refreshToken(),http);return ResponseEntity.noContent().build();}
}
