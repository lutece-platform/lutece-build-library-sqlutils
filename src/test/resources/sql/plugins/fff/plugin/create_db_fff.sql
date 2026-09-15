--liquibase formatted sql
--lutece runAfter:core
--changeset fff:create_db_fff
CREATE TABLE fff_data (id INT);
