package com.deployer.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public AuthController(AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/login")
	public ResponseEntity<Map<String, String>> login(
			@RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		if (request.username() == null || request.password() == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Введите логин и пароль"));
		}
		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.username(), request.password()));
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
			securityContextRepository.saveContext(context, httpRequest, httpResponse);
			return ResponseEntity.ok(Map.of("ok", "true"));
		} catch (AuthenticationException ex) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "Неверный логин или пароль"));
		}
	}

	public record LoginRequest(String username, String password) {
	}
}
