package com.deployer.config;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/",
								"/login",
								"/index.html",
								"/assets/**",
								"/api/login",
								"/api/logout"
						).permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, exception) -> {
					if (request.getRequestURI().startsWith("/api/")) {
						response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
						response.setContentType(MediaType.APPLICATION_JSON_VALUE);
						response.setCharacterEncoding("UTF-8");
						response.getWriter().write("{\"error\":\"Требуется вход\"}");
					} else {
						response.sendRedirect("/login");
					}
				}))
				.logout(logout -> logout
						.logoutUrl("/api/logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessHandler((request, response, authentication) ->
								response.setStatus(HttpServletResponse.SC_NO_CONTENT))
				);
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(DeployerProperties properties, PasswordEncoder encoder) {
		DeployerProperties.Auth auth = properties.auth();
		return new InMemoryUserDetailsManager(
				User.withUsername(auth.username())
						.password(encoder.encode(auth.password()))
						.roles("USER")
						.build()
		);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
		config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
