# 安全策略

当前维护范围是默认分支 `main`；仓库尚未发布单独维护的版本线。

请不要通过公开议题报告疑似安全漏洞。使用 GitHub 的[私密漏洞报告](https://github.com/zyyyyynnn/Prelude/security/advisories/new)功能提交复现条件、影响范围与必要证据。

普通缺陷、功能建议和非敏感安全加固建议仍可使用公开议题。

当前安全基线：服务端 Session 认证（Spring Session Redis，HttpOnly Cookie），密码 Argon2id，OAuth（Google/GitHub）身份绑定，CSRF（XSRF-TOKEN Cookie + X-XSRF-TOKEN Header）与 Origin 校验，资源所有权以 accountId 隔离，对象存储下载走先授权后短 TTL 预签名。
