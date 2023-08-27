package com.atguigu.gmall.index.utils;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class DistributedLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Boolean tryLock(String lockName, String uuid, Integer expire){
        String script = "if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                "then " +
                "   redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                "   redis.call('expire', KEYS[1], ARGV[2]) " +
                "   return 1 " +
                "else " +
                "   return 0 " +
                "end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expire.toString());
        if (!flag){
            try {
                Thread.sleep(40);
                this.tryLock(lockName, uuid, expire);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // 获取锁成功之后，自动续期
        this.renewExpiration(lockName, uuid, expire);
        return true;
    }

    public void unlock(String lockName, String uuid){
        String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 " +
                "then " +
                "   return nil " +
                "elseif redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0 " +
                "then " +
                "   return redis.call('del', KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";
        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(lockName), uuid);
        if (flag == null){
            throw new IllegalMonitorStateException("你要释放的锁不属于你！");
        }
    }

    private void renewExpiration(String lockName, String uuid, Integer expire){
        String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 " +
                "then " +
                "   return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else " +
                "   return 0 " +
                "end";
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Boolean flag = redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expire.toString());
                if (flag){
                    // 如果自动续期成功，说明可能需要下一次的自动续期
                    renewExpiration(lockName, uuid, expire);
                }
            }
        }, expire * 1000 / 3);
    }

    public static void main(String[] args) {

        BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 20, 0.3);
        bloomFilter.put("1");
        bloomFilter.put("2");
        bloomFilter.put("3");
        bloomFilter.put("4");
        bloomFilter.put("5");
        bloomFilter.put("6");
        bloomFilter.put("7");
        System.out.println(bloomFilter.mightContain("1"));
        System.out.println(bloomFilter.mightContain("3"));
        System.out.println(bloomFilter.mightContain("5"));
        System.out.println(bloomFilter.mightContain("7"));
        System.out.println(bloomFilter.mightContain("8"));
        System.out.println(bloomFilter.mightContain("9"));
        System.out.println(bloomFilter.mightContain("10"));
        System.out.println(bloomFilter.mightContain("11"));
        System.out.println(bloomFilter.mightContain("12"));
        System.out.println(bloomFilter.mightContain("13"));
        System.out.println(bloomFilter.mightContain("14"));
        System.out.println(bloomFilter.mightContain("15"));
        System.out.println(bloomFilter.mightContain("16"));
        System.out.println(bloomFilter.mightContain("17"));


//        System.out.println("定时任务的初始时间：" + System.currentTimeMillis());
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.out.println("定时任务的执行时间：" + System.currentTimeMillis());
//            }
//        }, 5000, 10000);

//        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
//        System.out.println("定时任务的初始时间：" + System.currentTimeMillis());
//        scheduledExecutorService.scheduleAtFixedRate(() -> {
//            System.out.println("定时任务的执行时间：" + System.currentTimeMillis());
//        }, 5, 10, TimeUnit.SECONDS);
    }
}