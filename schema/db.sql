CREATE TABLE customers (
  id        BIGSERIAL PRIMARY KEY,
  firstname VARCHAR(255) NOT NULL,
  lastname  VARCHAR(255),
  email     VARCHAR(255)
);
