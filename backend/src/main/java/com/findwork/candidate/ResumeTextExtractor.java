package com.findwork.candidate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
public class ResumeTextExtractor {
    public String extract(String filename, byte[] content) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            String text = lower.endsWith(".pdf") ? extractPdf(content) : extractDocx(content);
            return normalize(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("简历文件无法解析，请上传有效的 PDF 或 DOCX 文件", e);
        }
    }

    private String extractPdf(byte[] content) throws IOException {
        try (PDDocument document = PDDocument.load(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] content) throws IOException {
        try (InputStream input = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(input)) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            return text.toString();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
    }
}
