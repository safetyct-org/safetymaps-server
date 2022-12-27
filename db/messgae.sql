CREATE TABLE safetymaps.messages (
  id serial PRIMARY KEY,
	dtgstart timestamp NOT NULL,
  dtgend timestamp NOT NULL,
	subject varchar NULL,
  description varchar NULL,
	username varchar NULL
);
