create table users(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  name varchar(100) not null,
  email varchar(100) not null UNIQUE,
  password varchar(255) not null,
  role varchar(50) not null,
  created_at TIMESTAMP,
  update_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id)
);
