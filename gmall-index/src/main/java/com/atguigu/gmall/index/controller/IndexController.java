package com.atguigu.gmall.index.controller;

import com.alibaba.csp.sentinel.adapter.reactor.ReactorSphU;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping("/**")
    public String toIndex(Model model){

        List<CategoryEntity> categories = this.indexService.queryLvl1Cateogries();
        model.addAttribute("categories", categories);

        // TODO: 加载广告

        return "index";
    }

    @GetMapping("index/cates/{pid}")
    @ResponseBody
    public ResponseVo<List<CategoryEntity>> queryLvl23CategoriesByPid(@PathVariable("pid")Long pid){
       List<CategoryEntity> categoryEntities =  this.indexService.queryLvl23CategoriesByPid(pid);
       return ResponseVo.ok(categoryEntities);
    }

    @GetMapping("index/test/lock")
    @ResponseBody
    public ResponseVo testLock(){
        this.indexService.testLock();
        return ResponseVo.ok();
    }

}