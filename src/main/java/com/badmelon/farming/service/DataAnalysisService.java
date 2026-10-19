/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.KnowledgeDoc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DataAnalysisService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Table> parseTables(String content) {
        Table csv;
        ArrayList<Table> tables = new ArrayList<Table>();
        if (content == null || content.isBlank()) {
            return tables;
        }
        tables.addAll(this.parseMarkdownTables(content));
        Table json = this.parseJsonTable(content);
        if (json != null) {
            tables.add(json);
        }
        if (tables.isEmpty() && (csv = this.parseCsvBlock(content)) != null) {
            tables.add(csv);
        }
        return tables;
    }

    public Table pickBestTable(List<Table> tables) {
        Table fallback = null;
        Table bestNumeric = null;
        int bestNumericCols = -1;
        for (Table t : tables) {
            int numCols;
            if (t == null || t.columns.size() < 2 || t.rows.isEmpty()) continue;
            if (fallback == null) {
                fallback = t;
            }
            if (!this.containsNumericColumn(t) || (numCols = this.numericColumns(t).size()) <= bestNumericCols) continue;
            bestNumericCols = numCols;
            bestNumeric = t;
        }
        return bestNumeric != null ? bestNumeric : fallback;
    }

    public boolean hasAnalyzableTable(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        for (Table t : this.parseTables(content)) {
            if (t == null || t.columns.size() < 2 || t.rows.isEmpty() || !this.containsNumericColumn(t)) continue;
            return true;
        }
        return false;
    }

    private List<Table> parseMarkdownTables(String content) {
        ArrayList<Table> tables = new ArrayList<Table>();
        String[] lines = content.split("\r?\n");
        int i = 0;
        while (i < lines.length) {
            List<String> header;
            String line = lines[i].trim();
            if (line.startsWith("|") && (header = this.splitMdRow(line)).size() >= 2) {
                int j;
                ArrayList<List<String>> rows = new ArrayList<List<String>>();
                for (j = i + 1; j < lines.length && lines[j].trim().startsWith("|"); ++j) {
                    List<String> cells = this.splitMdRow(lines[j].trim());
                    if (this.isSeparatorRow(cells)) continue;
                    while (cells.size() < header.size()) {
                        cells.add("");
                    }
                    if (cells.size() > header.size()) {
                        cells = cells.subList(0, header.size());
                    }
                    rows.add(cells);
                }
                if (!rows.isEmpty()) {
                    Table t = new Table();
                    t.columns = header;
                    t.rows = rows;
                    tables.add(t);
                }
                i = j;
                continue;
            }
            ++i;
        }
        return tables;
    }

    private List<String> splitMdRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        String[] parts = s.split("(?<!\\\\)\\|");
        ArrayList<String> cells = new ArrayList<String>();
        for (String p : parts) {
            String c = p.trim().replace("\\|", "|").replace("**", "").replace("`", "");
            cells.add(c);
        }
        return cells;
    }

    private boolean isSeparatorRow(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        for (String c : cells) {
            if (c.replace("-", "").replace(":", "").trim().isEmpty()) continue;
            return false;
        }
        return true;
    }

    private Table parseJsonTable(String content) {
        int s = content.indexOf(91);
        if (s < 0) {
            return null;
        }
        int e = content.lastIndexOf(93);
        if (e <= s) {
            return null;
        }
        String json = content.substring(s, e + 1);
        try {
            JsonNode arr = this.objectMapper.readTree(json);
            if (!arr.isArray() || arr.isEmpty()) {
                return null;
            }
            JsonNode first = arr.get(0);
            if (!first.isObject()) {
                return null;
            }
            Table t = new Table();
            first.fieldNames().forEachRemaining(t.columns::add);
            for (JsonNode item : arr) {
                if (!item.isObject()) continue;
                ArrayList<String> row = new ArrayList<String>();
                for (String c : t.columns) {
                    row.add(item.path(c).isMissingNode() ? "" : item.path(c).asText());
                }
                t.rows.add(row);
            }
            return t.rows.isEmpty() ? null : t;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private Table parseCsvBlock(String content) {
        String[] lines = content.split("\r?\n");
        String sep = null;
        int start = -1;
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.isBlank() || sep != null) continue;
            int comma = line.split(",", -1).length;
            int tab = line.split("\t", -1).length;
            if (comma < 2 && tab < 2) continue;
            sep = comma >= tab ? "," : "\t";
            start = i;
            break;
        }
        if (sep == null || start < 0) {
            return null;
        }
        Table t = new Table();
        ArrayList<String> header = new ArrayList<String>();
        for (String c : this.splitCsvLine(lines[start], sep)) {
            header.add(c.trim());
        }
        t.columns = header;
        for (int i = start + 1; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            if (line.contains(sep)) {
                List<String> row = this.splitCsvLine(line, sep);
                while (row.size() < header.size()) {
                    row.add("");
                }
                t.rows.add(row);
                continue;
            }
            if (!line.startsWith("|")) break;
        }
        return t.rows.isEmpty() ? null : t;
    }

    private List<String> splitCsvLine(String line, String sep) {
        ArrayList<String> cells = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); ++i) {
            char ch = line.charAt(i);
            if (ch == '\"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    cur.append('\"');
                    ++i;
                    continue;
                }
                inQuote = !inQuote;
                continue;
            }
            if (sep.equals(String.valueOf(ch)) && !inQuote) {
                cells.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        cells.add(cur.toString().trim());
        return cells;
    }

    public boolean isNumeric(String v) {
        if (v == null) {
            return false;
        }
        String t = this.extractLeadingNumber(v);
        return t != null;
    }

    public double toNum(String v) {
        String t = this.extractLeadingNumber(v);
        return Double.parseDouble(t);
    }

    private String extractLeadingNumber(String v) {
        if (v == null) {
            return null;
        }
        String s = v.replace("\u7ea6", "").replace(",", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        Matcher m = Pattern.compile("[-+]?\\d+(\\.\\d+)?").matcher(s);
        if (m.find() && m.start() == 0) {
            return m.group();
        }
        return null;
    }

    private boolean containsNumericColumn(Table t) {
        for (List<String> row : t.rows) {
            for (int i = 0; i < row.size() && i < t.columns.size(); ++i) {
                if (!this.isNumeric(row.get(i))) continue;
                return true;
            }
        }
        return false;
    }

    private List<Integer> numericColumns(Table t) {
        ArrayList<Integer> cols = new ArrayList<Integer>();
        for (int c = 0; c < t.columns.size(); ++c) {
            boolean numeric = true;
            int sample = 0;
            for (List<String> row : t.rows) {
                String v;
                if (c >= row.size() || (v = row.get(c)) == null || v.isBlank()) continue;
                ++sample;
                if (this.isNumeric(v)) continue;
                numeric = false;
                break;
            }
            if (!numeric || sample <= 0) continue;
            cols.add(c);
        }
        return cols;
    }

    public Map<String, Object> analyze(KnowledgeDoc doc, String operation, String col1, String col2, int topN) {
        String op;
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        List<Table> tables = this.parseTables(doc.getContent());
        Table table = this.pickBestTable(tables);
        if (table == null) {
            result.put("success", false);
            result.put("summary", "\u26a0\ufe0f \u6587\u6863\u300c" + doc.getFileName() + "\u300d\u4e2d\u672a\u89e3\u6790\u5230\u53ef\u5206\u6790\u7684\u6570\u636e\u8868\u683c\uff08\u652f\u6301 Markdown \u8868\u683c / CSV / JSON \u6570\u7ec4\uff09\u3002");
            result.put("chart", null);
            return result;
        }
        switch (op = operation == null || operation.isBlank() ? "describe" : operation.trim().toLowerCase()) {
            case "top": 
            case "topn": {
                return this.topN(table, col1, col2, topN <= 0 ? 5 : topN, doc.getFileName());
            }
            case "trend": 
            case "line": {
                return this.trend(table, col1, col2, doc.getFileName());
            }
            case "distribution": 
            case "pie": 
            case "dist": {
                return this.distribution(table, col1, col2, doc.getFileName());
            }
        }
        return this.describe(table, doc.getFileName());
    }

    private Map<String, Object> describe(Table t, String docName) {
        List<Integer> numCols = this.numericColumns(t);
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("doc", docName);
        result.put("columns", t.columns);
        result.put("rows", t.rows.size());
        result.put("operation", "describe");
        StringBuilder sb = new StringBuilder();
        sb.append("\ud83d\udcca \u6570\u636e\u7edf\u8ba1\uff08\u6765\u6e90\uff1a").append(docName).append("\uff0c\u5171 ").append(t.rows.size()).append(" \u884c\uff0c").append(t.columns.size()).append(" \u5217\uff09\n");
        ArrayList stats = new ArrayList();
        for (int c : numCols) {
            ArrayList<Double> vals = new ArrayList<Double>();
            for (List<String> row : t.rows) {
                if (c >= row.size() || !this.isNumeric(row.get(c))) continue;
                vals.add(this.toNum((String)row.get(c)));
            }
            if (vals.isEmpty()) continue;
            Collections.sort(vals);
            double sum = vals.stream().mapToDouble(Double::doubleValue).sum();
            double d = sum / (double)vals.size();
            double median = (Double)vals.get(vals.size() / 2);
            double min = (Double)vals.get(0);
            double max = (Double)vals.get(vals.size() - 1);
            double std = 0.0;
            Iterator iterator = vals.iterator();
            while (iterator.hasNext()) {
                double v = (Double)iterator.next();
                std += (v - d) * (v - d);
            }
            std = vals.size() > 1 ? Math.sqrt(std / (double)(vals.size() - 1)) : 0.0;
            LinkedHashMap<String, Object> s = new LinkedHashMap<String, Object>();
            s.put("column", t.columns.get(c));
            s.put("count", vals.size());
            s.put("mean", this.round(d));
            s.put("median", this.round(median));
            s.put("min", this.round(min));
            s.put("max", this.round(max));
            s.put("sum", this.round(sum));
            s.put("std", this.round(std));
            stats.add(s);
            sb.append("\u2022 ").append(t.columns.get(c)).append("\uff1a\u5408\u8ba1=").append(this.round(sum)).append("\uff0c\u5747\u503c=").append(this.round(d)).append("\uff0c\u4e2d\u4f4d\u6570=").append(this.round(median)).append("\uff0c\u6700\u5927=").append(this.round(max)).append("\uff0c\u6700\u5c0f=").append(this.round(min)).append("\uff0c\u6807\u51c6\u5dee=").append(this.round(std)).append("\n");
        }
        result.put("stats", stats);
        if (stats.isEmpty()) {
            sb.append("\u2022 \u672a\u53d1\u73b0\u6570\u503c\u5217\uff0c\u53ef\u5c1d\u8bd5 top / distribution \u5206\u6790\u3002\n");
        }
        LinkedHashMap<String, Object> chart = new LinkedHashMap<String, Object>();
        if (!stats.isEmpty()) {
            ArrayList series = new ArrayList();
            ArrayList<String> x = new ArrayList<String>();
            ArrayList data = new ArrayList();
            for (Object o : stats) {
                Map<String, Object> map = (Map<String, Object>) o;
                x.add((String) map.get("column"));
                data.add(map.get("sum"));
            }
            LinkedHashMap<String, Object> ser = new LinkedHashMap<String, Object>();
            ser.put("name", "\u5408\u8ba1");
            ser.put("data", data);
            series.add(ser);
            chart.put("type", "bar");
            chart.put("title", docName + " \u2014 \u6570\u503c\u5217\u5408\u8ba1");
            chart.put("x", x);
            chart.put("series", series);
        }
        result.put("chart", chart.isEmpty() ? null : chart);
        result.put("summary", sb.toString());
        return result;
    }

    private Map<String, Object> topN(Table t, String col1, String col2, int topN, String docName) {
        int valCol;
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("doc", docName);
        result.put("operation", "topN");
        int catCol = t.colIndex(col1);
        if (catCol < 0) {
            catCol = this.firstNonNumericColumn(t);
        }
        if ((valCol = t.colIndex(col2)) < 0) {
            valCol = this.firstNumericColumn(t);
        }
        if (catCol < 0 || valCol < 0 || catCol == valCol) {
            result.put("summary", "\u26a0\ufe0f \u8868\u683c\u4e2d\u65e0\u6cd5\u786e\u5b9a\u5206\u7c7b\u5217\u4e0e\u6570\u503c\u5217\uff0c\u65e0\u6cd5\u6267\u884c topN \u5206\u6790\u3002");
            result.put("chart", null);
            return result;
        }
        ArrayList<Object[]> entries = new ArrayList<Object[]>();
        for (List<String> row : t.rows) {
            String cat;
            if (catCol >= row.size() || valCol >= row.size() || !this.isNumeric(row.get(valCol)) || (cat = row.get(catCol).trim()).isEmpty()) continue;
            entries.add(new Object[]{cat, this.toNum(row.get(valCol))});
        }
        entries.sort((a, b) -> Double.compare((Double)b[1], (Double)a[1]));
        int n = Math.min(topN, entries.size());
        ArrayList<String> x = new ArrayList<String>();
        ArrayList<Object> data = new ArrayList<Object>();
        StringBuilder sb = new StringBuilder();
        sb.append("\ud83d\udcca ").append(docName).append(" \u2014 \u6309\u300c").append(t.columns.get(catCol)).append("\u300d\u7684\u300c").append(t.columns.get(valCol)).append("\u300dTop ").append(n).append("\uff1a\n");
        for (int i = 0; i < n; ++i) {
            Object[] e = (Object[])entries.get(i);
            x.add((String)e[0]);
            data.add(e[1]);
            sb.append("  ").append(i + 1).append(". ").append(e[0]).append("\uff1a").append(this.round((Double)e[1])).append("\n");
        }
        LinkedHashMap<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "bar");
        chart.put("title", t.columns.get(catCol) + " vs " + t.columns.get(valCol) + "\uff08Top " + n + "\uff09");
        chart.put("x", x);
        LinkedHashMap<String, Object> ser = new LinkedHashMap<String, Object>();
        ser.put("name", t.columns.get(valCol));
        ser.put("data", data);
        chart.put("series", List.of(ser));
        result.put("chart", chart);
        result.put("summary", sb.toString());
        return result;
    }

    private Map<String, Object> trend(Table t, String col1, String col2, String docName) {
        int yCol;
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("doc", docName);
        result.put("operation", "trend");
        int xCol = t.colIndex(col1);
        if (xCol < 0) {
            xCol = 0;
        }
        if ((yCol = t.colIndex(col2)) < 0) {
            yCol = this.firstNumericColumn(t);
        }
        if (yCol < 0) {
            result.put("summary", "\u26a0\ufe0f \u8868\u683c\u4e2d\u6ca1\u6709\u6570\u503c\u5217\uff0c\u65e0\u6cd5\u751f\u6210\u8d8b\u52bf\u56fe\u3002");
            result.put("chart", null);
            return result;
        }
        ArrayList<String> x = new ArrayList<String>();
        ArrayList<Double> data = new ArrayList<Double>();
        StringBuilder sb = new StringBuilder();
        sb.append("\ud83d\udcc8 ").append(docName).append(" \u2014 \u8d8b\u52bf\u5206\u6790\uff08").append(t.columns.get(yCol)).append(" \u968f ").append(t.columns.get(xCol)).append(" \u53d8\u5316\uff09\uff1a\n");
        for (List<String> row : t.rows) {
            if (xCol >= row.size() || yCol >= row.size() || !this.isNumeric(row.get(yCol))) continue;
            x.add(row.get(xCol).trim());
            data.add(this.toNum(row.get(yCol)));
        }
        for (int i = 0; i < x.size(); ++i) {
            sb.append("  ").append((String)x.get(i)).append("\uff1a").append(this.round((Double)data.get(i))).append("\n");
        }
        LinkedHashMap<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "line");
        chart.put("title", t.columns.get(xCol) + " \u2014 " + t.columns.get(yCol));
        chart.put("x", x);
        LinkedHashMap<String, Object> ser = new LinkedHashMap<String, Object>();
        ser.put("name", t.columns.get(yCol));
        ser.put("data", data);
        chart.put("series", List.of(ser));
        result.put("chart", chart);
        result.put("summary", sb.toString());
        return result;
    }

    private Map<String, Object> distribution(Table t, String col1, String col2, String docName) {
        int valCol;
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("doc", docName);
        result.put("operation", "distribution");
        int catCol = t.colIndex(col1);
        if (catCol < 0) {
            catCol = this.firstNonNumericColumn(t);
        }
        if ((valCol = t.colIndex(col2)) < 0) {
            valCol = this.firstNumericColumn(t);
        }
        if (catCol < 0) {
            result.put("summary", "\u26a0\ufe0f \u8868\u683c\u4e2d\u6ca1\u6709\u5206\u7c7b\u5217\uff0c\u65e0\u6cd5\u751f\u6210\u5206\u5e03\u56fe\u3002");
            result.put("chart", null);
            return result;
        }
        LinkedHashMap<String, Double> agg = new LinkedHashMap<String, Double>();
        if (valCol >= 0 && valCol != catCol) {
            for (List<String> row : t.rows) {
                if (catCol >= row.size() || valCol >= row.size() || !this.isNumeric(row.get(valCol))) continue;
                String cat = row.get(catCol).trim();
                if (cat.isEmpty()) continue;
                agg.merge(cat, this.toNum(row.get(valCol)), Double::sum);
            }
        } else {
            for (List<String> row : t.rows) {
                if (catCol >= row.size()) continue;
                String cat = row.get(catCol).trim();
                if (cat.isEmpty()) continue;
                agg.merge(cat, 1.0, Double::sum);
            }
        }
        ArrayList x = new ArrayList(agg.keySet());
        ArrayList data = new ArrayList(agg.values());
        StringBuilder sb = new StringBuilder();
        sb.append("\ud83d\udcca ").append(docName).append(" \u2014 \u5206\u5e03\u5206\u6790\uff08").append((String)(valCol >= 0 && valCol != catCol ? "\u6309\u300c" + t.columns.get(valCol) + "\u300d\u6c47\u603b" : "\u8ba1\u6570")).append("\uff09\uff1a\n");
        double total = agg.values().stream().mapToDouble(Double::doubleValue).sum();
        int idx = 0;
        for (Map.Entry en : agg.entrySet()) {
            double pct = total > 0.0 ? (Double)en.getValue() / total * 100.0 : 0.0;
            sb.append("  ").append((String)en.getKey()).append("\uff1a").append(this.round((Double)en.getValue())).append("\uff08").append(this.round(pct)).append("%\uff09\n");
            ++idx;
        }
        LinkedHashMap<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "pie");
        chart.put("title", t.columns.get(catCol) + " \u5206\u5e03");
        chart.put("x", x);
        LinkedHashMap<String, Object> ser = new LinkedHashMap<String, Object>();
        ser.put("name", t.columns.get(catCol));
        ser.put("data", data);
        chart.put("series", List.of(ser));
        result.put("chart", chart);
        result.put("summary", sb.toString());
        return result;
    }

    private int firstNumericColumn(Table t) {
        for (int c = 0; c < t.columns.size(); ++c) {
            for (List<String> row : t.rows) {
                if (c >= row.size() || !this.isNumeric(row.get(c))) continue;
                return c;
            }
        }
        return -1;
    }

    private int firstNonNumericColumn(Table t) {
        ArrayList<Integer> num = new ArrayList<Integer>(this.numericColumns(t));
        for (int c = 0; c < t.columns.size(); ++c) {
            if (num.contains(c)) continue;
            return c;
        }
        return -1;
    }

    private double round(double v) {
        return (double)Math.round(v * 100.0) / 100.0;
    }

    public static class Table {
        public List<String> columns = new ArrayList<String>();
        public List<List<String>> rows = new ArrayList<List<String>>();

        public int colIndex(String name) {
            if (name == null || name.isBlank()) {
                return -1;
            }
            for (int i = 0; i < this.columns.size(); ++i) {
                if (!this.columns.get(i).equalsIgnoreCase(name.trim())) continue;
                return i;
            }
            return -1;
        }
    }
}

