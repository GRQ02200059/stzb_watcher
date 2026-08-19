(function () {
  'use strict';
  var TOKEN_KEY = 'stzb.local.auth.token';
  var USER_KEY = 'stzb.local.auth.username';
  var gate = document.getElementById('local-auth-gate');
  if (!gate) return;
  var form = document.getElementById('local-auth-form');
  var username = document.getElementById('local-auth-username');
  var password = document.getElementById('local-auth-password');
  var submit = document.getElementById('local-auth-submit');
  var toggle = document.getElementById('local-auth-register-toggle');
  var message = document.getElementById('local-auth-message');
  var registration = false;

  window.STZBLocalAuth = {
    isGranted: false,
    token: function () { return localStorage.getItem(TOKEN_KEY) || ''; },
    clear: function () { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); },
  };

  function setMessage(text, error) {
    message.textContent = text || '';
    message.dataset.error = error ? 'true' : 'false';
  }
  function setBusy(busy) {
    submit.disabled = busy;
    submit.textContent = busy ? '校验中…' : (registration ? '注册并登录' : '登录');
  }
  function showGate() { document.body.classList.add('local-auth-required'); gate.hidden = false; username.value = localStorage.getItem(USER_KEY) || ''; username.focus(); }
  function enter(result, fallbackName) {
    var token = result && result.sessionToken;
    if (!token) { fail('认证服务未返回有效登录凭证'); return; }
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, (result.user && result.user.username) || fallbackName || username.value.trim());
    window.STZBLocalAuth.isGranted = true;
    document.body.classList.remove('local-auth-required');
    gate.hidden = true;
    setMessage('');
  }
  function fail(text) { window.STZBLocalAuth.isGranted = false; showGate(); setMessage(text, true); }
  function request(path, body) {
    return fetch(path, { method: 'POST', headers: {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}, body: JSON.stringify(body), cache: 'no-store' }).then(function (response) { return response.json().then(function (json) { return {ok: response.ok, json: json}; }); });
  }
  function errorText(result) { return (result && result.error && result.error.message) || '认证失败，请检查账号状态'; }
  function verify() {
    var token = window.STZBLocalAuth.token();
    if (!token) { showGate(); return; }
    setMessage('正在校验本地登录状态…');
    request('/api/local-auth/verify', {token: token}).then(function (result) {
      if (result.ok && result.json && result.json.ok) {
        window.STZBLocalAuth.isGranted = true; document.body.classList.remove('local-auth-required'); gate.hidden = true; setMessage('');
      } else { window.STZBLocalAuth.clear(); fail(errorText(result.json)); }
    }).catch(function () { fail('认证服务器暂时无法连接，请启动时完成一次在线校验'); });
  }
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var name = username.value.trim(); var secret = password.value;
    if (!name || !secret) { setMessage('请输入用户名和密码', true); return; }
    setBusy(true);
    request(registration ? '/api/local-auth/register' : '/api/local-auth/login', {username: name, password: secret}).then(function (result) {
      if (result.ok && result.json && result.json.ok) enter(result.json, name); else setMessage(errorText(result.json), true);
    }).catch(function () { setMessage('认证服务器暂时无法连接', true); }).finally(function () { password.value = ''; setBusy(false); });
  });
  toggle.addEventListener('click', function () { registration = !registration; toggle.textContent = registration ? '已有账号？返回登录' : '没有账号？注册'; submit.textContent = registration ? '注册并登录' : '登录'; setMessage(''); });
  showGate();
  verify();
}());
