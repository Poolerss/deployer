package com.deployer.deploy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LogHub {

	private static final int HISTORY_LIMIT = 2000;
	private final List<LogEvent> history = new ArrayList<>();
	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(0L);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(error -> emitters.remove(emitter));
		emitters.add(emitter);

		List<LogEvent> replay;
		synchronized (history) {
			replay = List.copyOf(history);
		}
		try {
			for (LogEvent event : replay) {
				emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
			}
		} catch (IOException ex) {
			emitters.remove(emitter);
			emitter.complete();
		}
		return emitter;
	}

	public void publish(LogEvent event) {
		synchronized (history) {
			history.add(event);
			if (history.size() > HISTORY_LIMIT) {
				history.subList(0, history.size() - HISTORY_LIMIT).clear();
			}
		}
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
			} catch (Exception ex) {
				emitters.remove(emitter);
				emitter.complete();
			}
		}
	}

	public void log(String stream, String text) {
		publish(LogEvent.log(stream, text));
	}

	public void status(DeploymentSnapshot snapshot) {
		publish(LogEvent.status(snapshot));
	}

	public void clear() {
		synchronized (history) {
			history.clear();
		}
	}
}
