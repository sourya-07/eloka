package in.eko.eloka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * ELOKA backend. Spring's default in-memory user is excluded: authentication is bearer tokens
 * only, and the default would print a generated password into the logs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ElokaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElokaServerApplication.class, args);
	}

}
