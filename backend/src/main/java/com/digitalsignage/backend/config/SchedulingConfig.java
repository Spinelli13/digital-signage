package com.digitalsignage.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas (monitoramento e cobrança); cada uma se desliga sozinha por propriedade (nos testes). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
