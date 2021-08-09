package link.thingscloud.freeswitch.esl.spring.boot.starter.example.handler;

import link.thingscloud.freeswitch.esl.constant.EventNames;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@EslEventName(EventNames.CHANNEL_CREATE)
@Component
public class ChannelCreateHandler implements EslEventHandler {
    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(String address, EslEvent event) {
        log.info("HeartbeatEslEventHandler handle address[{}] EslEvent[{}].", address, event);
    }
}
