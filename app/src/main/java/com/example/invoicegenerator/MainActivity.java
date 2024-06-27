package com.example.invoicegenerator;

import static com.example.invoicegenerator.DataBase.DB_DIRECTORY;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity {

    private int invoiceCounter = 1;
    private static final int STORAGE_PERMISSION_CODE = 1;

    private EditText etCustomerName, etFee, etCity, dateEditText;
    private Spinner spCustomerType, spPaymentMode;
    private Button btnGenerateInvoice;
    private Calendar calendar;
    private DataBase dataBase;
    private GridLayout monthsGridLayout;
    private String monthName = "";
    private Map<String, Double> monthTotalsMap = new HashMap<>();
    private Set<Integer> invoiceNumberSet = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etCustomerName = findViewById(R.id.etCustomerName);
        etFee = findViewById(R.id.etFee);
        etCity = findViewById(R.id.etCity);
        spCustomerType = findViewById(R.id.spCustomerType);
        spPaymentMode = findViewById(R.id.spPaymentMode);
        btnGenerateInvoice = findViewById(R.id.btnGenerateInvoice);
        dateEditText = findViewById(R.id.dateEditText);
        calendar = Calendar.getInstance();
        dataBase = new DataBase(this);
        monthsGridLayout = findViewById(R.id.monthsGridLayout);

        int year = 0;

        dateEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH);
                int day = calendar.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        calendar.set(year, month, dayOfMonth);
                        // Format the date as dd/MM/yyyy using String.format
                        String formattedDate = String.format("%02d/%02d/%d", dayOfMonth, month + 1, year);
                        dateEditText.setText(formattedDate);
                    }
                }, year, month, day);
                datePickerDialog.setTitle("Invoice Date");
                datePickerDialog.show();
            }
        });

        reloadData();

        btnGenerateInvoice.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!isStoragePermissionGranted()) {
                    requestStoragePermission();
                } else {
                    // Proceed with your logic here if permission is already granted
                    generateInvoice(dataBase);
                }
            }
        });
    }

    private void generateInvoice(DataBase dataBase) {
        String customerName = etCustomerName.getText().toString().trim();
        String feeStr = etFee.getText().toString().trim();
        String customerType = spCustomerType.getSelectedItem().toString();
        String city = etCity.getText().toString().trim();
        String paymentMode = spPaymentMode.getSelectedItem().toString();

        if (customerName.isEmpty() || feeStr.isEmpty() || city.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double fee = Double.parseDouble(feeStr);
        if (!invoiceNumberSet.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                invoiceCounter = Collections.max(invoiceNumberSet) + 1;
            }
        } else {
            invoiceCounter = 1;
        }
        String invoiceNumber = getNextInvoiceNumber(invoiceCounter);
        String date = dateEditText.getText().toString();

        boolean entryMade = dataBase.addEntry(customerName, invoiceNumber, fee, date);
        if (entryMade) {
            reloadData();
        }
        Document document = new Document();
        File filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + DB_DIRECTORY + "/", "Invoice_" + customerName + ".pdf");
        try {
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("INVOICE", titleFont);
            title.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(title);

            Drawable logoDrawable = ContextCompat.getDrawable(MainActivity.this, R.drawable.nandi_group_of_companies_india_logo);
            Image logo = Image.getInstance(drawableToBytes(logoDrawable));
            logo.scaleAbsolute(100, 80);
            logo.setAlignment(Image.ALIGN_RIGHT);
            document.add(logo);

            // Adding invoice details
            Font detailsFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font headingFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            document.add(new Paragraph("Nandi Pipes Badminton Academy", headingFont));
            document.add(new Paragraph("Nandyal", detailsFont));
            document.add(new Paragraph("Email: anamala.nagarjuna9@gmail.com", detailsFont));
            document.add(new Paragraph(" "));

            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);
            detailsTable.setSpacingBefore(10f);
            detailsTable.setSpacingAfter(10f);

            addDetailsCell(detailsTable, "Bill To:-", PdfPCell.ALIGN_LEFT);
            addDetailsCell(detailsTable, "Invoice No: " + invoiceNumber, PdfPCell.ALIGN_RIGHT);
            addDetailsCell(detailsTable, "Name: " + customerName + "\n" + "Type: " + customerType + "\n" + "Place: " + city, PdfPCell.ALIGN_LEFT);
            addDetailsCell(detailsTable, "Invoice Date: " + date, PdfPCell.ALIGN_RIGHT);

            document.add(detailsTable);

            PdfPTable table = new PdfPTable(4); // 4 columns for S.No, Description, Fee, and Total Fee
            table.setWidthPercentage(100); // Set table width to 100% of the page width
            table.setSpacingBefore(10f); // Space before the table
            table.setSpacingAfter(10f); // Space after the table

            // Set the widths of the columns
            float[] columnWidths = {1f, 3f, 1f, 1f}; // Adjust the widths as needed
            table.setWidths(columnWidths);

            // Adding table header
            addTableHeader(table);

            // Adding table rows
            addRow(table, "1", "Monthly Fee\nPaid " + paymentMode, String.valueOf(fee), String.valueOf(fee));

            document.add(table);

            // Adding total amount
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(75);
            addTotalRow(totalTable, "", "");
            addTotalRow(totalTable, "Subtotal", String.valueOf(fee));
            addTotalRow(totalTable, "Total", String.valueOf(fee));
            addTotalRow(totalTable, "Paid (" + date + ")", String.valueOf(fee));
            addTotalRow(totalTable, "Balance Due", "0.00");

            document.add(totalTable);

            // Adding notes
            document.add(new Paragraph(" "));
            Paragraph note = new Paragraph("Notes:", headingFont);
            note.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(note);
            Paragraph notesPara1 = new Paragraph("* Please pay every month on or before 5th *", detailsFont);
            notesPara1.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(notesPara1);
            Paragraph notePara2 = new Paragraph(" * Thank you  :-) * ", detailsFont);
            notePara2.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(notePara2);

            logoDrawable = ContextCompat.getDrawable(MainActivity.this, R.drawable.signature);
            Image signature = Image.getInstance(drawableToBytes(logoDrawable));
            signature.scaleAbsolute(100, 50);
            signature.setAlignment(Element.ALIGN_RIGHT);
            document.add(signature);
            Paragraph signatoryParagraph = new Paragraph("Authorized Signatory", detailsFont);
            signatoryParagraph.setAlignment(Element.ALIGN_RIGHT);
            document.add(signatoryParagraph);

            document.close();
            Toast.makeText(this, "Invoice generated successfully. File Path: " + filePath, Toast.LENGTH_LONG).show();
        } catch (DocumentException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating invoice: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void reloadData() {
        monthTotalsMap.clear();
        for (int i = 0; i < monthsGridLayout.getChildCount(); i++) {
            TextView monthView = (TextView) monthsGridLayout.getChildAt(i);
            monthView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String month = ((TextView) v).getText().toString();
                    Double fee = Double.MIN_VALUE;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        fee = monthTotalsMap.getOrDefault(month, 0.0);
                    }
                    Toast.makeText(MainActivity.this, "Fee for " + month + ": " + fee, Toast.LENGTH_SHORT).show();
                }
            });
        }

        Cursor cursor = dataBase.getAllEntries();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DataBase.COLUMN_ID));
                String customerName = cursor.getString(cursor.getColumnIndexOrThrow(DataBase.COLUMN_CUSTOMER_NAME));
                String invoiceNumber = cursor.getString(cursor.getColumnIndexOrThrow(DataBase.COLUMN_INVOICE_NUMBER));
                double fee = cursor.getDouble(cursor.getColumnIndexOrThrow(DataBase.COLUMN_FEE));
                String invoiceDate = cursor.getString(cursor.getColumnIndexOrThrow(DataBase.COLUMN_INVOICE_DATE));
                invoiceNumberSet.add(Integer.parseInt(invoiceNumber.substring(5, 10)));
                String month = invoiceDate.substring(3, 5);
                monthName = getMonthName((Integer.parseInt(month)) - 1);

                double currentFee = 0;
                Double feeValue = monthTotalsMap.get(monthName);
                if (feeValue != null) {
                    currentFee = feeValue;
                } else {
                    currentFee = 0;
                }
                monthTotalsMap.put(monthName, currentFee + fee);

                Log.d("DatabaseEntry", "ID: " + id + ", Customer Name: " + customerName + ", Invoice Number: " + invoiceNumber + ", Fee: " + fee + ", Invoice Date: " + invoiceDate);

            } while (cursor.moveToNext());
        }

        if (cursor != null) {
            cursor.close();
        }

    }

    private String getMonthName(int month) {
        String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        return monthNames[month];
    }

    private byte[] drawableToBytes(Drawable drawable) {
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    private String getNextInvoiceNumber(int invoiceNumber) {
        int PADDING = 5;
        String PREFIX = "NPBA-";
        return PREFIX + String.format("%0" + PADDING + "d", invoiceCounter++);
    }

    private void addTableHeader(PdfPTable table) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Stream.of("S.No", "Description", "Fee", "Total Fee")
                    .forEach(columnTitle -> {
                        PdfPCell header = new PdfPCell();
                        header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                        header.setBorderWidth(2);
                        header.setPhrase(new Phrase(columnTitle));
                        header.setHorizontalAlignment(Element.ALIGN_CENTER); // Center-align the header text
                        table.addCell(header);
                    });
        }
    }

    private void addRow(PdfPTable table, String serialNumber, String description, String fee, String totalFee) {
        PdfPCell cell;

        cell = new PdfPCell(new Phrase(serialNumber));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER); // Center-align the serial number
        table.addCell(cell);

        cell = new PdfPCell(new Phrase(description));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT); // Left-align the description
        table.addCell(cell);

        cell = new PdfPCell(new Phrase(fee));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT); // Right-align the fee
        table.addCell(cell);

        cell = new PdfPCell(new Phrase(totalFee));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT); // Right-align the total fee
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable totalTable, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        totalTable.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTable.addCell(valueCell);
    }

    private static void addDetailsCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
                generateInvoice(dataBase);
            } else {
                // Permission denied
                Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, STORAGE_PERMISSION_CODE);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, STORAGE_PERMISSION_CODE);
            }
        } else {
            // Requesting permission for devices below Android 11
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            return writePermission == PackageManager.PERMISSION_GRANTED && readPermission == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    protected void onDestroy() {
        dataBase.close();
        super.onDestroy();
    }
}
