package com.example.storage;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.storage.util.Helper;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DownloadActivity extends AppCompatActivity implements View.OnClickListener{
	private ImageView mImageView;
	private StorageReference imageRef;
	private TextView mTextView;
	static final Integer WRITE_EXST = 1;
	static final Integer READ_EXST = 2;
	static final Integer PERMISSION_REQUEST_ID =4;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download);
		bindWidget();
		FirebaseStorage storage = FirebaseStorage.getInstance();
		// Check Permission.
		askForPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,WRITE_EXST);
		askForPermission(Manifest.permission.READ_EXTERNAL_STORAGE,READ_EXST);

		StorageReference storageRef = storage.getReference();
		imageRef = storageRef.child("photos/photo/image1.jpg");
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if(requestCode==PERMISSION_REQUEST_ID){

			if (grantResults.length > 1
					&& grantResults[1] == PackageManager.PERMISSION_GRANTED
					&& grantResults[2] == PackageManager.PERMISSION_GRANTED) {

			} else {
				Log.e("permission", "Permission not granted");

			}
		}
	}

	private void askForPermission(String permission, Integer requestCode) {
		if (ContextCompat.checkSelfPermission(DownloadActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {

			// Should we show an explanation?
			if (ActivityCompat.shouldShowRequestPermissionRationale(DownloadActivity.this, permission)) {

				//This is called if user has denied the permission before
				//In this case I am just asking the permission again
				ActivityCompat.requestPermissions(DownloadActivity.this, new String[]{permission}, requestCode);

			} else {

				ActivityCompat.requestPermissions(DownloadActivity.this, new String[]{permission}, requestCode);
			}
		} else {
			Toast.makeText(this, "" + permission + " is already granted.", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.button_download_in_memory:
				downloadInMemory();
				break;
			case R.id.button_download_in_file:
				downloadInLocalFile();
				break;
			case R.id.button_download_via_url:
				downloadDataViaUrl();
				break;
			case R.id.button_get_metadata:
				getMetadata();
				break;
			case R.id.button_update_metadata:
				updateMetaData();
				break;
			case R.id.button_delete_file:
				deleteFile();
				break;

			case R.id.button_zip_file:
				downloadInLocalZIP();
				break;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Helper.dismissProgressDialog();
		Helper.dismissDialog();
	}

	private void bindWidget() {
		mImageView = findViewById(R.id.imageview);
		mTextView = findViewById(R.id.textview);
		findViewById(R.id.button_download_in_memory).setOnClickListener(this);
		findViewById(R.id.button_download_in_file).setOnClickListener(this);
		findViewById(R.id.button_download_via_url).setOnClickListener(this);
		findViewById(R.id.button_get_metadata).setOnClickListener(this);
		findViewById(R.id.button_update_metadata).setOnClickListener(this);
		findViewById(R.id.button_delete_file).setOnClickListener(this);
		findViewById(R.id.button_zip_file).setOnClickListener(this);
	}

	private void downloadInMemory() {
		//long ONE_MEGABYTE = 1024 * 1024;
		Helper.showDialog(this);
		imageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
			@Override
			public void onSuccess(byte[] bytes) {
				Helper.dismissDialog();
				Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				mImageView.setImageBitmap(bitmap);
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		});
	}

	private void downloadInLocalZIP() {
		File dir = new File(Environment.getExternalStorageDirectory() + "/photos");
		final File file = new File(dir, "photos.zip");
		try {
			if (!dir.exists()) {
				dir.mkdir();
			}
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		FirebaseStorage storage = FirebaseStorage.getInstance();
		StorageReference storageRef = storage.getReference();
		StorageReference imageRef;
		imageRef = storageRef.child("photos.zip");
		final FileDownloadTask fileDownloadTask = imageRef.getFile(file);
		Helper.initProgressDialog(this);
		Helper.mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {
				fileDownloadTask.cancel();
			}
		});
		Helper.mProgressDialog.show();

		fileDownloadTask.addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
			@Override
			public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
				Helper.dismissProgressDialog();
				mTextView.setText(file.getPath());
				File sourceFilePath = new File(Environment.getExternalStorageDirectory() + "/photos", "photos.zip");
				File destinationFilePathAfterUnzip = new File(Environment.getExternalStorageDirectory() + "/photos", "");
				try {
					unzip(sourceFilePath,destinationFilePathAfterUnzip);
				} catch (IOException e) {
					e.printStackTrace();
					Log.e("TAGG", "File error "+ e.getMessage());
				}
				//mImageView.setImageURI(Uri.fromFile(file));
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissProgressDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		}).addOnProgressListener(new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
			@Override
			public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
				int progress = (int) ((100 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount());
				Helper.setProgress(progress);
			}
		});
	}

	public void unzip(File zipFile, File targetDirectory) throws IOException {
		ZipInputStream zis = new ZipInputStream(
				new BufferedInputStream(new FileInputStream(zipFile)));
		try {
			ZipEntry ze;
			int count;
			byte[] buffer = new byte[8192];
			while ((ze = zis.getNextEntry()) != null) {
				File file = new File(targetDirectory, ze.getName());
				File dir = ze.isDirectory() ? file : file.getParentFile();
				if (!dir.isDirectory() && !dir.mkdirs())
					throw new FileNotFoundException("Failed to ensure directory: " +
							dir.getAbsolutePath());
				if (ze.isDirectory())
					continue;
				FileOutputStream fout = new FileOutputStream(file);
				try {
					while ((count = zis.read(buffer)) != -1)
						fout.write(buffer, 0, count);
				} finally {
					fout.close();
				}
        /* if time should be restored as well
        long time = ze.getTime();
        if (time > 0)
            file.setLastModified(time);
        */
			}

			zipFile.delete();
		} finally {
			zis.close();
		}
	}

	private void downloadInLocalFile() {
		File dir = new File(Environment.getExternalStorageDirectory() + "/photos/photos/photo/");
		final File file = new File(dir, "image1.jpg");
		try {
			if (!dir.exists()) {
				dir.mkdir();
			}
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}

		final FileDownloadTask fileDownloadTask = imageRef.getFile(file);
		Helper.initProgressDialog(this);
		Helper.mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {
				fileDownloadTask.cancel();
			}
		});
		Helper.mProgressDialog.show();

		fileDownloadTask.addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
			@Override
			public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
				Helper.dismissProgressDialog();
				mTextView.setText(file.getPath());
				mImageView.setImageURI(Uri.fromFile(file));
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissProgressDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		}).addOnProgressListener(new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
			@Override
			public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
				int progress = (int) ((100 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount());
				Helper.setProgress(progress);
			}
		});
	}

	private void downloadDataViaUrl() {
		Helper.showDialog(this);
		imageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
			@Override
			public void onSuccess(Uri uri) {
				Helper.dismissDialog();
				mTextView.setText(uri.toString());
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		});
	}

	private void getMetadata() {
		Helper.showDialog(this);
		imageRef.getMetadata().addOnSuccessListener(new OnSuccessListener<StorageMetadata>() {
			@Override
			public void onSuccess(StorageMetadata storageMetadata) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Country: %s", storageMetadata.getCustomMetadata("country")));
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		});
	}

	private void updateMetaData() {
		Helper.showDialog(this);
		StorageMetadata metadata = new StorageMetadata.Builder().setCustomMetadata("country", "Thailand").build();
		imageRef.updateMetadata(metadata).addOnSuccessListener(new OnSuccessListener<StorageMetadata>() {
			@Override
			public void onSuccess(StorageMetadata storageMetadata) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Country: %s", storageMetadata.getCustomMetadata("country")));
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		});
	}

	private void deleteFile() {
		Helper.showDialog(this);
		imageRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
			@Override
			public void onSuccess(Void aVoid) {
				Helper.dismissDialog();
				mImageView.setImageDrawable(null);
				mTextView.setText(String.format("%s was deleted.", imageRef.getPath()));
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				Helper.dismissDialog();
				mTextView.setText(String.format("Failure: %s", exception.getMessage()));
			}
		});
	}
}