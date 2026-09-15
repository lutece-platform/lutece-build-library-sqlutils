--liquibase formatted sql
--lutece runAfter:mmm
--changeset ddd:init_db_ddd
INSERT INTO ddd_data VALUES (1);
