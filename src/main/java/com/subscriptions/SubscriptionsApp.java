package com.subscriptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Сервис подписок на onno-framework. Схема БД, REST и UI генерируются из типизированной метамодели;
 * вручную таблицы, DTO и CRUD-контроллеры не пишутся.
 */
@SpringBootApplication
public class SubscriptionsApp {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionsApp.class, args);
    }
}
