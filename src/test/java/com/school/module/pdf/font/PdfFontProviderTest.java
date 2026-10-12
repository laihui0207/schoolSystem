package com.school.module.pdf.font;

import com.itextpdf.kernel.font.PdfFont;
import com.school.common.exception.BusinessException;
import com.school.module.pdf.config.PdfProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 字体回退：候选文件逐个尝试，全部失败回退 font-asian 内置 CJK 字体
 */
@DisplayName("PDF字体提供者测试")
class PdfFontProviderTest {

    @Test
    @DisplayName("应该回退到内置CJK字体_当所有字体路径都不存在")
    void shouldFallbackToBuiltInCjkFont_WhenNoFontPathExists() {
        // Given
        PdfProperties properties = new PdfProperties();
        properties.setFontPaths(List.of("/nonexistent/simhei.ttf", "/nonexistent/msyh.ttc,0"));
        PdfFontProvider provider = new PdfFontProvider(properties);

        // When
        PdfFont font = provider.createFont();

        // Then
        assertThat(font).isNotNull();
        assertThat(provider.fallbackUsed()).isTrue();
        assertThat(provider.resolvedSource()).isEqualTo("STSong-Light");
    }

    @Test
    @DisplayName("应该回退到内置CJK字体_当字体路径列表为空")
    void shouldFallbackToBuiltInCjkFont_WhenFontPathsEmpty() {
        // Given
        PdfProperties properties = new PdfProperties();
        properties.setFontPaths(List.of());
        PdfFontProvider provider = new PdfFontProvider(properties);

        // When
        PdfFont font = provider.createFont();

        // Then
        assertThat(font).isNotNull();
        assertThat(provider.fallbackUsed()).isTrue();
    }

    @Test
    @DisplayName("应该跳过损坏的字体文件并回退_当文件存在但不是字体")
    void shouldSkipCorruptFontFile_WhenFileIsNotAFont(@TempDir Path tempDir) throws IOException {
        // Given: 一个存在但内容不是 TrueType 的文件
        Path corrupt = tempDir.resolve("broken.ttf");
        Files.write(corrupt, "not a font".getBytes(StandardCharsets.UTF_8));
        PdfProperties properties = new PdfProperties();
        properties.setFontPaths(List.of(corrupt.toString()));
        PdfFontProvider provider = new PdfFontProvider(properties);

        // When
        PdfFont font = provider.createFont();

        // Then: 不抛异常，回退内置字体
        assertThat(font).isNotNull();
        assertThat(provider.fallbackUsed()).isTrue();
        assertThat(provider.resolvedSource()).isEqualTo("STSong-Light");
    }

    @Test
    @DisplayName("应该抛出业务异常_当兜底字体也不可用")
    void shouldThrowBusinessException_WhenFallbackFontUnavailable() {
        // Given
        PdfProperties properties = new PdfProperties();
        properties.setFontPaths(List.of());
        properties.setFallbackFont("NoSuchFont-Light");
        PdfFontProvider provider = new PdfFontProvider(properties);

        // When & Then
        assertThatThrownBy(provider::createFont)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 字体不可用");
    }

    @Test
    @DisplayName("应该每次返回新的字体实例_当重复调用")
    void shouldReturnFreshInstance_WhenCalledTwice() {
        // Given: PdfFont 与 PdfDocument 绑定，不能跨文档复用
        PdfProperties properties = new PdfProperties();
        properties.setFontPaths(List.of());
        PdfFontProvider provider = new PdfFontProvider(properties);

        // When
        PdfFont first = provider.createFont();
        PdfFont second = provider.createFont();

        // Then
        assertThat(first).isNotSameAs(second);
    }

}
