package com.hmdp.controller;

import com.hmdp.utils.ExportCsvUtil;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

  @PostMapping(value = "/export")
  public void getSkuList1(HttpServletResponse response) {
    List<Object[]> cellList = new ArrayList<>();
    Object[] obj1 = {1, "小明", 13};
    Object[] obj2 = {2, "小强", 14};
    Object[] obj3 = {3, "小红", 15};
    cellList.add(obj1);
    cellList.add(obj2);
    cellList.add(obj3);

    String[] tableHeaderArr = {"id", "姓名", "年龄"};
    String fileName = "导出文件.csv";
    byte[] bytes = ExportCsvUtil.writeCsvAfterToBytes(tableHeaderArr, cellList);
    ExportCsvUtil.responseSetProperties(fileName, bytes, response);
  }
}
