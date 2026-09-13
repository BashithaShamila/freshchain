-- One physical Postgres, one logical database per service. No service has
-- credentials for another service's database; the boundary is enforced here,
-- not by convention.
CREATE DATABASE freshchain_order;
CREATE DATABASE freshchain_inventory;
CREATE DATABASE freshchain_fulfillment;

\connect freshchain_inventory
CREATE EXTENSION IF NOT EXISTS vector;
