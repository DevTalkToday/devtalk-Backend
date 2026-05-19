-- Social OAuth login support, including GitHub and Google.
-- With spring.jpa.hibernate.ddl-auto=update, Hibernate creates/applies these automatically.
-- Keep this file as a manual reference when you want to manage schema directly.

ALTER TABLE users
    ADD COLUMN email varchar(255) NULL;

ALTER TABLE users
    MODIFY COLUMN password_hash varchar(255) NULL;

ALTER TABLE users
    ADD COLUMN description varchar(500) NULL;

ALTER TABLE users
    ADD COLUMN profile_completed bit NOT NULL DEFAULT 0;

CREATE TABLE oauth_accounts (
    id bigint NOT NULL AUTO_INCREMENT,
    provider varchar(30) NOT NULL,
    provider_user_id varchar(120) NOT NULL,
    email varchar(255) NULL,
    user_id bigint NOT NULL,
    created_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_oauth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
