package com.hmdp.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.lang3.StringUtils;

/**
 * @author dingchuan
 */
@Slf4j
public class ExportCsvUtil {


  /**
   * 写CSV并转换为字节流
   *
   * @param tableHeaderArr 表头
   * @param cellList       数据
   * @return
   */
  public static byte[] writeDataAfterToBytes(String[] tableHeaderArr, List<String> cellList) {
    byte[] bytes = new byte[0];
    ByteArrayOutputStream byteArrayOutputStream = null;
    OutputStreamWriter outputStreamWriter = null;
    BufferedWriter bufferedWriter = null;
    try {
      byteArrayOutputStream = new ByteArrayOutputStream();
      outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
      bufferedWriter = new BufferedWriter(outputStreamWriter);
      //excel文件需要通过文件头的bom来识别编码，而CSV文件格式不自带bom,所以写文件时，需要先写入bom头，否则excel打开乱码
      bufferedWriter.write(new String(ByteOrderMark.UTF_8.getBytes()));
      //写表头
      StringBuilder sb = new StringBuilder();
      String tableHeader = String.join(",", tableHeaderArr);
      sb.append(tableHeader + StringUtils.CR + StringUtils.LF);
      for (String rowCell : cellList) {
        sb.append(rowCell + StringUtils.CR + StringUtils.LF);
      }
      bufferedWriter.write(sb.toString());
      bufferedWriter.flush();
      //把输出流转换字节流
      bytes = byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()).getBytes();
      return bytes;
    } catch (IOException e) {
      log.error("writeDataAfterToBytes IOException:{}", e.getMessage(), e);
    } finally {
      try {
        if (bufferedWriter != null) {
          bufferedWriter.close();
        }
        if (outputStreamWriter != null) {
          outputStreamWriter.close();
        }
        if (byteArrayOutputStream != null) {
          byteArrayOutputStream.close();
        }
      } catch (IOException e) {
        log.error("iostream close IOException:{}", e.getMessage(), e);
      }
    }
    return bytes;
  }

  /**
   * 写CSV并转换为字节流
   *
   * @param headers  表头
   * @param cellList 表数据
   * @return
   */
  public static byte[] writeCsvAfterToBytes(String[] headers, List<Object[]> cellList) {
    byte[] bytes = new byte[0];
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream,
        StandardCharsets.UTF_8);
    BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
    CSVPrinter csvPrinter = null;
    try {
      //创建csvPrinter并设置表格头
      csvPrinter = new CSVPrinter(bufferedWriter, CSVFormat.DEFAULT.withHeader(headers));
      //写数据
      csvPrinter.printRecords(cellList);
      csvPrinter.flush();
      bytes = byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()).getBytes();
    } catch (IOException e) {
      log.error("writeCsv IOException:{}", e.getMessage(), e);
    } finally {
      try {
        if (csvPrinter != null) {
          csvPrinter.close();
        }
        if (bufferedWriter != null) {
          bufferedWriter.close();
        }
        if (outputStreamWriter != null) {
          outputStreamWriter.close();
        }
        if (byteArrayOutputStream != null) {
          byteArrayOutputStream.close();
        }
      } catch (IOException e) {
        log.error("iostream close IOException:{}", e.getMessage(), e);
      }
    }
    return bytes;
  }

  /**
   * 设置下载响应
   *
   * @param fileName
   * @param bytes
   * @param response
   */
  public static void responseSetProperties(String fileName, byte[] bytes,
      HttpServletResponse response) {
    try {
      fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
      response.setContentType("application/csv");
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.setHeader("Pragma", "public");
      response.setHeader("Cache-Control", "max-age=30");
      response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
      OutputStream outputStream = response.getOutputStream();
      outputStream.write(bytes);
      outputStream.flush();
    } catch (IOException e) {
      log.error("iostream error:{}", e.getMessage(), e);
    }
  }
}
