package com.hmdp.config;

import com.hmdp.utils.RabbitConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean("seckill_queue")
    public Queue queue(){
        return QueueBuilder.durable(RabbitConstant.SECKILL_QUEUE)
                //.ttl(RabbitConstant.SECKILL_MESSAGE_TTL) //设置这个队列中的消息过期时间为30分钟
                //.deadLetterExchange(RabbitConstant.SECKILL_DEAD_LETTER_EXCHANGE)
                //.deadLetterRoutingKey(RabbitConstant.SECKILL_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean("seckill_dead_letter_queue")
    public Queue deadLetterQueue(){
        return QueueBuilder.durable(RabbitConstant.SECKILL_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean("seckill_exchange")
    public Exchange exchange(){
        return ExchangeBuilder.directExchange(RabbitConstant.SECKILL_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean("seckill_dead_letter_exchange")
    public Exchange deadLetterExchange(){
        return ExchangeBuilder.directExchange(RabbitConstant.SECKILL_DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Binding binding(@Qualifier("seckill_queue")Queue queue,
                                        @Qualifier("seckill_exchange")Exchange exchange){
        return BindingBuilder.bind(queue).to(exchange)
                .with(RabbitConstant.SECKILL_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Binding deadBinding(@Qualifier("seckill_dead_letter_queue")Queue queue,
                                        @Qualifier("seckill_dead_letter_exchange")Exchange exchange){
        return BindingBuilder.bind(queue).to(exchange)
                .with(RabbitConstant.SECKILL_DEAD_LETTER_ROUTING_KEY)
                .noargs();
    }


}
