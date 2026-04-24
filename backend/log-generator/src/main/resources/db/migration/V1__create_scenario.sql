CREATE SCHEMA IF NOT EXISTS generator;

CREATE TABLE generator.scenario (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    type          VARCHAR(64)  NOT NULL,
    attack_type   VARCHAR(64)  NOT NULL,
    successful    BOOLEAN      NOT NULL,
    rate          BIGINT       NOT NULL,
    fraud_ratio   BIGINT       NOT NULL
);
