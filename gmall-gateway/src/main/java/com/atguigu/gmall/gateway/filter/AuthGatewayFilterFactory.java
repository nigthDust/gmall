package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

     @Autowired
     private JwtProperties properties;


    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
         return Arrays.asList("paths");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        return (exchange,chain) ->{
            //获取轻轻对象和响应对象
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            //1.判断当前请求路在不在拦截名单之中，不在则直接放行
            String curPath = request.getURI().getPath();  //当前请求的路径
            List<String> paths = config.paths; //拦截名单
            // 1）如果拦截名单为空，拦截所有路径
            // 2)如果当前路径以拦截名单之后的任意路径开头，则拦截
            // 拦截名单不为空，并且当前路径不以任意拦截名单之中的路径开头，说明 不在拦截名单之中，则直接放行
            if (!CollectionUtils.isEmpty(paths) && !paths.stream().anyMatch(path -> curPath.startsWith(path))){
                return chain.filter(exchange);
            }
            //2.获取token：同步cookie 异步-头信息
            String token = request.getHeaders().getFirst(this.properties.getToken());
            if (StringUtils.isBlank(token)){
                //如果头信息没有传递token，则从cookie中获取
                MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                if (!CollectionUtils.isEmpty(cookies) && cookies.containsKey(this.properties.getCookieName())){
                    HttpCookie cookie = cookies.getFirst(this.properties.getCookieName());
                    token = cookie.getValue();
                }
            }
            //3.判断token是否为空，如果为空说明没有登录则重定向到登录页面，请求结束
            if (StringUtils.isBlank(token)){
                //重定向到登录页面
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl="+ request.getURI());
                return response.setComplete();
            }
            try {
                //4.使用公钥解析token，如果出现异常则重定向到登录页面，请求结束
                Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());
                //5.验证ip地址：获取token载荷中登录用户的ip地址和当前请求中的ip地址是否一致，不一致重定向 ，请求结束
                String ip = map.get("ip").toString();
                String curIp = IpUtils.getIpAddressAtGateway(request); // 当前请求的ip地址
                if (!StringUtils.equals(ip,curIp)){
                    //重定向到登录页面
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl="+ request.getURI());
                    return response.setComplete();
                }

                //6.吧登录信息传递给后续服务
                request.mutate().header("userId",map.get("userId").toString()).header("username",map.get("username").toString()).build();
                exchange.mutate().request(request).build();
                // 7.放行
                return chain.filter(exchange);
            } catch (Exception e) {
                //重定向到登录页面
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl="+ request.getURI());
                return response.setComplete();
            }
        } ;
    }
    @Data
    public static  class PathConfig{
        private List<String>  paths;
    }
}
