package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.api.GmallUmsApi;
import com.atguigu.gmall.ums.entity.UserEntity;
import exception.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Service
public class AuthService {
    @Autowired
    private GmallUmsApi gmallUmsApi;

    @Autowired
    private JwtProperties jwtProperties;

    public void login(String loginName, String password, HttpServletRequest request, HttpServletResponse response) {
        // 1.调用ums的远程接口查询用户
        ResponseVo<UserEntity> userEntityResponseVo = this.gmallUmsApi.queryUser(loginName, password);
        UserEntity userEntity = userEntityResponseVo.getData();
        // 2.如果用户为空，这抛出异常，提示用户名或者密码错误
        if (userEntity ==null ){
            throw  new AuthException("登录名或者密码错误！");
        }
        // 3.组装载荷
        Map<String, Object> map = new HashMap<>();
        map.put("userId",userEntity.getId());
        map.put("username",userEntity.getUsername());
        //为了防止盗用，加入登录用户的ip
        map.put("ip", IpUtils.getIpAddressAtService(request));
        try {
            //4.生成jwt类型的token
            String token = JwtUtils.generateToken(map, this.jwtProperties.getPrivateKey(), this.jwtProperties.getExpire());
            //5.把token放入cookie
            CookieUtils.setCookie(request,response,this.jwtProperties.getCookieName(),token,this.jwtProperties.getExpire() * 60);
            //6.为了方便展示登录状态，把昵称放入cookie
            CookieUtils.setCookie(request,response,this.jwtProperties.getUnick(),userEntity.getNickname(),this.jwtProperties.getExpire()*60);

        } catch (Exception e) {
            throw  new AuthException("登录失败，请联系管理员！");
        }


    }
}
