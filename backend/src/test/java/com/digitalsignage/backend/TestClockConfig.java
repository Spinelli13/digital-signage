package com.digitalsignage.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Troca o relógio do servidor por um que os testes controlam (regras de agenda das campanhas). */
@TestConfiguration
public class TestClockConfig {

	@Bean
	@Primary
	public MutableClock testClock() {
		return new MutableClock();
	}

}
