package io.github.kalpa0448.taskmgr;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 XLSX 生成器（零依赖）。
 * 只支持：多行多列、首行加粗、字符串与数字。
 * 内部用 inlineStr，省掉 sharedStrings 表。
 */
public final class XlsxWriter {

    private XlsxWriter() {}

    public static void write(File out, String sheetName, List<String[]> rows) throws IOException {
        try (OutputStream os = new FileOutputStream(out)) {
            write(os, sheetName, rows);
        }
    }

    public static void write(OutputStream stream, String sheetName, List<String[]> rows)
            throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(stream))) {
            put(z, "[Content_Types].xml", CONTENT_TYPES);
            put(z, "_rels/.rels", ROOT_RELS);
            put(z, "xl/workbook.xml", workbook(sheetName));
            put(z, "xl/_rels/workbook.xml.rels", WB_RELS);
            put(z, "xl/styles.xml", STYLES);
            put(z, "xl/worksheets/sheet1.xml", sheet(rows));
        }
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static String sheet(List<String[]> rows) {
        StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
          .append("<cols>");
        // 给每列一个默认宽度，方便阅读
        int cols = 0;
        for (String[] r : rows) cols = Math.max(cols, r.length);
        for (int i = 1; i <= Math.max(cols, 1); i++) {
            sb.append("<col min=\"").append(i).append("\" max=\"").append(i)
              .append("\" width=\"").append(i == 1 ? 26 : 14).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");

        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            sb.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < row.length; c++) {
                String ref = colName(c) + (r + 1);
                String v = row[c] == null ? "" : row[c];
                boolean num = isNumber(v);
                sb.append("<c r=\"").append(ref).append('"');
                if (r == 0) sb.append(" s=\"1\"");
                if (num) {
                    sb.append("><v>").append(v).append("</v></c>");
                } else {
                    sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                      .append(esc(v)).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static boolean isNumber(String v) {
        if (v == null || v.isEmpty()) return false;
        if (v.length() > 15) return false;                 // 避免 PID 之类被科学计数
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (!(ch >= '0' && ch <= '9') && ch != '.' && ch != '-') return false;
        }
        return true;
    }

    private static String colName(int idx) {
        StringBuilder s = new StringBuilder();
        int n = idx;
        while (true) {
            s.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
            if (n < 0) break;
        }
        return s.toString();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    if (c < 0x20 && c != '\t' && c != '\n') b.append(' ');
                    else b.append(c);
            }
        }
        return b.toString();
    }

    private static String workbook(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + esc(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static final String CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WB_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    private static final String STYLES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border/></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"2\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "</cellXfs></styleSheet>";
}
