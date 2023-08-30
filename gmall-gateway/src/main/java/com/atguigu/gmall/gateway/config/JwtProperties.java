package com.atguigu.gmall.gateway.config;

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
    private String cookieName;
    private String token;

    private PublicKey publicKey;

    @PostConstruct
    public  void  init(){
        try {
            this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        }catch (Exception e){
            log.error("读取公钥文件失败！错误信息：" + e.getMessage());
        }
    }


}
