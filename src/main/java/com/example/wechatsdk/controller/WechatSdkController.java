package com.example.wechatsdk.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dtflys.forest.Forest;
import com.example.wechatsdk.domain.dto.SendMessageDTO;
import com.example.wechatsdk.domain.dto.UnlimitedQRCodeDTO;
import com.example.wechatsdk.domain.vo.AccessTokenVO;
import com.example.wechatsdk.domain.vo.LoginVO;
import com.example.wechatsdk.util.HutoolCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信开放能力统一入口：把常用的小程序 / 公众号接口收敛成自有 REST API。
 *
 * <p>设计目标：业务方只依赖本服务，不再各自对接微信协议、各自维护 access_token。</p>
 */
@RestController
public class WechatSdkController {

    private static final Logger log = LoggerFactory.getLogger(WechatSdkController.class);

    /** appId -> secret，支持多小程序；未指定 appId 时默认取第一个 */
    private static final Map<String, String> APP_MAP = new LinkedHashMap<>();

    private static final String ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";

    /** access_token 提前 600 秒过期，避免临界时间点失效 */
    private static final int EXPIRE_AHEAD_SECONDS = 600;

    /** 缓存时间下限，防止微信返回异常值时立刻过期 */
    private static final int MIN_CACHE_SECONDS = 60;

    private static final String MINI_TOKEN_CACHE_KEY = "accessToken:";
    private static final String GZH_TOKEN_CACHE_KEY = "gzh:accessToken";

    @Value("${wechat.appid:}")
    private String appId;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.gzhappid:}")
    private String gzhAppId;

    @Value("${wechat.gzhsecret:}")
    private String gzhSecret;

    private final RestTemplate restTemplate;

    public WechatSdkController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        APP_MAP.clear();
        if (StrUtil.isBlank(appId) || StrUtil.isBlank(secret)) {
            log.warn("未配置微信小程序 appid/secret，请设置环境变量 WECHAT_MINI_APPID / WECHAT_MINI_SECRET");
            return;
        }
        String[] appIds = appId.split(StrUtil.COMMA);
        String[] secrets = secret.split(StrUtil.COMMA);
        int size = Math.min(appIds.length, secrets.length);
        if (appIds.length != secrets.length) {
            log.warn("wechat.appid 与 wechat.secret 数量不一致（{} vs {}），仅加载前 {} 组",
                    appIds.length, secrets.length, size);
        }
        for (int i = 0; i < size; i++) {
            APP_MAP.put(appIds[i].trim(), secrets[i].trim());
        }
        log.info("已加载 {} 个小程序配置", APP_MAP.size());
    }

    /**
     * 小程序登录：用 wx.login 拿到的 code 换取 openid / unionid。
     */
    @GetMapping("/login")
    public LoginVO login(@RequestParam String code, @RequestParam(required = false) String wxAppId) {
        String appid = resolveAppId(wxAppId);
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appid
                + "&secret=" + secretOf(appid)
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        return JSONObject.parseObject(Forest.get(url).executeAsString(), LoginVO.class);
    }

    /**
     * 发送订阅消息。
     */
    @PostMapping("/sendMessage")
    public Map<String, Object> sendMessage(@RequestBody SendMessageDTO messageDTO) {
        String appid = resolveAppId(messageDTO.getWxAppId());
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + getAccessToken(appid);

        Map<String, Object> result = Forest.post(url)
                .contentTypeJson()
                .addBody("touser", messageDTO.getTouser())
                .addBody("template_id", messageDTO.getTemplateId())
                .addBody("data", parseData(messageDTO.getData()))
                .addBody("page", messageDTO.getPage())
                .executeAsMap();

        log.info("用户 {} 订阅消息发送结果：{}", messageDTO.getTouser(), result);

        Map<String, Object> resultMap = new HashMap<>(2);
        resultMap.put("errcode", result.get("errcode"));
        resultMap.put("errmsg", result.get("errmsg"));
        return resultMap;
    }

    /**
     * 生成不限制数量的小程序码，直接把微信返回的图片字节流透传给调用方。
     */
    @PostMapping("/getUnlimitedQRCode")
    public ResponseEntity<byte[]> getUnlimitedQRCode(@RequestBody UnlimitedQRCodeDTO qrCodeDTO) {
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + getAccessToken(null);

        Map<String, Object> body = new HashMap<>(8);
        body.put("scene", qrCodeDTO.getScene());
        body.put("page", qrCodeDTO.getPage());
        body.put("check_path", qrCodeDTO.getCheckPath());
        body.put("env_version", qrCodeDTO.getEnvVersion());
        body.put("width", qrCodeDTO.getWidth());
        body.put("auto_color", qrCodeDTO.getAutoColor());
        body.put("line_color", qrCodeDTO.getLineColor());
        body.put("is_hyaline", qrCodeDTO.getIsHyaline());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
    }

    /**
     * 获取微信用户手机号。
     */
    @GetMapping("/getPhoneNumber")
    public Map<String, Object> getPhoneNumber(@RequestParam String code,
                                              @RequestParam(required = false) String wxAppId) {
        String appid = resolveAppId(wxAppId);
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + getAccessToken(appid);
        Map<String, Object> result = Forest.post(url).contentTypeJson().addBody("code", code).executeAsMap();

        Map<String, Object> resultMap = new HashMap<>(4);
        Object errcode = result.get("errcode");
        resultMap.put("errcode", errcode);
        resultMap.put("errmsg", result.get("errmsg"));

        if (errcode != null && "0".equals(errcode.toString())) {
            Object phoneInfo = result.get("phone_info");
            if (phoneInfo != null) {
                JSONObject info = JSONObject.parseObject(JSON.toJSONString(phoneInfo));
                resultMap.put("phoneNumber", info.get("purePhoneNumber"));
            }
        }
        return resultMap;
    }

    /**
     * 获取公众号已发布文章列表。
     */
    @GetMapping("/getFreePublishList")
    public ResponseEntity<Map<String, Object>> getFreePublishList(
            @RequestParam int offset,
            @RequestParam int count,
            @RequestParam(value = "no_content", required = false, defaultValue = "0") int noContent) {

        Map<String, Object> resultMap = new HashMap<>();

        if (offset < 0) {
            resultMap.put("errcode", -1);
            resultMap.put("errmsg", "offset 不能小于 0");
            return ResponseEntity.badRequest().body(resultMap);
        }
        if (count < 1 || count > 20) {
            resultMap.put("errcode", -1);
            resultMap.put("errmsg", "count 必须在 1 到 20 之间");
            return ResponseEntity.badRequest().body(resultMap);
        }

        String accessToken;
        try {
            accessToken = getGzhAccessToken();
        } catch (RuntimeException e) {
            resultMap.put("errcode", -1);
            resultMap.put("errmsg", "获取公众号 access_token 失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        String url = "https://api.weixin.qq.com/cgi-bin/freepublish/batchget?access_token=" + accessToken;
        Map<String, Object> body = new HashMap<>(4);
        body.put("offset", offset);
        body.put("count", count);
        body.put("no_content", noContent);

        Map<String, Object> response = Forest.post(url).contentTypeJson().addBody(body).executeAsMap();
        return ResponseEntity.ok(response);
    }

    /**
     * 获取公众号 access_token，仅用于调试，生产环境不建议直接暴露。
     */
    @GetMapping("/getGzhToken")
    public ResponseEntity<String> getGzhToken() {
        return ResponseEntity.ok(getGzhAccessToken());
    }

    private String getAccessToken(String wxAppId) {
        String appid = resolveAppId(wxAppId);
        String cacheKey = MINI_TOKEN_CACHE_KEY + appid;

        String token = HutoolCache.get(cacheKey);
        if (StrUtil.isNotBlank(token)) {
            return token;
        }

        synchronized (this) {
            // 双重检查，避免并发时重复请求微信接口
            token = HutoolCache.get(cacheKey);
            if (StrUtil.isNotBlank(token)) {
                return token;
            }
            AccessTokenVO tokenVO = fetchToken(appid, secretOf(appid));
            String accessToken = tokenVO.getAccess_token();
            if (StrUtil.isBlank(accessToken)) {
                throw new IllegalStateException("获取 access_token 失败: "
                        + tokenVO.getErrcode() + " " + tokenVO.getErrmsg());
            }
            HutoolCache.set(cacheKey, accessToken, cacheSeconds(tokenVO.getExpires_in()));
            return accessToken;
        }
    }

    private String getGzhAccessToken() {
        String token = HutoolCache.get(GZH_TOKEN_CACHE_KEY);
        if (StrUtil.isNotBlank(token)) {
            return token;
        }

        synchronized (this) {
            token = HutoolCache.get(GZH_TOKEN_CACHE_KEY);
            if (StrUtil.isNotBlank(token)) {
                return token;
            }
            AccessTokenVO tokenVO = fetchToken(gzhAppId, gzhSecret);
            if (StrUtil.isBlank(tokenVO.getAccess_token())) {
                throw new IllegalStateException("获取公众号 access_token 失败: "
                        + tokenVO.getErrcode() + " " + tokenVO.getErrmsg());
            }
            HutoolCache.set(GZH_TOKEN_CACHE_KEY, tokenVO.getAccess_token(),
                    cacheSeconds(tokenVO.getExpires_in()));
            return tokenVO.getAccess_token();
        }
    }

    private AccessTokenVO fetchToken(String appid, String appSecret) {
        String url = String.format(ACCESS_TOKEN_URL, appid, appSecret);
        String response = Forest.get(url).executeAsString();
        return JSONObject.parseObject(response, AccessTokenVO.class);
    }

    /**
     * 微信返回的 expires_in 是字符串，解析失败时按默认 7200 秒处理。
     */
    private int cacheSeconds(String expiresIn) {
        int expires = 7200;
        try {
            expires = Integer.parseInt(expiresIn);
        } catch (NumberFormatException ignored) {
            // 保持默认值即可
        }
        return Math.max(expires - EXPIRE_AHEAD_SECONDS, MIN_CACHE_SECONDS);
    }

    private String resolveAppId(String wxAppId) {
        if (StrUtil.isNotBlank(wxAppId)) {
            return wxAppId;
        }
        return APP_MAP.isEmpty() ? "" : APP_MAP.keySet().iterator().next();
    }

    private String secretOf(String appid) {
        return APP_MAP.getOrDefault(appid, "");
    }

    /**
     * 订阅消息的 data 既可能是 JSON 字符串，也可能被反序列化成 Map，
     * 这里统一转成微信要求的 JSON 对象再提交。
     */
    private Object parseData(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String) {
            return JSONObject.parseObject((String) data);
        }
        return JSON.parseObject(JSON.toJSONString(data));
    }

}
