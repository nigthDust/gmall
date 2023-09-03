package com.atguigu.gmall.scheduled.mapper;


import com.atguigu.gmall.scheduled.pojo.Cart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}