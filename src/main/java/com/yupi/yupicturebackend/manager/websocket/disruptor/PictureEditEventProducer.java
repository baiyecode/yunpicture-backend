package com.yupi.yupicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;


/**
 * 图片编辑事件生产者
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发送图片编辑事件
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        //请求 RingBuffer 分配下一个可用的槽位。这个方法会返回一个序列号 (sequence number)，
        //指向 RingBuffer 数组中的一个位置。如果 RingBuffer 已满（即所有槽位都被生产者申请但尚未发布，且没有消费者处理掉足够多的槽位），
        //调用 next() 的线程会被阻塞，直到有空间可用。这是 Disruptor 协调生产者和消费者的关键机制，防止覆盖未处理的数据。
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy //当 Spring 容器关闭，即将销毁 PictureEditEventProducer Bean 之前，会自动调用被 @PreDestroy 标记的方法。这是一种优雅停机的实践。
    public void close() {
        pictureEditEventDisruptor.shutdown();
        //调用 Disruptor 实例的 shutdown 方法。这个方法会：
        //等待所有已发布到 RingBuffer 的事件被消费者处理完毕。
        //停止所有内部的工作线程。
        //释放相关资源。
        //防止新的事件被发布到 RingBuffer（后续的 publish 调用可能会失败或被忽略）。
        //这确保了在应用程序关闭时，Disruptor 能够安全、完整地处理完所有待处理的事件，避免数据丢失或线程异常。
    }
}
