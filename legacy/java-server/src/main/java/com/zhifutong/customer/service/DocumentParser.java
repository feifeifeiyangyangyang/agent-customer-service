package com.zhifutong.customer.service;

import com.zhifutong.customer.exception.BusinessException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

@Component
public class DocumentParser {

    public String parse(Path path, String extension) {
        try {
            String text = switch (extension) {
                case "pdf" -> parsePdf(path);
                case "docx" -> parseDocx(path);
                case "txt" -> Files.readString(path, StandardCharsets.UTF_8);
                case "md" -> parseMarkdown(path);
                default -> throw new BusinessException("不支持的文件类型: " + extension);
            };
            if (text == null || text.isBlank()) {
                throw new BusinessException("未提取到有效文本，扫描版 PDF 或空文档暂不支持");
            }
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("文档解析失败: " + ex.getMessage());
        }
    }

    private String parsePdf(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseDocx(Path path) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    builder.append(text).append('\n');
                }
            });
            return builder.toString();
        }
    }

    private String parseMarkdown(Path path) throws Exception {
        String markdown = Files.readString(path, StandardCharsets.UTF_8);
        Parser parser = Parser.builder().build();
        return TextContentRenderer.builder().build().render(parser.parse(markdown));
    }
}
