package com.atguigu.gmall.auth.config;

import com.atguigu.gmall.common.utils.RsaUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;

@ConfigurationProperties(prefix = "jwt")
@Data
@Slf4j
public class JwtProperties {
    private String pubKeyPath;
    private String priKeyPath;
    private String secret;
    private Integer expire;
    private String cookieName;
    private String unick;

    private PublicKey publicKey;
    private PrivateKey privateKey;

    @PostConstruct
    public  void  init(){
        try {
            File pubFile = new File(pubKeyPath);
            File priFile = new File(priKeyPath);
            //判断公私钥文件是否存在，如果有一个不存在都要重新生成
            if (!pubFile.exists() || !priFile.exists()){
                RsaUtils.generateKey(pubKeyPath,priKeyPath,secret);
            }
            this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
            this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
        }catch (Exception e){
            log.error("生成公私钥失败！错误信息：" + e.getMessage());
        }
    }


}
