# 微信 SDK 试手

> 一个 Spring Boot 练手项目：把小程序 / 公众号的常用开放接口，收敛成一套自己的 REST API。

![Java](https://img.shields.io/badge/Java-8%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 这个项目是做什么的

业务里经常要用到这些能力：小程序登录、发订阅消息、生成小程序码、拿用户手机号；公众号还要取 `access_token`、拉已发布文章列表。

如果每个业务系统各对接一遍，会出现三个问题：

1. 微信接口散落在各处，错误码和返回结构不统一；
2. 每个服务各自获取 `access_token`，容易触发微信的频率限制；
3. 换 `appId`、加一个小程序，要改很多地方。

所以这个项目做了一层很薄的聚合网关：**对外只暴露自有接口，对内统一管理 `appId`、`secret` 和 `access_token`**，业务方一次接入即可复用。

> 定位说明：这是一个用于练手 / 学习第三方 API 封装的小项目，不是生产级中台。上生产前请先看文末的「已知限制」和「安全说明」。

---

## 功能一览

| 能力 | 接口 | 说明 |
| --- | --- | --- |
| 小程序登录 | `GET /login` | `code` 换 `openid` / `unionid` |
| 订阅消息 | `POST /sendMessage` | 发送订阅消息，支持模板数据与跳转页 |
| 无限制小程序码 | `POST /getUnlimitedQRCode` | 直接返回图片字节流 |
| 用户手机号 | `GET /getPhoneNumber` | 用 `code` 换取手机号 |
| 公众号发布列表 | `GET /getFreePublishList` | 分页获取已发布文章 |
| 公众号 token | `GET /getGzhToken` | 调试用，直接返回 token |

此外还做了：

- **多小程序路由**：一套服务配置多个 `appId`，调用时可按需指定；
- **token 统一托管**：进程内缓存 + 提前 600 秒过期 + 双重检查，减少重复请求；
- **统一错误透传**：微信的 `errcode` / `errmsg` 原样返回，方便上游排障；
- **二进制透传**：二维码不做二次编解码，直接回传 `byte[]`。

---

## 技术栈

| 层次 | 技术 | 版本 | 用途 |
| --- | --- | --- | --- |
| 语言 | Java | 8 | 全项目 |
| 框架 | Spring Boot | 2.7.18 | Web、依赖注入、配置绑定 |
| Web | Spring MVC | 5.3.39 | REST 接口、`ResponseEntity` |
| HTTP 客户端 | Forest | 1.5.18 | 调用微信 API（连接池 / 超时 / TLS） |
| HTTP 客户端 | RestTemplate | Spring 自带 | 下载二维码图片字节流 |
| JSON | FastJSON | 2.0.64 | 请求体与微信响应的序列化 |
| 工具库 | Hutool | 5.7.21 | `TimedCache` 本地缓存、字符串工具 |
| 简化代码 | Lombok | Boot 托管 | DTO / VO 的 getter、setter |
| 测试 | JUnit 5 | Boot 托管 | 上下文冒烟测试、缓存单测 |
| 构建 | Maven | 3.6+ | 打包为可执行 jar |

---

## 项目结构

```text
wechat-sdk-demo
├── pom.xml
├── README.md
├── LICENSE
├── .gitignore
├── .github/workflows/maven.yml
└── src
    ├── main
    │   ├── java/com/example/wechatsdk
    │   │   ├── WechatSdkApplication.java        # 启动类
    │   │   ├── controller
    │   │   │   └── WechatSdkController.java     # 全部对外接口 + token 管理
    │   │   ├── domain
    │   │   │   ├── dto                          # 请求参数
    │   │   │   └── vo                           # 响应对象
    │   │   ├── handler
    │   │   │   └── GlobalExceptionHandler.java  # 统一异常出口
    │   │   └── util
    │   │       └── HutoolCache.java             # 进程内缓存
    │   └── resources
    │       └── application.yml                  # 配置（凭据走环境变量）
    └── test/java/com/example/wechatsdk
        ├── WechatSdkApplicationTests.java
        └── HutoolCacheTest.java
```

---

## 快速开始

### 1. 环境要求

- JDK 8 及以上
- Maven 3.6 及以上

### 2. 配置微信凭据

项目里的 `application.yml` 不包含任何真实凭据，全部通过环境变量注入：

| 环境变量 | 对应配置 | 说明 |
| --- | --- | --- |
| `WECHAT_MINI_APPID` | `wechat.appid` | 小程序 appId，多个用英文逗号分隔 |
| `WECHAT_MINI_SECRET` | `wechat.secret` | 小程序 secret，顺序与 appId 一一对应 |
| `WECHAT_MP_APPID` | `wechat.gzhappid` | 公众号 appId |
| `WECHAT_MP_SECRET` | `wechat.gzhsecret` | 公众号 secret |

多个小程序的写法（数量和顺序必须一致）：

```text
WECHAT_MINI_APPID=wx1111111111111111,wx2222222222222222
WECHAT_MINI_SECRET=secret_of_app1,secret_of_app2
```

PowerShell：

```powershell
$env:WECHAT_MINI_APPID = "wx1234567890abcdef"
$env:WECHAT_MINI_SECRET = "your_mini_secret"
$env:WECHAT_MP_APPID = "wxabcdef1234567890"
$env:WECHAT_MP_SECRET = "your_mp_secret"
mvn spring-boot:run
```

bash / zsh：

```bash
export WECHAT_MINI_APPID=wx1234567890abcdef
export WECHAT_MINI_SECRET=your_mini_secret
export WECHAT_MP_APPID=wxabcdef1234567890
export WECHAT_MP_SECRET=your_mp_secret
mvn spring-boot:run
```

> 不配置也能启动（启动日志会打印一条 warn），但调用微信接口时会返回 `appid` 相关的错误码。

### 3. 启动与打包

```bash
# 开发模式
mvn spring-boot:run

# 打包 & 运行
mvn clean package
java -jar target/wechat-sdk-demo.jar
```

默认地址：`http://localhost:8081/WechatSDK`

---

## 接口说明

所有接口都带 context-path，下文示例统一使用：

```text
http://localhost:8081/WechatSDK
```

### 1. 小程序登录

`GET /login`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `code` | 是 | `wx.login()` 拿到的临时登录凭证 |
| `wxAppId` | 否 | 指定小程序，不传则用配置中的第一个 |

```bash
curl "http://localhost:8081/WechatSDK/login?code=081ExampleCode"
```

成功：

```json
{
  "openid": "oUpF8uMuAJO_M2pxb1Q9zNjWeS6o",
  "unionid": null,
  "errcode": null,
  "errmsg": null
}
```

失败：

```json
{
  "openid": null,
  "unionid": null,
  "errcode": 40029,
  "errmsg": "invalid code"
}
```

> `code` 只能用一次；`session_key` 属于敏感信息，本项目不向前端返回。

### 2. 发送订阅消息

`POST /sendMessage`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `wxAppId` | 否 | 指定小程序 |
| `templateId` | 是 | 订阅消息模板 id |
| `touser` | 是 | 接收者 openid |
| `data` | 是 | 模板数据，支持 JSON 对象或 JSON 字符串 |
| `page` | 否 | 点击卡片后的跳转页面 |

```bash
curl -X POST "http://localhost:8081/WechatSDK/sendMessage" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": "TEMPLATE_ID",
    "touser": "OPENID",
    "page": "pages/index/index",
    "data": {
      "thing1": { "value": "测试订单" },
      "time2": { "value": "2026-10-25 20:00:00" }
    }
  }'
```

响应：

```json
{ "errcode": 0, "errmsg": "ok" }
```

> `data` 会被原样提交给微信，字段结构需与所选模板一致；本项目会把对象或 JSON 字符串统一转成 JSON 对象。

### 3. 生成无限制小程序码

`POST /getUnlimitedQRCode`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `scene` | 是 | 场景值，最大 32 个可见字符 |
| `page` | 否 | 扫码后跳转的页面 |
| `checkPath` | 否 | 是否校验 page 是否存在 |
| `envVersion` | 否 | `release` / `trial` / `develop` |
| `width` | 否 | 二维码边长（px） |
| `autoColor` | 否 | 是否自动配置线条颜色 |
| `lineColor` | 否 | 线条颜色，`autoColor=false` 时生效 |
| `isHyaline` | 否 | 是否需要透明底色 |

```bash
curl -X POST "http://localhost:8081/WechatSDK/getUnlimitedQRCode" \
  -H "Content-Type: application/json" \
  -d '{"scene":"userId=1001","page":"pages/index/index"}' \
  --output qrcode.png
```

成功时返回图片字节流（PNG）。**参数错误时微信返回的是 JSON 错误而不是图片**，调用方可以通过响应头的 `Content-Type` 区分。

### 4. 获取用户手机号

`GET /getPhoneNumber`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `code` | 是 | 手机号快速验证组件返回的 code |
| `wxAppId` | 否 | 指定小程序 |

```bash
curl "http://localhost:8081/WechatSDK/getPhoneNumber?code=PHONE_CODE"
```

成功：

```json
{
  "errcode": 0,
  "errmsg": "ok",
  "phoneNumber": "13800000000"
}
```

> 返回的是不带国家区号的 `purePhoneNumber`，需要区号可以自行扩展返回字段。

### 5. 公众号已发布文章列表

`GET /getFreePublishList`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `offset` | 是 | 偏移量，从 0 开始 |
| `count` | 是 | 返回数量，1~20 |
| `no_content` | 否 | 1 不返回正文，0 返回，默认 0 |

```bash
curl "http://localhost:8081/WechatSDK/getFreePublishList?offset=0&count=10"
```

响应直接透传微信结构：

```json
{
  "total_count": 2,
  "item_count": 2,
  "item": [
    {
      "article_id": "ARTICLE_ID",
      "title": "文章标题",
      "update_time": 1760000000
    }
  ]
}
```

> `offset < 0` 或 `count` 不在 1~20 之间时，接口返回 HTTP 400 和 `errcode: -1`。

### 6. 获取公众号 access_token

`GET /getGzhToken`

```bash
curl "http://localhost:8081/WechatSDK/getGzhToken"
```

返回纯文本 token。**仅用于调试**，生产环境不建议把 token 暴露给调用方。

### 错误码约定

| errcode | 含义 |
| --- | --- |
| `0` | 成功 |
| `-1` | 本服务的参数校验或内部错误 |
| 其他 | 微信官方错误码，原样透传 |

---

## access_token 是怎么管的

微信的 `access_token` 有两个特点：有效期 7200 秒，且有调用频率限制。项目里的处理方式：

1. **按 appId 分 key 缓存**：小程序用 `accessToken:{appId}`，公众号用 `gzh:accessToken`；
2. **提前过期**：缓存 TTL 取 `expires_in - 600`，最少 60 秒，避免刚好在临界点失效；
3. **双重检查**：缓存未命中时进入 `synchronized` 块，再查一次，减少并发下的重复请求；
4. **失败兜底**：微信返回的 `access_token` 为空时抛出异常，由统一异常处理返回 500，而不是把 `null` 拼进 URL。

局限也要说清楚：这是**单机内存缓存**。多实例部署时，每个实例会各自缓存、各自刷新，不能完全避免重复获取。生产环境建议换成 Redis + 分布式锁。

---

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8081` | 服务端口 |
| `server.servlet.context-path` | `/WechatSDK` | 统一前缀 |
| `wechat.appid` | 空 | 小程序 appId，对应 `WECHAT_MINI_APPID` |
| `wechat.secret` | 空 | 小程序 secret，对应 `WECHAT_MINI_SECRET` |
| `wechat.gzhappid` | 空 | 公众号 appId，对应 `WECHAT_MP_APPID` |
| `wechat.gzhsecret` | 空 | 公众号 secret，对应 `WECHAT_MP_SECRET` |
| `forest.max-connections` | `1000` | 连接池最大连接数 |
| `forest.max-route-connections` | `500` | 单路由最大连接数 |
| `forest.timeout` | `3000` | 请求超时（毫秒） |
| `forest.connect-timeout` | `3000` | 连接超时（毫秒） |
| `forest.read-timeout` | `3000` | 读取超时（毫秒） |
| `forest.max-retry-count` | `0` | 失败重试次数 |
| `forest.ssl-protocol` | `TLSv1.2` | HTTPS 协议版本 |
| `forest.log-response-content` | `false` | 是否打印响应内容（可能含 token，默认关闭） |

---

## 安全说明

- 仓库中**不包含任何真实的 appId / secret**，`application.yml` 只保留环境变量占位符；
- 如果你在本地填了真实凭据，请不要提交；建议使用 IDE 的运行配置或 `.env`（已在 `.gitignore` 中忽略）；
- **如果真实 secret 曾经进过 Git 历史，仅删除文件是不够的**，请到微信公众平台重置 AppSecret；
- 本项目没有做接口鉴权，不要直接暴露到公网；上线前至少加一层网关鉴权或签名校验；
- 日志默认不打印微信响应内容，避免 `access_token` 落盘。

---

## 已知限制

- `access_token` 是单机缓存，多实例部署会重复刷新；
- 没有接口鉴权、限流、重试与熔断；
- 公众号只支持单账号；
- 只覆盖了 6 个常用接口，微信其他能力需要自行扩展；
- 单元测试只覆盖上下文启动和缓存读写，没有 mock 微信接口。

## Roadmap

- [ ] `access_token` 迁移到 Redis，加分布式锁
- [ ] 接口鉴权（AppKey + 签名）与限流
- [ ] 微信 `errcode` 枚举化、统一错误码与告警
- [ ] 用 WireMock 补接口级单测
- [ ] 接入 OpenAPI / Swagger
- [ ] 抽成 `wechat-spring-boot-starter`，支持自动装配
- [ ] 升级 Spring Boot 3.x + JDK 17

---

## 参考文档

- [微信小程序服务端 API](https://developers.weixin.qq.com/miniprogram/dev/api-backend/)
- [公众号发布能力](https://developers.weixin.qq.com/doc/offiaccount/Publish/Get_publication_records.html)
- [Forest 官方文档](https://forest.dtflyx.com/)

## License

[MIT](LICENSE)
