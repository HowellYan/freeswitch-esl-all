package link.thingscloud.freeswitch.esl.spring.boot.starter.example.handler;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import link.thingscloud.freeswitch.esl.constant.EventNames;
import link.thingscloud.freeswitch.esl.helper.EslHelper;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 *
 * @author th158
 */
@Slf4j
@EslEventName(EventNames.CHANNEL_CREATE)
@Component
public class ChannelCreateHandler implements EslEventHandler {

    @NacosInjected
    private NamingService namingService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(String address, EslEvent event) {
        try {
            // 根据服务名从注册中心获取一个健康的服务实例
            Instance instance = namingService.selectOneHealthyInstance("fs-esl");
            log.info(" ip [{}] port [{}]", instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            e.printStackTrace();
        }
        log.info("address[{}] EslEvent[{}]", address, EslHelper.formatEslEvent(event));
    }


}
