package com.spzx.aichat;

import com.spzx.common.security.annotation.EnableCustomConfig;
import com.spzx.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@EnableCustomConfig
@EnableRyFeignClients // 重点！加上这个，你的 Feign 才能远程调用商品服务
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SpzxAichatApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpzxAichatApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  AI聊天模块启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }
}