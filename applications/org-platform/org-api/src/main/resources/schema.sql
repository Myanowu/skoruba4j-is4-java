CREATE TABLE IF NOT EXISTS org_unit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_id INTEGER,
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  path TEXT NOT NULL,
  unit_type TEXT NOT NULL DEFAULT 'DEPT',
  enabled INTEGER NOT NULL DEFAULT 1,
  manager_user_id TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS org_role (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL,
  code TEXT NOT NULL,
  name TEXT NOT NULL,
  UNIQUE (org_unit_id, code),
  FOREIGN KEY (org_unit_id) REFERENCES org_unit(id)
);

CREATE TABLE IF NOT EXISTS org_person (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_key TEXT NOT NULL UNIQUE,
  idp_user_id TEXT UNIQUE,
  employee_no TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  email TEXT,
  login_name TEXT,
  phone TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS org_membership (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  org_unit_id INTEGER,
  org_role_id INTEGER,
  title TEXT,
  is_primary INTEGER NOT NULL DEFAULT 0,
  effective_from TEXT NOT NULL DEFAULT (datetime('now')),
  effective_to TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (org_unit_id) REFERENCES org_unit(id),
  FOREIGN KEY (org_role_id) REFERENCES org_role(id)
);

CREATE TABLE IF NOT EXISTS permission_code (
  code TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  category TEXT
);

CREATE TABLE IF NOT EXISTS role_permission (
  org_role_id INTEGER NOT NULL,
  permission_code TEXT NOT NULL,
  PRIMARY KEY (org_role_id, permission_code),
  FOREIGN KEY (org_role_id) REFERENCES org_role(id),
  FOREIGN KEY (permission_code) REFERENCES permission_code(code)
);

CREATE TABLE IF NOT EXISTS form_definition (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL,
  version INTEGER NOT NULL,
  title TEXT NOT NULL,
  schema_json TEXT NOT NULL,
  list_fields_json TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  process_def_code TEXT,
  UNIQUE (code, version)
);

CREATE TABLE IF NOT EXISTS form_instance (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  form_def_id INTEGER NOT NULL,
  form_code TEXT NOT NULL,
  form_version INTEGER NOT NULL,
  org_unit_id INTEGER,
  created_by TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (form_def_id) REFERENCES form_definition(id)
);

CREATE TABLE IF NOT EXISTS process_definition (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL,
  version INTEGER NOT NULL,
  title TEXT NOT NULL,
  nodes_json TEXT NOT NULL,
  bound_form_code TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  UNIQUE (code, version)
);

CREATE TABLE IF NOT EXISTS process_instance (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  process_def_id INTEGER NOT NULL,
  process_code TEXT NOT NULL,
  form_instance_id INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'RUNNING',
  current_node_key TEXT,
  starter_user_id TEXT NOT NULL,
  org_unit_id INTEGER,
  amount REAL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (process_def_id) REFERENCES process_definition(id),
  FOREIGN KEY (form_instance_id) REFERENCES form_instance(id)
);

CREATE TABLE IF NOT EXISTS process_task (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  process_instance_id INTEGER NOT NULL,
  node_key TEXT NOT NULL,
  candidate_user_ids TEXT,
  candidate_group TEXT,
  status TEXT NOT NULL DEFAULT 'OPEN',
  action TEXT,
  actioned_by TEXT,
  remarks TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  closed_at TEXT,
  FOREIGN KEY (process_instance_id) REFERENCES process_instance(id)
);

CREATE TABLE IF NOT EXISTS approver_set_template (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER,
  code TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  kind TEXT NOT NULL DEFAULT 'GENERIC',
  steps_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS query_export_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  form_code TEXT,
  filter_json TEXT,
  row_count INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO permission_code (code, name, category) VALUES
  ('PLATFORM_ADMIN', 'Platform admin', 'system'),
  ('ORG_ADMIN', 'Org admin', 'org'),
  ('FORM_SUBMIT', 'Submit forms', 'form'),
  ('FORM_QUERY_ORG', 'Query org forms', 'form'),
  ('TASK_APPROVE', 'Approve tasks', 'workflow');
