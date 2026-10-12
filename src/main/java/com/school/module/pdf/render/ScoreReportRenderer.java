package com.school.module.pdf.render;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.school.common.exception.BusinessException;
import com.school.module.pdf.dto.ScoreReportData;
import com.school.module.pdf.font.PdfFontProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 成绩单 PDF 排版（iText 7）：每个 {@link ScoreReportData} 一页
 *
 * @author School System
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreReportRenderer {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final float TITLE_SIZE = 18f;
    private static final float SUBTITLE_SIZE = 14f;
    private static final float BODY_SIZE = 10.5f;
    private static final float FOOTER_SIZE = 9f;
    private static final float[] SUBJECT_COLUMNS = {18f, 10f, 10f, 10f, 10f, 10f, 16f, 16f};
    private static final String DASH = "-";

    private final PdfFontProvider fontProvider;

    /**
     * 把若干份成绩单渲染进一个 PDF（每份一页）并写入输出流
     */
    public void render(List<ScoreReportData> reports, OutputStream out) {
        if (reports == null || reports.isEmpty()) {
            throw new BusinessException("没有可生成的成绩单");
        }
        PdfFont font = fontProvider.createFont();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out));
             Document document = new Document(pdf, PageSize.A4)) {
            pdf.getDocumentInfo().setTitle(reports.get(0).getExamName() + " 成绩单").setAuthor("School System");
            document.setMargins(40, 40, 40, 40);
            // ponytail: 字体设在 Document 上由所有元素继承，不必逐个 Paragraph/Cell 设置
            document.setFont(font).setFontSize(BODY_SIZE);
            for (int i = 0; i < reports.size(); i++) {
                if (i > 0) {
                    document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                }
                writeReport(document, reports.get(i));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("成绩单 PDF 渲染失败", e);
            throw new BusinessException("生成PDF失败: " + e.getMessage());
        }
    }

    private void writeReport(Document document, ScoreReportData data) {
        document.add(new Paragraph(blank(data.getSchoolName()))
                .setFontSize(TITLE_SIZE).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));
        document.add(new Paragraph(blank(data.getExamName()) + " 成绩单")
                .setFontSize(SUBTITLE_SIZE).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));
        document.add(new Paragraph(headerLine(data))
                .setFontColor(ColorConstants.DARK_GRAY).setTextAlignment(TextAlignment.CENTER).setMarginBottom(12));

        document.add(studentInfoTable(data));
        document.add(new Paragraph("各科成绩").setBold().setMarginTop(12).setMarginBottom(4));
        document.add(subjectTable(data));
        document.add(new Paragraph("总分与名次").setBold().setMarginTop(12).setMarginBottom(4));
        document.add(totalTable(data));

        String generated = data.getGeneratedAt() == null ? "" : TIME_FORMAT.format(data.getGeneratedAt());
        document.add(new Paragraph("生成时间：" + generated)
                .setFontSize(FOOTER_SIZE).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.RIGHT).setMarginTop(16));
    }

    private static String headerLine(ScoreReportData data) {
        StringBuilder sb = new StringBuilder();
        if (!blank(data.getTermName()).isEmpty()) {
            sb.append("学期：").append(data.getTermName());
        }
        if (!blank(data.getExamDate()).isEmpty()) {
            if (sb.length() > 0) {
                sb.append("    ");
            }
            sb.append("考试日期：").append(data.getExamDate());
        }
        return sb.toString();
    }

    private Table studentInfoTable(ScoreReportData data) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{12f, 21f, 12f, 21f, 12f, 22f}))
                .useAllAvailableWidth();
        table.addCell(labelCell("姓名")).addCell(valueCell(data.getStudentName()));
        table.addCell(labelCell("学号")).addCell(valueCell(data.getStudentNo()));
        String classLabel = blank(data.getGradeName()).isEmpty()
                ? blank(data.getClassName())
                : data.getGradeName() + " " + blank(data.getClassName());
        table.addCell(labelCell("班级")).addCell(valueCell(classLabel));
        return table;
    }

    private Table subjectTable(ScoreReportData data) {
        Table table = new Table(UnitValue.createPercentArray(SUBJECT_COLUMNS)).useAllAvailableWidth();
        for (String header : new String[]{"科目", "满分", "得分", "班排", "年排", "校排", "班级均分", "年级均分"}) {
            table.addHeaderCell(headerCell(header));
        }
        for (ScoreReportData.SubjectRow row : data.getSubjectRows()) {
            table.addCell(bodyCell(row.getSubjectName(), TextAlignment.LEFT));
            table.addCell(bodyCell(number(row.getFullScore()), TextAlignment.CENTER));
            table.addCell(scoreCell(row));
            table.addCell(bodyCell(rank(row.getRankInClass()), TextAlignment.CENTER));
            table.addCell(bodyCell(rank(row.getRankInGrade()), TextAlignment.CENTER));
            table.addCell(bodyCell(rank(row.getRankInSchool()), TextAlignment.CENTER));
            table.addCell(bodyCell(number(row.getClassAverage()), TextAlignment.CENTER));
            table.addCell(bodyCell(number(row.getGradeAverage()), TextAlignment.CENTER));
        }
        return table;
    }

    private Cell scoreCell(ScoreReportData.SubjectRow row) {
        if (row.isMissing()) {
            return bodyCell("无记录", TextAlignment.CENTER).setFontColor(ColorConstants.GRAY);
        }
        if (row.isAbsent()) {
            // ponytail: 缺考用红字明示，不与 0 分混淆
            return bodyCell("缺考", TextAlignment.CENTER).setFontColor(ColorConstants.RED);
        }
        return bodyCell(number(row.getScore()), TextAlignment.CENTER);
    }

    private Table totalTable(ScoreReportData data) {
        Table table = new Table(UnitValue.createPercentArray(SUBJECT_COLUMNS)).useAllAvailableWidth();
        for (String header : new String[]{"", "总满分", "总分", "班排", "年排", "校排", "班级均分", "年级均分"}) {
            table.addHeaderCell(headerCell(header));
        }
        table.addCell(bodyCell("总分", TextAlignment.LEFT).setBold());
        table.addCell(bodyCell(number(data.getTotalFullScore()), TextAlignment.CENTER));
        table.addCell(bodyCell(number(data.getTotalScore()), TextAlignment.CENTER).setBold());
        table.addCell(bodyCell(rank(data.getTotalRankInClass()), TextAlignment.CENTER));
        table.addCell(bodyCell(rank(data.getTotalRankInGrade()), TextAlignment.CENTER));
        table.addCell(bodyCell(rank(data.getTotalRankInSchool()), TextAlignment.CENTER));
        table.addCell(bodyCell(number(data.getClassAverage()), TextAlignment.CENTER));
        table.addCell(bodyCell(number(data.getGradeAverage()), TextAlignment.CENTER));
        return table;
    }

    // ---------------------------------------------------------------- 单元格

    private static Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text).setBold())
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(4);
    }

    private static Cell bodyCell(String text, TextAlignment alignment) {
        return new Cell().add(new Paragraph(text)).setTextAlignment(alignment).setPadding(4);
    }

    private static Cell labelCell(String text) {
        return new Cell().add(new Paragraph(text))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(4);
    }

    private static Cell valueCell(String text) {
        return new Cell().add(new Paragraph(blank(text))).setPadding(4);
    }

    // ---------------------------------------------------------------- 格式化

    private static String number(BigDecimal value) {
        if (value == null) {
            return DASH;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toPlainString() : stripped.setScale(Math.min(stripped.scale(), 2), java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String rank(Integer value) {
        return value == null ? DASH : String.valueOf(value);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

}
