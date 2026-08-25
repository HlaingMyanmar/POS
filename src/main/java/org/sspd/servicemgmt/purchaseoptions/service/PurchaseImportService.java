package org.sspd.servicemgmt.purchaseoptions.service;
import lombok.RequiredArgsConstructor;import org.apache.poi.ss.usermodel.*;import org.springframework.stereotype.Service;import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseImportPreviewDTO;import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import java.io.*;import java.math.*;import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class PurchaseImportService{
 private final ProductRepository products;
 public PurchaseImportPreviewDTO preview(MultipartFile file)throws IOException{
  if(file==null||file.isEmpty())throw new RuntimeException("Excel file is required.");
  if(file.getSize()>5L*1024*1024)throw new RuntimeException("Excel file must be 5MB or smaller.");
  String n=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();if(!n.endsWith(".xlsx")&&!n.endsWith(".xls"))throw new RuntimeException("Only .xlsx or .xls files are supported.");
  List<PurchaseImportPreviewDTO.Row> out=new ArrayList<>();List<String> global=new ArrayList<>();DataFormatter fmt=new DataFormatter();
  try(Workbook wb=WorkbookFactory.create(file.getInputStream())){Sheet s=wb.getSheetAt(0);Row header=s.getRow(s.getFirstRowNum());if(header==null)throw new RuntimeException("Header row is missing.");
   Map<String,Integer> cols=new HashMap<>();for(Cell c:header)cols.put(normalize(fmt.formatCellValue(c)),c.getColumnIndex());
   int codeCol=required(cols,"productcode","product code","code","sku"),qtyCol=required(cols,"qty","quantity"),costCol=required(cols,"unitcost","unit cost","cost","purchaseprice");
   Integer batchCol=optional(cols,"batch","batchnumber","batch no"),expiryCol=optional(cols,"expiry","expirydate","expirationdate");
   for(int i=header.getRowNum()+1;i<=s.getLastRowNum();i++){Row r=s.getRow(i);if(r==null)continue;String code=value(r,codeCol,fmt).trim();if(code.isBlank()&&value(r,qtyCol,fmt).isBlank()&&value(r,costCol,fmt).isBlank())continue;List<String> errors=new ArrayList<>();var product=products.findByProductCode(code).orElse(null);if(product==null)errors.add("Product code not found: "+code);Integer qty=parseInt(value(r,qtyCol,fmt));if(qty==null||qty<=0)errors.add("Qty must be a positive whole number");BigDecimal cost=parseDecimal(value(r,costCol,fmt));if(cost==null||cost.signum()<=0)errors.add("Unit cost must be greater than zero");LocalDate expiry=parseDate(expiryCol==null?null:r.getCell(expiryCol),fmt,errors);
    String batch=batchCol==null?null:value(r,batchCol,fmt).trim();boolean valid=errors.isEmpty();out.add(PurchaseImportPreviewDTO.Row.builder().rowNumber(i+1).productCode(code).productId(product==null?null:product.getId()).productName(product==null?null:product.getName()).qty(qty).unitCost(cost).subtotal(valid?cost.multiply(BigDecimal.valueOf(qty)):null).batchNumber(batch==null||batch.isBlank()?null:batch).expiryDate(expiry).serialRequired(product!=null&&Boolean.TRUE.equals(product.getHasSerial())).valid(valid).errors(errors).build());
   }
  }catch(IllegalArgumentException e){throw new RuntimeException(e.getMessage());}
  int valid=(int)out.stream().filter(x->Boolean.TRUE.equals(x.getValid())).count();for(var r:out)if(!Boolean.TRUE.equals(r.getValid()))global.add("Row "+r.getRowNumber()+": "+String.join("; ",r.getErrors()));
  return PurchaseImportPreviewDTO.builder().totalRows(out.size()).validRows(valid).invalidRows(out.size()-valid).rows(out).errors(global).build();
 }
 public byte[] template()throws IOException{try(Workbook wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new ByteArrayOutputStream()){Sheet s=wb.createSheet("Purchase Import");Row h=s.createRow(0);String[] headers={"Product Code","Qty","Unit Cost","Batch Number","Expiry Date"};for(int i=0;i<headers.length;i++){h.createCell(i).setCellValue(headers[i]);s.setColumnWidth(i,i==0?5000:3500);}Row example=s.createRow(1);example.createCell(0).setCellValue("PRODUCT-CODE");example.createCell(1).setCellValue(1);example.createCell(2).setCellValue(1000);example.createCell(3).setCellValue("BATCH-001");example.createCell(4).setCellValue(LocalDate.now().plusYears(1).toString());wb.write(out);return out.toByteArray();}}
 private int required(Map<String,Integer>m,String...names){Integer i=optional(m,names);if(i==null)throw new IllegalArgumentException("Missing required column: "+names[0]);return i;}
 private Integer optional(Map<String,Integer>m,String...names){for(String n:names){Integer i=m.get(normalize(n));if(i!=null)return i;}return null;}
 private String normalize(String s){return s==null?"":s.toLowerCase().replaceAll("[^a-z0-9]","");}
 private String value(Row r,int col,DataFormatter f){Cell c=r.getCell(col);return c==null?"":f.formatCellValue(c);}
 private Integer parseInt(String s){try{BigDecimal n=new BigDecimal(s.replace(",","").trim());return n.stripTrailingZeros().scale()>0?null:n.intValueExact();}catch(Exception e){return null;}}
 private BigDecimal parseDecimal(String s){try{return new BigDecimal(s.replace(",","").trim()).setScale(2,RoundingMode.HALF_UP);}catch(Exception e){return null;}}
 private LocalDate parseDate(Cell c,DataFormatter f,List<String>errors){if(c==null||f.formatCellValue(c).isBlank())return null;try{if(DateUtil.isCellDateFormatted(c))return c.getLocalDateTimeCellValue().toLocalDate();String v=f.formatCellValue(c).trim();for(var p:List.of(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"),java.time.format.DateTimeFormatter.ofPattern("d-M-yyyy")))try{return LocalDate.parse(v,p);}catch(Exception ignored){}errors.add("Invalid expiry date: "+v);return null;}catch(Exception e){errors.add("Invalid expiry date");return null;}}
}
