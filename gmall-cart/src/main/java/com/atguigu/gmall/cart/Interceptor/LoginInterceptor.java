package com.atguigu.gmall.cart.Interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@Data
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

   // private UserInfo userInfo;
    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("前置方法，在Handler方法执行之前执行");

        //先获取cookie中token和userKey
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        //不管有没有登录，都需要
        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKey());
        if (StringUtils.isBlank(userKey)){
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request,response,jwtProperties.getUserKey(),userKey,this.jwtProperties.getExpire());
        }
        // 获取了登录信息
        Long userId = null;
        if (StringUtils.isNotBlank(token)){
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, jwtProperties.getPublicKey());
            userId = Long.valueOf(map.get("userId").toString());


        }

        //this.userInfo = new UserInfo(userId,userKey);
//        request.setAttribute("userId",userId);
//        request.setAttribute("userKey",userKey);
        THREAD_LOCAL.set(new UserInfo(userId,userKey));

        return  true;
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        System.out.println("后置方法，在Handler方法执行之后执行");

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //System.out.println("完成方法，在视图渲染完成之后执行");
        //此处必须调用该方法释放局部变量，因为使用了tomcat线程池，请求结束线程并没有结束，如果不手动释放会导致内存泄漏,直到oom死机
        THREAD_LOCAL.remove();
    }
}
