package com.school.module.pdf.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PDF 成绩单配置（application.yml 的 app.pdf.* 段）
 *
 * @author School System
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.pdf")
public class PdfProperties {

    /**
     * 候选中文字体文件，按顺序尝试；.ttc 用「路径,索引」指定子字体
     */
    // ponytail: 默认值与 application.yml 保持一致，yml 缺省该段时仍可工作；全部不可读时回退 font-asian 内置 CJK 字体
    private List<String> fontPaths = new ArrayList<>(List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttc,0",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0"));

    /**
     * 兜底字体：iText font-asian 内置的 CJK 字体名（不嵌入 PDF，依赖阅读器自带字形）
     */
    private String fallbackFont = "STSong-Light";

    /**
     * 兜底字体编码
     */
    private String fallbackEncoding = "UniGB-UCS2-H";

}
