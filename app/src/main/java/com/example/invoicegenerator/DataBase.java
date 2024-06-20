package com.example.invoicegenerator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DataBase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "invoice.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_ENTRIES = "entries";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CUSTOMER_NAME = "customer_name";
    public static final String COLUMN_INVOICE_NUMBER = "invoice_number";
    public static final String COLUMN_FEE = "fee";
    public static final String COLUMN_INVOICE_DATE = "invoice_date";

    public DataBase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_ENTRIES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_CUSTOMER_NAME + " TEXT, " +
                COLUMN_INVOICE_NUMBER + " TEXT, " +
                COLUMN_FEE + " REAL, " +
                COLUMN_INVOICE_DATE + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ENTRIES);
        onCreate(db);
    }

    public boolean addEntry(String customerName, String invoiceNumber, double fee, String invoiceDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CUSTOMER_NAME, customerName);
        values.put(COLUMN_INVOICE_NUMBER, invoiceNumber);
        values.put(COLUMN_FEE, fee);
        values.put(COLUMN_INVOICE_DATE, invoiceDate);
        long result = -1;
        try {
            result = db.insertOrThrow(TABLE_ENTRIES, null, values);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.close();
        }
        return result != -1;
    }


    public Cursor getAllEntries() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_ENTRIES, null);
    }
}