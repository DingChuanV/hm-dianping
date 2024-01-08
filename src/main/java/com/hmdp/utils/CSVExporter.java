package com.hmdp.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVExporter {

  public static void exportToCSV(String filePath, List<String[]> data) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
      for (String[] row : data) {
        for (int i = 0; i < row.length; i++) {
          writer.append(escapeSpecialCharacters(row[i]));
          if (i < row.length - 1) {
            writer.append(",");
          }
        }
        writer.append("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String escapeSpecialCharacters(String cell) {
    if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
      cell = cell.replace("\"", "\"\"");
      cell = "\"" + cell + "\"";
    }
    return cell;
  }

  public static void main(String[] args) {
    // 示例用法
    List<String[]> data = List.of(
        new String[]{"Name", "Age", "City"},
        new String[]{"John Doe", "30", "New York"},
        new String[]{"Alice Smith", "25", "Los Angeles"},
        new String[]{"Bob Johnson", "35", "Chicago"}
    );

    exportToCSV("example.csv", data);
  }
}

