CREATE TABLE safetymaps.rq (
	id serial4 NOT NULL,
	queuenname varchar NOT NULL,
	messagebus varchar NOT NULL,
	CONSTRAINT rq_pkey PRIMARY KEY (id)
);

CREATE TABLE safetymaps.incidents (
	id serial4 NOT NULL,
	"source" varchar NOT NULL,
	sourceenv varchar NOT NULL,
	sourceid varchar NOT NULL,
	sourceenvid varchar NULL,
	notes varchar NULL,
	units varchar NULL,
	"location" varchar NULL,
	discipline varchar NULL,
	status varchar NULL,
	sender varchar NULL,
	"number" int4 NULL,
	characts varchar NULL,
  tenantid varchar NULL,
	CONSTRAINT incidents_pkey PRIMARY KEY (id),
	CONSTRAINT incidents_sourceenvid_key UNIQUE (sourceenvid)
);

CREATE TABLE safetymaps.units (
	id serial4 NOT NULL,
	"source" varchar NOT NULL,
	sourceenv varchar NOT NULL,
	sourceid varchar NOT NULL,
	sourceenvid varchar NULL,
	gmsstatuscode int4 NULL,
	sender varchar NULL,
	primairevoertuigsoort varchar NULL,
	lon numeric NULL,
	lat numeric NULL,
	speed int4 NULL,
	heading int4 NULL,
	eta int4 NULL,
	geom public.geometry(point, 4326) NULL,
  --incidentsourceid varchar NULL,
  --incidentinzetrol varchar NULL,
	CONSTRAINT units_pkey PRIMARY KEY (id),
	CONSTRAINT units_sourceenvid_key UNIQUE (sourceenvid)
);

insert into safetymaps.settings (name, value)
select 'safetyconnect_rq', 'true'
union select 'safetyconnect_rq_host', '10.233.184.139'
union select 'safetyconnect_rq_pass', 'okp6VLjjJ2H22B2r'
union select 'safetyconnect_rq_senders', ''
union select 'safetyconnect_rq_user', 'safetyconnect'
union select 'safetyconnect_rq_vhost', 'test'
union select 'safetyconnect_rq_tenants', ''