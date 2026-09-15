--liquibase formatted sql
--lutece runAfter:eee
--changeset eee:create_db_eee
CREATE TABLE eee_data (id INT);
