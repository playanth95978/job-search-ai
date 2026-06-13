-- CV Builder: one editable resume per user + immutable version snapshots.

CREATE TABLE IF NOT EXISTS resume
(
  id         uuid                     DEFAULT uuid_generate_v4() PRIMARY KEY,
  user_id    varchar(100)             NOT NULL UNIQUE,
  title      varchar(255),
  template   varchar(50),
  data       jsonb                    NOT NULL,
  version    integer                  NOT NULL DEFAULT 1,
  created_at timestamp with time zone DEFAULT now(),
  updated_at timestamp with time zone DEFAULT now()
);

CREATE INDEX IF NOT EXISTS resume_user_idx ON resume (user_id);

CREATE TABLE IF NOT EXISTS resume_version
(
  id         uuid                     DEFAULT uuid_generate_v4() PRIMARY KEY,
  resume_id  uuid                     NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
  version    integer                  NOT NULL,
  title      varchar(255),
  template   varchar(50),
  data       jsonb                    NOT NULL,
  created_at timestamp with time zone DEFAULT now()
);

CREATE INDEX IF NOT EXISTS resume_version_resume_idx ON resume_version (resume_id);
