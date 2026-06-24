# 开发环境与版本

## 推荐版本
- `JDK 17`
- `Maven 3.9.x`
- `Spring Boot 3.4.2`
- `Spring Cloud 2024.0.1`
- `MySQL 8.0`
- `Redis 7.x`
- `Consul 1.18+`

## 本地配置
- 使用 UTF-8 编码
- 统一时区为 `Asia/Shanghai`
- 服务端口建议：
  - `viewer-service: 8081`
  - `finance-service: 8082`
  - `analytics-service: 8083`
  - `simulator-service: 8084`

## 启动前检查
- 确认数据库已创建并导入 `sql/schema.sql`
- 确认注册中心可访问
- 确认 Maven 能正常拉取依赖
- 确认 JVM 使用 `-Dfile.encoding=UTF-8`
