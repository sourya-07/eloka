package in.eko.eloka;

import org.springframework.boot.SpringApplication;

public class TestElokaServerApplication {

	public static void main(String[] args) {
		SpringApplication.from(ElokaServerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
