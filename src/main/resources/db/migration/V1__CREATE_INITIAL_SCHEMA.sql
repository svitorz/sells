create table users(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  name varchar(100) not null,
  email varchar(100) not null UNIQUE,
  username varchar(100) not null UNIQUE,
  password varchar(255) not null,
  role varchar(50) not null,
  birthday_date DATE,
  active BOOLEAN not null default true,
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
  barcode varchar(50),
  price NUMERIC(10, 2) not null,
  unit varchar(2) not null,
  description TEXT not null,
  stock NUMERIC(10, 3) not null,
  version BIGINT not null default 0,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id),
  constraint ck_products_price check (price >= 0),
  constraint ck_products_unit check (unit in ('UN', 'KG')),
  constraint ck_products_stock check (stock >= 0)
);

create unique index ux_products_barcode on products(barcode) where deleted_at is null and barcode is not null;

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
  phone varchar(20) not null,
  email varchar(255),
  birthday_date DATE,
  cpf_cnpj varchar(14),
  street varchar(150),
  number varchar(20),
  complement varchar(100),
  neighborhood varchar(100),
  city varchar(100),
  state varchar(2),
  zip_code varchar(10),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY(id),
  constraint ck_customers_cpf_cnpj check (cpf_cnpj is null or cpf_cnpj ~ '^([0-9]{11}|[0-9]{14})$')
);

create unique index ux_customers_cpf_cnpj on customers(cpf_cnpj) where deleted_at is null and cpf_cnpj is not null;

create table sales(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  date TIMESTAMP not null,
  subtotal NUMERIC(10, 2) not null,
  status varchar(20) not null,
  customer_id BIGINT,
  user_id BIGINT not null,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  cancelled_at TIMESTAMP,
  cancelled_by_user_id BIGINT,
  cancel_reason TEXT,
  version BIGINT not null default 0,
  PRIMARY KEY(id),
  constraint ck_sales_subtotal check (subtotal >= 0),
  constraint ck_sales_status check (status in ('DRAFT', 'CANCELLED', 'COMPLETED')),
  constraint ck_sales_cancellation check (
    status <> 'CANCELLED'
    or (cancelled_at is not null and cancelled_by_user_id is not null and coalesce(trim(cancel_reason), '') <> '')
  ),
  constraint fk_sales_customer foreign key(customer_id) references customers(id),
  constraint fk_sales_user foreign key(user_id) references users(id),
  constraint fk_sales_cancelled_by foreign key(cancelled_by_user_id) references users(id)
);

create table sale_items(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  product_id BIGINT not null,
  quantity NUMERIC(10, 3) not null,
  unit_price NUMERIC(10, 2) not null,
  sale_id BIGINT not null,
  PRIMARY KEY(id),
  constraint uk_sale_items_sale_product unique (sale_id, product_id),
  constraint ck_sale_items_quantity check (quantity > 0),
  constraint ck_sale_items_unit_price check (unit_price >= 0),
  constraint fk_sale_items_product foreign key(product_id) references products(id),
  constraint fk_sale_items_sale foreign key(sale_id) references sales(id) on delete cascade
);

create table stock_movements(
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  product_id BIGINT not null,
  type varchar(20) not null,
  quantity NUMERIC(10, 3) not null,
  sale_id BIGINT,
  user_id BIGINT not null,
  created_at TIMESTAMP not null,
  notes TEXT,
  PRIMARY KEY(id),
  constraint ck_stock_movements_type check (type in ('ENTRY', 'SALE', 'RETURN', 'ADJUSTMENT')),
  constraint ck_stock_movements_quantity check (
    quantity <> 0
    and (type not in ('ENTRY', 'RETURN') or quantity > 0)
    and (type <> 'SALE' or quantity < 0)
  ),
  constraint ck_stock_movements_sale check (type not in ('SALE', 'RETURN') or sale_id is not null),
  constraint ck_stock_movements_adjustment_notes check (type <> 'ADJUSTMENT' or coalesce(trim(notes), '') <> ''),
  constraint fk_stock_movements_product foreign key(product_id) references products(id),
  constraint fk_stock_movements_sale foreign key(sale_id) references sales(id),
  constraint fk_stock_movements_user foreign key(user_id) references users(id)
);
