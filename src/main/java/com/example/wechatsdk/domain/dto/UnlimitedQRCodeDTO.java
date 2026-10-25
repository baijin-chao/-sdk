package com.example.wechatsdk.domain.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

/**
 * 无限制小程序码请求参数。
 */
@Data
public class UnlimitedQRCodeDTO {

    /**
     * 场景值，最大 32 个可见字符
     */
    private String scene;

    /**
     * 扫码后跳转的页面
     */
    private String page;

    /**
     * 是否校验 page 是否存在
     */
    private Boolean checkPath;

    /**
     * 要打开的小程序版本：release / trial / develop
     */
    private String envVersion;

    /**
     * 二维码边长，单位 px
     */
    private Integer width;

    /**
     * 是否自动配置线条颜色
     */
    private Boolean autoColor;

    /**
     * 线条颜色，autoColor 为 false 时生效
     */
    private JSONObject lineColor;

    /**
     * 是否需要透明底色
     */
    private Boolean isHyaline;

}
