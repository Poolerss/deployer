package com.deployer.deploy;

import com.deployer.config.DeployerProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/deploy")
public class DeployController {

	private final DeployService deployService;
	private final LogHub logHub;
	private final DeployerProperties properties;

	public DeployController(DeployService deployService, LogHub logHub, DeployerProperties properties) {
		this.deployService = deployService;
		this.logHub = logHub;
		this.properties = properties;
	}

	@GetMapping
	public DeploymentSnapshot current() {
		return deployService.snapshot();
	}

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream() {
		return logHub.subscribe();
	}

	@GetMapping("/settings")
	public PanelSettings settings(HttpServletRequest request) {
		return new PanelSettings(
				properties.appPort(),
				RequestOrigin.host(request, properties.publicHost()),
				RequestOrigin.scheme(request)
		);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DeploymentSnapshot> deploy(
			@RequestParam("jar") MultipartFile jar,
			@RequestParam("url") String url,
			HttpServletRequest request
	) {
		return ResponseEntity.accepted().body(deployService.deploy(
				jar,
				url,
				RequestOrigin.host(request, properties.publicHost()),
				RequestOrigin.scheme(request)
		));
	}

	@DeleteMapping
	public DeploymentSnapshot stop() {
		return deployService.stopAndRemove();
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(DeployBusyException.class)
	ResponseEntity<Map<String, String>> busy(DeployBusyException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<Map<String, String>> failed(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
	}
}
