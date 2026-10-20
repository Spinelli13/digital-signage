package com.digitalsignage.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// A autenticação é toda própria (JWT do painel + token do aparelho); sem isto o Spring cria um usuário
// padrão e imprime uma senha aleatória no log a cada inicialização.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DigitalSignageApplication {

	public static void main(String[] args) {
		SpringApplication.run(DigitalSignageApplication.class, args);
	}

}
