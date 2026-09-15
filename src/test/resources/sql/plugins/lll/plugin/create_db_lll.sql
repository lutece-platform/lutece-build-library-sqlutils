--liquibase formatted sql
--changeset lll:create_db_lll
CREATE TABLE lll_data (id INT);
--lutece runAfter:ccc
