package com.atguigu.gmall.index.annotation;


import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {
    /*
     这里可以执行的缓存前缀，默认：gmall；
     */
    String prefix() default "gmall:";

    /*
     这里指定缓存的过期时间，默认:300min
     */
    int timeout() default 300;
    /*
    为了防止缓存雪崩，给缓存时间添加随机值，这里可以指定随机值的范围
     */
    int random() default 60;
    /*
    为了防止缓存击穿，给缓存添加分布式锁,这里可以指定分布式锁的前缀
     */
    String lock() default "gmall:lock:";
}
