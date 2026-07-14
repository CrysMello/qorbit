#!/bin/bash
set -e

echo OVERRIDE
cat /etc/systemd/system/qorbit.service.d/override.conf || true

echo XVFB
systemctl status xvfb.service --no-pager -n 5 || true

echo QORBIT_ENV
systemctl show -p Environment qorbit.service || true

echo QORBIT_PROC
PID=$(systemctl show -p MainPID --value qorbit.service)
if [ -n "$PID" ] && [ "$PID" != "0" ]; then
  sudo tr "\0" "\n" < /proc/$PID/environ | grep -E "DISPLAY|JAVA_TOOL_OPTIONS|scanner|SCANNER|chromedriver|CHROME" || true
fi

echo TEST
cd /tmp
rm -f login.html qorbit_cookie.txt gravacao_headers.txt gravacao_body.txt csrf.txt
curl -s -c qorbit_cookie.txt http://[::1]:8080/auth/login -o login.html
if [ ! -s login.html ]; then echo "LOGIN_PAGE_EMPTY"; exit 1; fi
CSRF=$(python3 - <<'PY'
import re, sys
html=open('login.html','r',encoding='utf-8').read()
m=re.search(r'name="_csrf" value="([^"]+)"', html)
if not m:
    sys.exit(1)
print(m.group(1))
PY
)
echo CSRF=$CSRF
if [ -z "$QORBIT_CHECK_EMAIL" ] || [ -z "$QORBIT_CHECK_PASSWORD" ]; then
  echo "Defina QORBIT_CHECK_EMAIL e QORBIT_CHECK_PASSWORD antes de rodar este script."
  exit 1
fi
curl -i -b qorbit_cookie.txt -c qorbit_cookie.txt -X POST -L -s -o /dev/null -w 'LOGIN_STATUS:%{http_code}\n' http://[::1]:8080/auth/login -F "email=$QORBIT_CHECK_EMAIL" -F "password=$QORBIT_CHECK_PASSWORD" -F "_csrf=$CSRF"
echo CALL
curl -i -b qorbit_cookie.txt -X POST http://[::1]:8080/api/gravacao/iniciar -H 'Content-Type: application/json' -d '{"url":"https://example.com"}' -s -D gravacao_headers.txt -o gravacao_body.txt || true
echo HEAD
sed -n '1,120p' gravacao_headers.txt || true
echo BODY
sed -n '1,200p' gravacao_body.txt || true
echo LOG
sudo sed -n '1,200p' /var/log/chromedriver.log || true
sudo sed -n '1,200p' /tmp/chromedriver.log || true
