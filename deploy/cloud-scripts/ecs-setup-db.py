#!/usr/bin/env python3
"""Create the local MySQL account and restore a gzipped Shengtai dump.

Run as root on the ECS after copying backend.env and migration.sql.gz.
No credentials are printed or stored in this script.
"""

import gzip
import pathlib
import subprocess


ROOT = pathlib.Path('/opt/shengtai')
ENV_FILE = pathlib.Path('/etc/shengtai/backend.env')
DUMP_FILE = ROOT / 'incoming/migration.sql.gz'


def env_value(name):
    for line in ENV_FILE.read_text().splitlines():
        if line.startswith(name + '='):
            value = line.split('=', 1)[1].strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            return value
    raise RuntimeError(f'Missing {name} in backend.env')


def sql_string(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "\\'") + "'"


user = env_value('DB_USER')
password = env_value('DB_PASSWORD')
if not user or not password:
    raise RuntimeError('Empty database account setting')

sql = [
    'CREATE DATABASE IF NOT EXISTS billiard_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;',
]
for host in ('localhost', '127.0.0.1'):
    account = sql_string(user) + '@' + sql_string(host)
    sql += [
        f'CREATE USER IF NOT EXISTS {account} IDENTIFIED BY {sql_string(password)};',
        f'ALTER USER {account} IDENTIFIED BY {sql_string(password)};',
        f'GRANT ALL PRIVILEGES ON billiard_db.* TO {account};',
    ]
subprocess.run(['mysql', '--protocol=socket'], input='\n'.join(sql),
               text=True, check=True, stdout=subprocess.DEVNULL)

with gzip.open(DUMP_FILE, 'rb') as dump:
    subprocess.run(['mysql', '--protocol=socket', 'billiard_db'], input=dump.read(),
                   check=True, stdout=subprocess.DEVNULL)

result = subprocess.check_output([
    'mysql', '--protocol=socket', '-N', '-e',
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='billiard_db'",
], text=True).strip()
print(f'Restored billiard_db: {result} tables')
