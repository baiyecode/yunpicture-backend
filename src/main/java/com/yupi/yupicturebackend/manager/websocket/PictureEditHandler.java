package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.yupicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassName: PictureEditHandler
 * Package: com.yupi.yupicturebackend.manager.websocket
 * Description:
 *
 * @Author 白夜
 * @Create 2025/11/3 15:35
 * @Version 1.0
 */

/**
 * 图片编辑 WebSocket 处理器
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    //注意，由于可能同时有多个 WebSocket 客户端建立连接和发送消息，
    //集合要使用并发包(JUC)中的concurrentHashMap来保证线程安全。

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();
    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();



    /**
     * 实现连接建立成功后执行的方法，保存会话到集合中，并且给其他会话发送消息
     * @param session 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        //将 pictureId 作为键，ConcurrentHashMap.newKeySet()创建的一个新的线程安全 Set 作为值放入 pictureSessions Map 中。
        //关键在于 putIfAbsent 只有在 pictureId 这个键不存在时才会执行 put 操作。
        //如果 pictureId 对应的 Set 已经存在（说明已经有其他用户连接到这张图片了），这个操作就不会改变 Map 的内容。
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);//将当前新建立的 session 添加到这个 Set 中。

        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给同一张图片的用户,不是操作，不需要排除自己
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 处理接收到的客户端消息，根据消息类型调用对应的处理方法
     * @param session 当前会话
     * @param message 接收到的文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        // 将消息解析为 PictureEditMessage
        //message.getPayload(): 获取 TextMessage 中的原始文本内容（JSON 字符串）。
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");

        // 根据消息类型处理消息（生产消息到 Disruptor 环形队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }


    /**
     * 处理图片进入编辑的消息
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session 当前会话
     * @param user 当前用户
     * @param pictureId 图片 ID
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            // 构造响应
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给同一张图片的用户,不是操作，不需要排除自己
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
        // 如果已经有用户在编辑了，那么就将该用户广播给请求进行编辑的用户
        // 通知其它用户
        //PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        //// 已经有用户在编辑，获取当前编辑用户ID
        //Long editingUserId = pictureEditingUsers.get(pictureId);
        //User editingUser = userService.getById(editingUserId);
        //String message = String.format("用户 %s 正在编辑", editingUser.getUserName());
        //pictureEditResponseMessage.setMessage(message);
        //pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        //pictureEditResponseMessage.setUser(userService.getUserVO(editingUser));
        //// 单独发送消息给当前请求编辑的session
        //// 创建 ObjectMapper, 用于将 Java 对象转换为 JSON
        //ObjectMapper objectMapper = new ObjectMapper();
        //String str = objectMapper.writeValueAsString(pictureEditResponseMessage);
        //TextMessage textMessage = new TextMessage(str);
        //if (session.isOpen()) {
        //    session.sendMessage(textMessage);
        //}
    }

    /**
     * 处理编辑操作
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session 当前会话
     * @param user 当前用户
     * @param pictureId 图片 ID
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        //当前编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.error("无效的操作");
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 退出编辑
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session 当前会话
     * @param user 当前用户
     * @param pictureId 图片 ID
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }


    /**
     * WebSocket 连接关闭时，需要移除当前用户的编辑状态、并且从集合中删除当前会话
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 获取当前会话的属性
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }



    /**
     * 广播图片编辑信息给所有连接的用户（支持排除掉某个 Session）
     *
     * @param pictureId 图片 ID
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @param excludeSession 排除的会话
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper, 用于将 Java 对象转换为 JSON
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();//创建一个自定义的 Jackson 模块。
            //当 ObjectMapper 序列化 Long 类型的字段时，会将其转换为字符串（String）而不是数字（Number）。
            module.addSerializer(Long.class, ToStringSerializer.instance);
            // 当 ObjectMapper 序列化 long 基本类型的字段时，会将其转换为字符串（String）而不是数字（Number）。
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }


    /**
     * 广播图片编辑信息给所有连接的用户
     * @param pictureId
     * @param pictureEditResponseMessage
     * @throws Exception
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }


}
