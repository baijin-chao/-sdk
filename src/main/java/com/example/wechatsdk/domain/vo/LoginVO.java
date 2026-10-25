package com.example.wechatsdk.domain.vo;

import lombok.Data;

/**
 * 小程序登录响应。
 *
 * <p>出于安全考虑，不向前端返回 session_key。</p>
 */
@Data
public class LoginVO {

    /**
     * 用户唯一标识
     */
    private String openid;

    /**
     * 用户在开放平台的唯一标识符（小程序已绑定开放平台时返回）
     */
    private String unionid;

    /**
     * 错误码
     */
    private Integer errcode;

    /**
     * 错误信息
     */
    private String errmsg;

}
