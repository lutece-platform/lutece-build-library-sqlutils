--liquibase formatted sql
--lutece runAfter:ccc
--changeset ddd:create_db_ddd
CREATE TABLE ddd_data (id INT);
