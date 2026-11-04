package kz.behaviorguard.backend;

import org.springframework.boot.SpringApplication;

public class TestBehaviorguardBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BehaviorguardBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
