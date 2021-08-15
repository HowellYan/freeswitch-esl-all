package link.thingscloud.freeswitch.esl.spring.boot.starter.example.handler;

import link.thingscloud.opensips.event.handler.Context;
import link.thingscloud.opensips.spring.boot.starter.handler.ClientConnectHandler;
import link.thingscloud.opensips.spring.boot.starter.handler.ClientEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * @author th158
 */
@Slf4j
@Configuration
public class OpensipConnectEventHandler implements ClientEventHandler, ClientConnectHandler {
    @Override
    public void onConnect(Context context, Object msg) {
        log.info("onConnect");
    }

    @Override
    public void handler(Context context, Object msg) {
        log.info("handler");
    }
}
