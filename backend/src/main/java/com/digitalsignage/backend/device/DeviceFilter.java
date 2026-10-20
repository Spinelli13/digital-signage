package com.digitalsignage.backend.device;

/** Filtros opcionais da lista de TVs. Todos juntos formam um "E". */
public record DeviceFilter(
		DeviceConnection connection,
		Long locationId,
		boolean noLocation,
		Long groupId,
		String query) {

	public static DeviceFilter none() {
		return new DeviceFilter(null, null, false, null, null);
	}

}
