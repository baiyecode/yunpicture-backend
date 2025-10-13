package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.util.List;

/**
 * ClassName: PictureTagCategory
 * Package: com.yupi.yupicturebackend.model.vo
 * Description:
 *
 * @Author 白夜
 * @Create 2025/10/13 11:51
 * @Version 1.0
 */

/**
 * 图片标签和分类列表视图
 */

@Data
public class PictureTagCategory {

    /**
     * 标签列表
     */
    private List<String> tagList;


    /**
     * 分类列表
     */
    private List<String> categoryList;



}
