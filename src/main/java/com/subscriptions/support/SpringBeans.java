package com.subscriptions.support;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Статический мост к Spring-контексту для хуков документов ({@code beforePost}, {@code rules()} и т.п.):
 * их создаёт фреймворк, а не Spring, поэтому внедрить зависимости через конструктор нельзя.
 * Использовать только там, где другого пути нет.
 */
@Component
public class SpringBeans implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static <T> T get(Class<T> type) {
        ApplicationContext current = context;
        if (current == null) {
            throw new IllegalStateException("Spring-контекст ещё не инициализирован");
        }
        return current.getBean(type);
    }
}
