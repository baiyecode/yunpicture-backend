package com.yupi.yupicturebackend.api.imagesearch.sub;

import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GetImageFirstUrlApi {

    /**
     * 获取图片列表页面地址
     *
     * @param url
     * @return
     */
    public static String getImageFirstUrl(String url) {
        try {
            // 使用 Jsoup 获取 HTML 内容
            Document document = Jsoup.connect(url)
                    .timeout(5000)
                    .get();

            // 获取所有 <script> 标签
            Elements scriptElements = document.getElementsByTag("script");

            // 遍历找到包含 `firstUrl` 的脚本内容
            for (Element script : scriptElements) {
                String scriptContent = script.html();//获取script标签内的JavaScript代码内容
                if (scriptContent.contains("\"firstUrl\"")) { //查找包含"firstUrl"关键字
                    // 正则表达式提取 firstUrl 的值
                    //  正则表达式 "firstUrl"\s*:\s*"(.*?)"
                    // \"firstUrl\" :匹配字符串 "firstUrl"
                    //  \s* :匹配零个或多个空白字符（空格、制表符、换行符等）
                    //  \" :匹配双引号
                    //  : 匹配字面量冒号:
                    //  ()：定义一个捕获组，可以将匹配到的内容提取出来。
                    //  (.*?) :. 表示匹配任意字符，* 表示匹配零次或多次，? 表示非贪婪（或最小）匹配。
                    //非贪婪：.*? 会尽可能少地匹配字符，直到遇到第一个 " 为止。
                    // 这很重要，因为如果URL本身包含引号或逗号，贪婪模式 .* 会匹配到错误的位置。
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(scriptContent);//创建一个匹配器，用于匹配正则表达式
                    if (matcher.find()) { //检查是否在JavaScript代码中找到了符合 "firstUrl":"<URL>" 格式的部分。
                        //获取第1个捕获组（即括号 (.*?) 内的内容）匹配到的字符串。
                        //group(0) 是整个匹配的字符串，group(1) 是第一个括号内的匹配内容，group(2) 是第二个括号内的内容（如果有的话）。
                        String firstUrl = matcher.group(1);//获取匹配到的字符串
                        // JSON中的斜杠通常被转义为\/,将转义的斜杠\/替换为正常斜杠/
                        firstUrl = firstUrl.replace("\\/", "/");
                        return firstUrl;
                    }
                }
            }

            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到 url");
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 请求目标 URL
        String url = "https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData[isLogoShow]=1&f=all&isLogoShow=1&session_id=8354737871005129755&sign=126d7e3747564cdb37e1e01761222616&tpl_from=pc";
        String imageFirstUrl = getImageFirstUrl(url);
        System.out.println("搜索成功，结果 URL：" + imageFirstUrl);
    }
}
