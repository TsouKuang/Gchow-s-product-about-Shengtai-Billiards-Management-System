#!/usr/bin/env python3
"""Check the configured WeChat AppSecret without printing credentials or tokens."""

import json
import pathlib
import urllib.parse
import urllib.request


def value(name):
    for line in pathlib.Path('/etc/shengtai/backend.env').read_text().splitlines():
        if line.startswith(name + '='):
            result = line.split('=', 1)[1].strip()
            if len(result) >= 2 and result[0] == result[-1] and result[0] in "\"'":
                result = result[1:-1]
            return result
    raise RuntimeError(f'Missing {name}')


query = urllib.parse.urlencode({
    'grant_type': 'client_credential',
    'appid': value('WECHAT_APPID'),
    'secret': value('WECHAT_SECRET'),
})
with urllib.request.urlopen('https://api.weixin.qq.com/cgi-bin/token?' + query, timeout=15) as response:
    result = json.load(response)
print('OK' if result.get('access_token') else 'ERROR ' + str(result.get('errcode')))
