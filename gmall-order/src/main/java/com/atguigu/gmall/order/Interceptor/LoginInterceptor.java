package com.atguigu.gmall.order.Interceptor;


import com.atguigu.gmall.order.pojo.UserInfo;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Component
@Data
public class LoginInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        Long userId = Long.valueOf(request.getHeader("userId"));
        String username = request.getHeader("username");
        THREAD_LOCAL.set(new UserInfo(userId, username, null));
        // 这里只放行，因为未登录的话会被过滤器拦截
        return true;
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        System.out.println("完成方法，在视图渲染完成之后执行。。");
        // 此处必须调用该方法释放局部变量，因为使用tomcat线程池，请求结束线程并没有结束，如果不手动释放会导致内存泄漏，直至OOM宕机
        THREAD_LOCAL.remove();
    }
}