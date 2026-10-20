-- Fase 2 (multi-dispositivo): locais e grupos de dispositivos.
--
-- Local: um ponto físico ("Loja Centro"). Cada TV pertence a no máximo um local.
-- Grupo: um conjunto livre de TVs (ex.: "Recepções"); uma TV pode estar em vários grupos.

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_locations_name_lower ON locations (lower(name));

CREATE TABLE device_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_device_groups_name_lower ON device_groups (lower(name));

CREATE TABLE device_group_members (
    group_id BIGINT NOT NULL REFERENCES device_groups(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, device_id)
);
CREATE INDEX idx_device_group_members_device ON device_group_members (device_id);

ALTER TABLE devices ADD COLUMN location_id BIGINT REFERENCES locations(id) ON DELETE SET NULL;
CREATE INDEX idx_devices_location_id ON devices (location_id);

-- Os dispositivos que já existem trazem o local como texto livre: cada nome distinto (sem diferenciar
-- maiúsculas/minúsculas nem espaços nas pontas) vira um local, e a TV passa a apontar para ele.
-- Se o mesmo local foi escrito de formas diferentes ("Loja Centro", "loja centro"), vale a grafia da TV mais antiga.
INSERT INTO locations (name)
SELECT name
FROM (
    SELECT DISTINCT ON (lower(btrim(location))) btrim(location) AS name, lower(btrim(location)) AS sort_key
    FROM devices
    WHERE location IS NOT NULL AND btrim(location) <> ''
    ORDER BY lower(btrim(location)), id
) AS distinct_locations
ORDER BY sort_key;

UPDATE devices d
SET location_id = l.id
FROM locations l
WHERE d.location IS NOT NULL AND btrim(d.location) <> '' AND lower(btrim(d.location)) = lower(l.name);

ALTER TABLE devices DROP COLUMN location;
