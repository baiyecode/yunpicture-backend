package com.yupi.yupicturebackend.config;

import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.Filter;
import java.io.IOException;

/**
 * 请求包装过滤器
 *
 * @author pine
 */
@Order(1) //指定了过滤器的执行优先级。数字越小，优先级越高，执行顺序越靠前。
@Component
public class HttpRequestWrapperFilter implements Filter {

    //它检查每个请求，如果请求是 application/json 类型，它就使用 RequestWrapper 将原始请求包装起来，
    //然后将包装后的请求传递给后续的过滤器链。这样，后续需要读取JSON请求体的组件（如进行空间权限校验的逻辑）就可以放心地读取，
    //而不必担心流被消费掉的问题。对于非JSON请求，则不进行任何包装操作。
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest servletRequest = (HttpServletRequest) request;
            String contentType = servletRequest.getHeader(Header.CONTENT_TYPE.getValue());
            if (ContentType.JSON.getValue().equals(contentType)) {
                // 可以再细粒度一些，只有需要进行空间权限校验的接口才需要包一层
                chain.doFilter(new RequestWrapper(servletRequest), response);
            } else {
                chain.doFilter(request, response);
            }
        }
    }

}
