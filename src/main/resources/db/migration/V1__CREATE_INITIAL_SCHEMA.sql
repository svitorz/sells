create table users(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  name varchar(100) not null,
  email varchar(100) not null UNIQUE,
  username varchar(100) not null UNIQUE,
  password varchar(255) not null,
  role varchar(50) not null,
  birthday_date DATE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id),
  constraint ck_users_role check (role in ('ADMIN', 'USER'))
);

create table categories(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  name varchar(255) not null,
  description TEXT,
  PRIMARY KEY(id)
);

create table products(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  name varchar(255) not null,
  price NUMERIC(10, 2) not null,
  description TEXT not null,
  stock INTEGER not null,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id),
  constraint ck_products_price check (price >= 0)
);

create table product_category(
  product_id BIGINT not null,
  category_id BIGINT not null,
  PRIMARY KEY(product_id, category_id),
  constraint fk_product_category_product foreign key(product_id) references products(id) on delete cascade,
  constraint fk_product_category_category foreign key(category_id) references categories(id) on delete cascade
);

create table customers(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  name varchar(255) not null,
  email varchar(255) not null,
  birthday_date DATE,
  phone varchar(255) not null,
  street varchar(150) not null,
  number varchar(20) not null,
  complement varchar(100),
  neighborhood varchar(100) not null,
  city varchar(100) not null,
  state varchar(2) not null,
  zip_code varchar(10) not null,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id)
);

create table sales(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  date TIMESTAMP not null,
  subtotal NUMERIC(10, 2) not null,
  status varchar(20) not null,
  customer_id BIGINT,
  user_id BIGINT not null,
  PRIMARY KEY(id),
  constraint ck_sales_subtotal check (subtotal >= 0),
  constraint ck_sales_status check (status in ('DRAFT', 'CANCELLED', 'COMPLETED')),
  constraint fk_sales_customer foreign key(customer_id) references customers(id),
  constraint fk_sales_user foreign key(user_id) references users(id)
);

create table sale_items(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  product_id BIGINT not null,
  quantity INTEGER not null,
  unit_price NUMERIC(10, 2) not null,
  sale_id BIGINT not null,
  PRIMARY KEY(id),
  constraint ck_sale_items_quantity check (quantity > 0),
  constraint ck_sale_items_unit_price check (unit_price >= 0),
  constraint fk_sale_items_product foreign key(product_id) references products(id),
  constraint fk_sale_items_sale foreign key(sale_id) references sales(id) on delete cascade
);
