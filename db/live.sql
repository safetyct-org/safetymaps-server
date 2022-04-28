CREATE TABLE safetymaps.live (
	incident varchar NOT NULL,
	name varchar NOT NULL,
	url varchar NOT NULL,
	vehicle varchar NULL,
	vod bool NULL,
	disableaudio bool NULL,
	debug bool NULL,
	CONSTRAINT live_pkey PRIMARY KEY (incident, url)
);

CREATE TABLE safetymaps.live_vehicles (
	vehicle varchar NOT NULL,
	url varchar NOT NULL,
	username varchar NULL,
	pass varchar NULL,
	CONSTRAINT live_vehicles_pkey PRIMARY KEY (vehicle, url)
);
