CREATE TABLE dummy
(
    id                                 BIGINT AUTO_INCREMENT NOT NULL,
    amount                             DECIMAL(20, 10)
);

INSERT INTO dummy (amount) VALUES (10.120);
INSERT INTO dummy (amount) VALUES (12.1200);
INSERT INTO dummy (amount) VALUES (13.0000);
INSERT INTO dummy (amount) VALUES (14.0);
INSERT INTO dummy (amount) VALUES (15);