create table users(
  id BIGINT,
  name varchar(100) not null,
  email varchar(100) not null,
  password varchar(255) not null,
  role varchar(50) not null,
  created_at TIMESTAMP,
  update_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id)
);
