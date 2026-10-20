package com.digitalsignage.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio dos testes: parte do relógio real, mas pode ser "congelado" em qualquer instante. */
public class MutableClock extends Clock {

	private volatile Instant fixed;

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return fixed != null ? fixed : Instant.now();
	}

	public void freeze(String isoInstant) {
		this.fixed = Instant.parse(isoInstant);
	}

	public void reset() {
		this.fixed = null;
	}

}
