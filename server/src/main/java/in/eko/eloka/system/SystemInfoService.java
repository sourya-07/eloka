package in.eko.eloka.system;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
class SystemInfoService {

	private final Environment environment;
	private final ObjectProvider<BuildProperties> buildProperties;
	private final Clock clock;

	SystemInfoService(Environment environment, ObjectProvider<BuildProperties> buildProperties, Clock clock) {
		this.environment = environment;
		this.buildProperties = buildProperties;
		this.clock = clock;
	}

	SystemInfo current() {
		BuildProperties build = buildProperties.getIfAvailable();
		String name = environment.getProperty("spring.application.name", "eloka-server");
		String version = build != null ? build.getVersion() : "dev";
		String[] profiles = environment.getActiveProfiles();
		if (profiles.length == 0) {
			profiles = environment.getDefaultProfiles(); // `local` when nothing is set explicitly
		}
		return new SystemInfo(name, version, List.of(profiles), Instant.now(clock));
	}
}
