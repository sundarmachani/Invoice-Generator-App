package com.example.invoicegenerator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public class DataBase extends SQLiteOpenHelper {

    private static final String TAG = "DataBase";

    private static final String DATABASE_NAME = "invoice.db";
    private static final int DATABASE_VERSION = 1;
    public static final String DB_DIRECTORY = "invoice_data";
    private static final String DB_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + DB_DIRECTORY + "/db/";

    private static final String TABLE_ENTRIES = "entries";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CUSTOMER_NAME = "customer_name";
    public static final String COLUMN_INVOICE_NUMBER = "invoice_number";
    public static final String COLUMN_FEE = "fee";
    public static final String COLUMN_INVOICE_DATE = "invoice_date";

    private final Context mContext;

    public DataBase(Context context) {
        super(context, getDatabaseFile(context).getAbsolutePath(), null, DATABASE_VERSION);
        this.mContext = context.getApplicationContext();
        createDirectoryIfNotExist();
        Log.d(TAG, "Database path: " + getDatabaseFile(context).getAbsolutePath());
    }

    private static File getDatabaseFile(Context context) {
        // Use getExternalFilesDir() for scoped storage directory
        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DB_DIRECTORY + "/db/");
        if (!directory.exists()) {
            directory.mkdirs(); // Ensure directories are created
        }
        return new File(directory, DATABASE_NAME);
    }

    private void createDirectoryIfNotExist() {
        File directory = new File(DB_PATH);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + DB_PATH);
            }
        }
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
