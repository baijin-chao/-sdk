package com.example.wechatsdk.domain.vo;

import lombok.Data;

/**
 * access_token 响应。
 */
@Data
public class AccessTokenVO {

    /**
     * 获取到的凭证
     */
    private String access_token;

    /**
     * 凭证有效时间，单位秒
     */
    private String expires_in;

    /**
     * 错误码
     */
    private String errcode;

    /**
     * 错误信息
     */
    private String errmsg;

}
