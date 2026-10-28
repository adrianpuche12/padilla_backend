package com.padilla.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// PRO-060: mails asincronicos. PRO-044: jobs programados (vencimientos, SLA, resumenes).
@EnableAsync
@EnableScheduling
public class PadillaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PadillaBackendApplication.class, args);
	}

}
