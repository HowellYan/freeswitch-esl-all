package link.thingscloud.freeswitch.esl.spring.boot.starter.example.handler;

import link.thingscloud.freeswitch.esl.OutboundEventListener;
import link.thingscloud.freeswitch.esl.outbound.handler.Context;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.OutBoundConnectHandler;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.OutBoundEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class OutboundConnectPreprocessEslEventHandler implements OutBoundEventHandler, OutBoundConnectHandler {

    @Override
    public void onConnect(Context context, EslEvent eslEvent) {
        log.info("{}", eslEvent);
    }

    @Override
    public void handler(Context context, EslEvent eslEvent) {
        log.info("{}", eslEvent);
    }
}
