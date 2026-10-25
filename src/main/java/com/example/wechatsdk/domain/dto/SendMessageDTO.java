package com.example.wechatsdk.domain.dto;

import lombok.Data;

/**
 * 订阅消息请求参数。
 */
@Data
public class SendMessageDTO {

    /**
     * 当前小程序的 appId，不传时使用配置中的第一个
     */
    private String wxAppId;

    /**
     * 订阅消息模板 id
     */
    private String templateId;

    /**
     * 接收者（用户）的 openid
     */
    private String touser;

    /**
     * 模板数据，支持 JSON 对象或 JSON 字符串
     */
    private Object data;

    /**
     * 点击卡片后的跳转页面，仅限本小程序内的页面，可带参数
     */
    private String page;

}
