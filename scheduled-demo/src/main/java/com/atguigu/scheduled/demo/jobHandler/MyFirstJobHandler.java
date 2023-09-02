package com.atguigu.scheduled.demo.jobHandler;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.stereotype.Component;

@Component
public class MyFirstJobHandler {
    /**
     * 1.方法必须返回ReturnT<String>
     * 2.方法必须有一个String类型的参数
     * 3.在方法上添加@xxlJob（"全局唯一标识"）
     */

    @XxlJob("myFirstJobHandler")
    public ReturnT<String> myFirstJobHandler(String param){
        System.out.println("任务执行了，执行时间：" + System.currentTimeMillis());

        XxlJobLogger.log("this is my first job!" + param);

        return  ReturnT.SUCCESS;
    }
}
