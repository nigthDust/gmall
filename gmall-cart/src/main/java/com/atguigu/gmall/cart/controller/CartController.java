package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.Interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;

@Controller
public class CartController {



    @Autowired
    private CartService cartService;


    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    @PostMapping("updateStatus")
    @ResponseBody
    public ResponseVo updateStatus(@RequestBody Cart cart){
        this.cartService.upddateStatus(cart);
        return ResponseVo.ok();
    }

    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }



    @GetMapping
    public String saveCart(Cart cart){
        this.cartService.saveCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId() + "&count=" + cart.getCount();
    }

    @GetMapping("addCart.html")
    public String queryCart(Cart cart, Model model){
        BigDecimal count = cart.getCount(); // 本次新增的数量
        //查询数据库中的购物车信息
        cart = this.cartService.queryCart(cart.getSkuId());
        cart.setCount(count);
        model.addAttribute("cart",cart);
        return "addCart";
    }

    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts =  this.cartService.queryCarts();
        model.addAttribute("carts",carts);
        return "cart";
    }













    @GetMapping("test")
    @ResponseBody
    public String test(HttpServletRequest request){
        System.out.println("这是handler方法....."+ LoginInterceptor.getUserInfo());
        return "hello cart";
    }
}
