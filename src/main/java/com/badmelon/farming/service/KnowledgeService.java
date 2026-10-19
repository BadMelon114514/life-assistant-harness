/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.KnowledgeDoc;
import com.badmelon.farming.mapper.KnowledgeDocMapper;
import com.badmelon.farming.service.AiService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {
    private final KnowledgeDocMapper docMapper;
    private final AiService aiService;
    @Value(value="${farming.harness.knowledge-dir:./data/knowledge}")
    private String knowledgeDir;

    public KnowledgeService(KnowledgeDocMapper docMapper, AiService aiService) {
        this.docMapper = docMapper;
        this.aiService = aiService;
    }

    public List<KnowledgeDoc> findAll() {
        return this.docMapper.findAll();
    }

    public List<KnowledgeDoc> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return this.docMapper.findAll();
        }
        return this.docMapper.search(keyword.trim());
    }

    public KnowledgeDoc findByKey(String key) {
        return this.docMapper.findByKey(key);
    }

    public KnowledgeDoc upload(MultipartFile file, String tags) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String type = this.detectType(originalName);
        Path dir = Paths.get(this.knowledgeDir, new String[0]);
        Files.createDirectories(dir, new FileAttribute[0]);
        String docKey = "doc_" + UUID.randomUUID().toString().substring(0, 8);
        String storedName = docKey + "_" + originalName;
        Path stored = dir.resolve(storedName);
        file.transferTo(stored.toAbsolutePath());
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocKey(docKey);
        doc.setFileName(originalName);
        doc.setFileType(type);
        doc.setFileSize(file.getSize());
        doc.setFilePath(stored.toString());
        doc.setTags(tags);
        doc.setParseStatus("parsing");
        this.docMapper.insert(doc);
        String content = this.parseContent(stored, type);
        doc.setContent(content);
        doc.setParseStatus("done");
        doc.setSummary(this.aiService.summarize(content, 150));
        this.docMapper.update(doc);
        return doc;
    }

    public void delete(String key) throws IOException {
        KnowledgeDoc doc = this.docMapper.findByKey(key);
        if (doc != null) {
            Path p = Paths.get(doc.getFilePath(), new String[0]);
            if (Files.exists(p, new LinkOption[0])) {
                Files.deleteIfExists(p);
            }
            this.docMapper.deleteByKey(key);
        }
    }

    public KnowledgeDoc saveText(String fileName, String content, String tags) throws IOException {
        Object originalName = fileName == null || fileName.isBlank() ? "doc.txt" : fileName;
        String lower = ((String)originalName).toLowerCase();
        if (!(lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".html"))) {
            originalName = (String)originalName + ".md";
        }
        String type = this.detectType((String)originalName);
        Path dir = Paths.get(this.knowledgeDir, new String[0]);
        Files.createDirectories(dir, new FileAttribute[0]);
        String docKey = "doc_" + UUID.randomUUID().toString().substring(0, 8);
        String storedName = docKey + "_" + (String)originalName;
        Path stored = dir.resolve(storedName);
        Files.write(stored.toAbsolutePath(), content.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocKey(docKey);
        doc.setFileName((String)originalName);
        doc.setFileType(type);
        doc.setFileSize(Long.valueOf(content.getBytes(StandardCharsets.UTF_8).length));
        doc.setFilePath(stored.toString());
        doc.setTags(tags);
        doc.setParseStatus("done");
        doc.setContent(content);
        doc.setSummary(this.aiService.summarize(content, 150));
        this.docMapper.insert(doc);
        return doc;
    }

    private String detectType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "xlsx";
        }
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) {
            return "pptx";
        }
        if (lower.endsWith(".csv")) {
            return "csv";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".md")) {
            return "md";
        }
        return "txt";
    }

    private String parseContent(Path file, String type) throws IOException {
        switch (type) {
            case "pdf": {
                return this.parsePdf(file);
            }
            case "docx": {
                return this.parseDocx(file);
            }
            case "xlsx": {
                return this.parseXlsx(file);
            }
            case "pptx": {
                return this.parsePptx(file);
            }
        }
        return this.readText(file);
    }

    private String readText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Charset cs = StandardCharsets.UTF_8;
        if (this.isProbablyGBK(bytes)) {
            cs = Charset.forName("GBK");
        }
        return new String(bytes, cs);
    }

    private boolean isProbablyGBK(byte[] bytes) {
        try {
            new String(bytes, StandardCharsets.UTF_8);
            return false;
        }
        catch (Exception e) {
            return true;
        }
    }

    private String parsePdf(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF((File)file.toFile());){
            PDFTextStripper stripper = new PDFTextStripper();
            String string = stripper.getText(document);
            return string;
        }
    }

    private String parseDocx(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file, new OpenOption[0]));){
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().equals("word/document.xml")) continue;
                String xml = new String(this.readAll(zis), StandardCharsets.UTF_8);
                sb.append(this.extractXmlText(xml, "w:t"));
                break;
            }
        }
        return sb.toString();
    }

    private String parseXlsx(Path file) throws IOException {
        ZipEntry entry;
        StringBuilder sb = new StringBuilder();
        ArrayList<String> sharedStrings = new ArrayList<String>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file, new OpenOption[0]));){
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.equals("xl/sharedStrings.xml")) continue;
                String xml = new String(this.readAll(zis), StandardCharsets.UTF_8);
                for (String s : this.extractXmlValues(xml, "si")) {
                    sharedStrings.add(this.extractXmlText(s, "t"));
                }
            }
        }
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file, new OpenOption[0]));) {
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().startsWith("xl/worksheets/sheet") || !entry.getName().endsWith(".xml")) continue;
                String xml = new String(this.readAll(zis), StandardCharsets.UTF_8);
                String text = this.extractXmlText(xml, "v");
                String inline = this.extractXmlText(xml, "t");
                sb.append("[\u5de5\u4f5c\u8868 ").append(entry.getName()).append("]\n");
                sb.append(text).append("\n").append(inline).append("\n");
            }
        }
        return sb.toString();
    }

    private String parsePptx(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file, new OpenOption[0]));){
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().startsWith("ppt/slides/slide") || !entry.getName().endsWith(".xml")) continue;
                String xml = new String(this.readAll(zis), StandardCharsets.UTF_8);
                String text = this.extractXmlText(xml, "a:t");
                sb.append("[\u5e7b\u706f\u7247 ").append(entry.getName()).append("]\n").append(text).append("\n");
            }
        }
        return sb.toString();
    }

    private byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private String extractXmlText(String xml, String tag) {
        int end;
        int start;
        StringBuilder sb = new StringBuilder();
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int idx = 0;
        while ((start = xml.indexOf(open, idx)) >= 0 && (end = xml.indexOf(close, start)) >= 0) {
            String val = xml.substring(start + open.length(), end);
            val = val.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
            sb.append(val).append("\n");
            idx = end + close.length();
        }
        return sb.toString();
    }

    private List<String> extractXmlValues(String xml, String tag) {
        int end;
        int start;
        ArrayList<String> list = new ArrayList<String>();
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int idx = 0;
        while ((start = xml.indexOf(open, idx)) >= 0 && (end = xml.indexOf(close, start)) >= 0) {
            list.add(xml.substring(start + open.length(), end));
            idx = end + close.length();
        }
        return list;
    }
}

