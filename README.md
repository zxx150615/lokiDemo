# loki-demo

一个**最小化** Spring Boot 项目，用于给刚搭建好的 [Grafana Loki](https://grafana.com/oss/loki/) 提供稳定的、结构化的 JSON 日志源。

- 启动后**每 10 秒**输出一条 JSON 格式日志到容器 stdout
- 日志在 INFO / WARN / ERROR 之间轮转，便于在 Loki 中按 `level` 过滤验证
- 不依赖任何外部组件，`docker compose up -d --build` 一键启动

---

## 一、技术栈

| 项 | 选型 |
| --- | --- |
| 语言 / JDK | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 构建 | Maven |
| 日志 | Logback + logstash-logback-encoder 7.4 |
| 运行时 | Docker / Docker Compose |

## 二、日志格式

每条日志固定输出为一行 JSON，字段如下：

```json
{
  "service": "order-service",
  "env": "prod",
  "level": "INFO",
  "traceId": "a1b2c3d4e5f60718",
  "msg": "order created: orderId=20260426-0001, amount=199.00"
}
```

字段说明：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| `service` | `spring.application.name`（环境变量 `SERVICE_NAME` 可覆盖） | 服务名 |
| `env` | `app.env`（环境变量 `APP_ENV` 可覆盖） | 环境标识，如 `prod` / `dev` |
| `level` | Logback `%level` | 日志级别 |
| `traceId` | 每次任务执行时由 MDC 写入 16 位短 UUID | 链路追踪 id |
| `msg` | Logback `%message` | 业务日志正文 |

> 字段渲染由 [`src/main/resources/logback-spring.xml`](src/main/resources/logback-spring.xml) 中的 `LoggingEventCompositeJsonEncoder` + `pattern` provider 严格控制，**不会输出多余字段**，方便在 Loki 中用 `| json` 直接解析后做标签提取。

## 三、快速开始

### 1. 启动

```bash
docker compose up -d --build
```

### 2. 查看日志

```bash
docker compose logs -f order-service
```

正常情况下每 10 秒会看到一条形如下面的日志：

```json
{"service":"order-service","env":"prod","level":"INFO","traceId":"a1b2c3d4e5f60718","msg":"scheduled heartbeat ok"}
```

### 3. 停止

```bash
docker compose down
```

## 四、自定义参数

通过环境变量覆盖默认值（在 [`docker-compose.yml`](docker-compose.yml) 中修改 `environment` 即可）：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVICE_NAME` | `order-service` | 写入到 `service` 字段 |
| `APP_ENV` | `prod` | 写入到 `env` 字段 |

例如想把这个容器伪装成另一个服务用于多服务采集测试：

```yaml
environment:
  SERVICE_NAME: payment-service
  APP_ENV: dev
```

## 五、对接 Loki

本项目把日志全部打到 **stdout**，无需挂载日志卷，对接 Loki 有两种主流姿势：

### 方式 A：Docker Loki Logging Driver（最简单）

在宿主机安装插件后，给容器声明 logging driver：

```bash
docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions
```

然后给 `docker-compose.yml` 里的服务追加：

```yaml
    logging:
      driver: loki
      options:
        loki-url: "http://<your-loki-host>:3100/loki/api/v1/push"
        loki-pipeline-stages: |
          - json:
              expressions:
                level: level
                service: service
                env: env
                traceId: traceId
        labels: service,env
```

### 方式 B：Promtail / Grafana Alloy 的 Docker 服务发现

让 Promtail（或 Alloy）通过 Docker socket 自动发现容器，再用 `pipeline_stages.json` 把字段提取为标签：

```yaml
pipeline_stages:
  - json:
      expressions:
        level:
        service:
        env:
        traceId:
  - labels:
      level:
      service:
      env:
```

之后在 Grafana Explore 里就可以这样查询：

```logql
{service="order-service", env="prod"} |= "payment" | json | level="ERROR"
```

## 六、项目结构

```
lokiDemo/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── README.md
└── src/main/
    ├── java/com/example/lokidemo/
    │   ├── LokiDemoApplication.java   # 启动类，@EnableScheduling
    │   └── LogScheduler.java          # 每 10 秒轮询输出不同级别日志
    └── resources/
        ├── application.yml            # 服务名 / env 配置
        └── logback-spring.xml         # JSON 日志格式定义
```
