#!/usr/bin/env python3
"""Verify ECS admin login locally without printing the password or token."""

import json
import pathlib
import sys
import urllib.request


def value(name):
    for line in pathlib.Path('/etc/shengtai/backend.env').read_text().splitlines():
        if line.startswith(name + '='):
            result = line.split('=', 1)[1].strip()
            if len(result) >= 2 and result[0] == result[-1] and result[0] in "\"'":
                result = result[1:-1]
            return result
    raise RuntimeError(f'Missing {name}')


credentials = (json.load(sys.stdin) if '--stdin' in sys.argv else
               {'username': 'admin', 'password': value('ADMIN_PASSWORD')})
body = json.dumps(credentials).encode()
request = urllib.request.Request('http://127.0.0.1:8080/api/auth/admin',
                                 data=body, headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=10) as response:
    result = json.load(response)
print('OK' if result.get('token') else 'INVALID_RESPONSE')
