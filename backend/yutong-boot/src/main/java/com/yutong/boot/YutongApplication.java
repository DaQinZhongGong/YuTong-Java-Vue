package com.yutong.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * YuTong 单体启动器。boot 模式默认入口。
 * 设计来源: 03-总体架构设计、09-Java后端设计、07-项目源代码目录层级
 * yutong-boot 只做聚合启动，不承载业务实现。
 * GA2-34: @EnableScheduling 启用工单 SLA 定时扫描。
 */
@SpringBootApplication(scanBasePackages = "com.yutong")
@MapperScan("com.yutong.**.mapper")
@EnableScheduling
public class YutongApplication {

    public static void main(String[] args) {
        SpringApplication.run(YutongApplication.class, args);
    }
}
