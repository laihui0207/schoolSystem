package com.school.module.pdf.font;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.school.common.exception.BusinessException;
import com.school.module.pdf.config.PdfProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PDF 中文字体提供者
 * <p>
 * 依次尝试 {@code app.pdf.font-paths} 里的字体文件（IDENTITY_H 嵌入），都不可用时
 * 回退 iText font-asian 内置的 {@code STSong-Light} + {@code UniGB-UCS2-H}。
 *
 * @author School System
 */
@Slf4j
@Component
public class PdfFontProvider {

    private final PdfProperties properties;

    /**
     * 上一次成功的字体来源（文件路径或兜底字体名）；null 表示尚未解析过
     */
    private volatile String resolvedSource;

    private volatile boolean fallbackUsed;

    public PdfFontProvider(PdfProperties properties) {
        this.properties = properties;
    }

    /**
     * 新建一个可排版中文的字体实例
     * <p>
     * ponytail: PdfFont 一旦被某个 PdfDocument 使用就与之绑定，不能跨文档复用，所以每次都新建；
     * 底层 FontProgram 由 iText 的 FontCache 缓存，重复创建不会重复读取字体文件。
     */
    public PdfFont createFont() {
        String known = resolvedSource;
        if (known != null && !fallbackUsed) {
            PdfFont cached = tryPath(known);
            if (cached != null) {
                return cached;
            }
        }
        List<String> paths = properties.getFontPaths() == null ? List.of() : properties.getFontPaths();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            PdfFont font = tryPath(path.trim());
            if (font != null) {
                remember(path.trim(), false);
                return font;
            }
        }
        return fallback();
    }

    /**
     * 当前实际使用的字体来源（文件路径或兜底字体名），尚未解析时为 null
     */
    public String resolvedSource() {
        return resolvedSource;
    }

    /**
     * 是否正在使用兜底内置字体
     */
    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    private PdfFont tryPath(String path) {
        try {
            // ponytail: 先把字体文件读进内存再交给 iText。按路径加载时 iText 会持有文件句柄，
            // Windows 下该文件在进程存活期间无法删除/替换（@TempDir 清理失败即此症状）；
            // 字体文件只有几 MB，一次性读入代价可忽略，且避免运行期字体文件被替换导致渲染错乱。
            String[] parts = path.split(",", 2);
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(parts[0].trim()));
            if (parts.length == 2) {
                // .ttc 子字体索引：iText 用 "<bytes>,<index>" 语法不可用，退回路径加载（句柄问题仅影响测试临时文件）
                return PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H);
            }
            return PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H);
        } catch (Exception e) {
            // ponytail: 文件不存在时 iText 抛 java.io.IOException，文件损坏/格式不支持时抛运行时 ITextException，
            // 统一按「该候选不可用、继续下一个」处理，不让一个坏路径拖垮整个 PDF 功能
            log.debug("PDF 字体不可用，跳过: {} ({})", path, e.getMessage());
            return null;
        }
    }

    private PdfFont fallback() {
        String fallbackFont = properties.getFallbackFont();
        try {
            PdfFont font = PdfFontFactory.createFont(fallbackFont, properties.getFallbackEncoding());
            if (!fallbackUsed) {
                log.warn("未找到可用的中文字体文件（app.pdf.font-paths），PDF 改用 iText 内置字体 {}，字形不嵌入、依赖阅读器支持 CJK",
                        fallbackFont);
            }
            remember(fallbackFont, true);
            return font;
        } catch (Exception e) {
            log.error("PDF 兜底字体 {} 也不可用: {}", fallbackFont, e.getMessage());
            throw new BusinessException("PDF 字体不可用，请检查 app.pdf.font-paths 配置");
        }
    }

    private void remember(String source, boolean fallback) {
        this.resolvedSource = source;
        this.fallbackUsed = fallback;
    }

}
