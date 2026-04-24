# API 接口说明

## 认证与基础数据

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/health`
- `GET /api/position/list`

## LLM 与用户设置

- `GET /api/llm/providers`
- `GET /api/user/llm-config`
- `PUT /api/user/llm-config`
- `GET /api/user/profile`
- `PUT /api/user/profile`

## 简历与面试

- `POST /api/resume/upload`
- `GET /api/resume/list`
- `DELETE /api/resume/{resumeId}`
- `POST /api/interview/start`
- `GET /api/interview/sessions`
- `GET /api/interview/{sessionId}/messages`
- `POST /api/interview/{sessionId}/chat`
- `POST /api/interview/{sessionId}/stage`
- `POST /api/interview/{sessionId}/finish`

## 数据分析

- `GET /api/analytics/radar`
- `GET /api/analytics/trend`
- `GET /api/analytics/weaknesses`
