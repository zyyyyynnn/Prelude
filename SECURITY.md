# 安全策略

当前维护范围是默认分支 `main`；仓库尚未发布单独维护的版本线。

安全漏洞只经 GitHub 的[私密漏洞报告](https://github.com/zyyyynn/Prelude/security/advisories/new)提交，附复现条件、影响范围与必要证据。公开议题用于普通缺陷、功能建议和非敏感安全加固建议。

当前安全基线：服务端 Session 认证（Spring Session Redis，HttpOnly Cookie），密码 Argon2id，OAuth（Google/GitHub）身份绑定，CSRF（XSRF-TOKEN Cookie + X-XSRF-TOKEN Header）与 Origin 校验，资源所有权以 accountId 隔离，对象存储下载走先授权后短 TTL 预签名。
