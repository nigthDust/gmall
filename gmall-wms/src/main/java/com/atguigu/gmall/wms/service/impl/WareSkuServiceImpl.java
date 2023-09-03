package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import exception.OrderException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "stock:lock:";
    private static final String KEY_PREFIX = "stock:info:";



    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuLockVo> checkLock(List<SkuLockVo> lockVos,String orderToken) {
        if (CollectionUtils.isEmpty(lockVos)){
            throw new OrderException("请选择要购买的商品！");
        }

        //遍历购物列表，验库存并锁库存
        lockVos.forEach(lockVo -> {
            this.checkAndLock(lockVo);
        });

        //只要有一个商品的库存锁定失败。所有锁定成功的商品都要解锁库存
        if (lockVos.stream().anyMatch(lockVo -> !lockVo.getLock())){
            //获取锁定成功的商品列表，遍历解释库存
            lockVos.stream().filter(SkuLockVo::getLock).forEach(lockVo->{
                this.wareSkuMapper.unlock(lockVo.getWareSkuId(),lockVo.getCount());
            });
            //返回锁定信息
            return lockVos;
        }

        //为了方便将来不支付之后解锁库存或者支付之后减库存，需要缓存锁定信息到redis orderToken：List<skuLockVo>
        this.redisTemplate.opsForValue().set(KEY_PREFIX+orderToken, JSON.toJSONString(lockVos),26, TimeUnit.HOURS);
        //如果都锁定成功，返回null
        return null;
    }

    private void checkAndLock(SkuLockVo lockVo) {

        RLock lock = this.redissonClient.getLock(LOCK_PREFIX + lockVo.getSkuId());
        lock.lock();

        try {
            //验库存
            List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.check(lockVo.getSkuId(), lockVo.getCount());
            if (CollectionUtils.isEmpty(wareSkuEntities)){
                lockVo.setLock(false);
                return;
            }

            //锁库存：通过大数据接口基于成本最低觉得那个仓库发货，这里就取第一个
            Long wareSkuId = wareSkuEntities.get(0).getId();
            if (this.wareSkuMapper.lock(wareSkuId,lockVo.getCount())==1){
                lockVo.setLock(true);
                lockVo.setWareSkuId(wareSkuId);
            }else {
                lockVo.setLock(false);
            }
        }finally {
            lock.unlock();
        }


    }

}