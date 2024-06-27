package com.example.invoicegenerator;

import android.app.DatePickerDialog;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
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

    private EditText etCustomerName, etFee, etCity, dateEditText;
    private Spinner spCustomerType, spPaymentMode;
    private Button btnGenerateInvoice;
    private Calendar calendar;
    private DataBase dataBase;
    private GridLayout monthsGridLayout;
    private String monthName = "";
    private Map<String, Double> monthTotalsMap = new HashMap<>();
    private Set<Integer> invoiceNumberSet = new HashSet<>();
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);

        firebaseStorage = FirebaseStorage.getInstance();
        storageReference = firebaseStorage.getReference();

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
                generateInvoice(dataBase);
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
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, outputStream);
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

            // Adding table header
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addTableHeader(table);

            // Adding table rows
            addRow(table, "1", "Monthly Fee\nPaid " + paymentMode, "1", String.valueOf(fee), String.valueOf(fee));

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
            byte[] pdfBytes = outputStream.toByteArray();

            // Upload PDF to Firebase Storage
            uploadFileToFirebaseStorage(pdfBytes, customerName);

            Toast.makeText(this, "Invoice generated and uploaded to Firebase Storage", Toast.LENGTH_LONG).show();

        } catch (DocumentException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate invoice: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void uploadFileToFirebaseStorage(byte[] pdfBytes, String customerName) {
        StorageReference storageRef = storageReference.child("invoices/" + customerName + "_invoice.pdf");
        UploadTask uploadTask = storageRef.putBytes(pdfBytes);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            Toast.makeText(MainActivity.this, "File uploaded successfully", Toast.LENGTH_LONG).show();
            Log.d("FirebaseStorage", "File uploaded successfully");
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                Log.d("FirebaseStorage", "Download URL: " + downloadUrl);
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Failed to retrieve download URL", Toast.LENGTH_LONG).show();
                Log.e("FirebaseStorage", "Failed to retrieve download URL", e);
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(MainActivity.this, "File upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("FirebaseStorage", "File upload failed", e);
        });
    }

    private byte[] drawableToBytes(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        }
        return null;
    }

    private void addTableHeader(PdfPTable table) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Stream.of("S.No", "Description", "Qty", "Rate (INR)", "Amount (INR)").forEach(columnTitle -> {
                PdfPCell header = new PdfPCell();
                header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                header.setBorderWidth(2);
                header.setPhrase(new Phrase(columnTitle));
                table.addCell(header);
            });
        }
    }

    private void addRow(PdfPTable table, String... columns) {
        for (String column : columns) {
            table.addCell(new PdfPCell(new Phrase(column)));
        }
    }

    private void addDetailsCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text));
        cell.setPadding(5);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String name, String value) {
        PdfPCell cell1 = new PdfPCell(new Phrase(name));
        cell1.setBorder(PdfPCell.NO_BORDER);
        table.addCell(cell1);

        PdfPCell cell2 = new PdfPCell(new Phrase(value));
        cell2.setBorder(PdfPCell.NO_BORDER);
        table.addCell(cell2);
    }

    @Override
    protected void onDestroy() {
        dataBase.close();
        super.onDestroy();
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

    private String getNextInvoiceNumber(int invoiceNumber) {
        int PADDING = 5;
        String PREFIX = "NPBA-";
        return PREFIX + String.format("%0" + PADDING + "d", invoiceCounter++);
    }

    private String getMonthName(int month) {
        String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        return monthNames[month];
    }
}