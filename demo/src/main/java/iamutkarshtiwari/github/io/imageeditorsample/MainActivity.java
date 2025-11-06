package iamutkarshtiwari.github.io.imageeditorsample;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import iamutkarshtiwari.github.io.ananas.BaseActivity;
import iamutkarshtiwari.github.io.ananas.editimage.EditImageActivity;
import iamutkarshtiwari.github.io.ananas.editimage.ImageEditorIntentBuilder;
import iamutkarshtiwari.github.io.imageeditorsample.imagepicker.utils.FileUtilsKt;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int REQUEST_PERMISSION_STORAGE = 1;
    public static final int ACTION_REQUEST_EDITIMAGE = 9;

    private ImageView imgView;
    private Bitmap mainBitmap;
    private Dialog loadingDialog;

    private int imageWidth, imageHeight;
    private String path;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    ActivityResultLauncher<Intent> editResultLauncher;

    ActivityResultLauncher<PickVisualMediaRequest> pickVisualMedia;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupActivityResultLaunchers();
        initView();
    }

    private void setupActivityResultLaunchers() {
        editResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        handleEditorImage(data);
                    }
                });
        pickVisualMedia = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::copyImageToCache);
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    private void initView() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        imageWidth = metrics.widthPixels;
        imageHeight = metrics.heightPixels;

        imgView = findViewById(R.id.img);

        View selectAlbum = findViewById(R.id.photo_picker);
        View editImage = findViewById(R.id.edit_image);
        selectAlbum.setOnClickListener(this);
        editImage.setOnClickListener(this);

        loadingDialog = BaseActivity.getLoadingDialog(this, iamutkarshtiwari.github.io.ananas.R.string.iamutkarshtiwari_github_io_ananas_loading,
                false);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.edit_image)
            editImageClick();
        else if (v.getId() == R.id.photo_picker)
            selectFromAlbum();
    }

    private void editImageClick() {
        File outputFile = FileUtilsKt.generateEditFile();
        try {
            Intent intent = new ImageEditorIntentBuilder(this, path, outputFile.getAbsolutePath())
                    .withAddText()
                    .withPaintFeature()
                    .withFilterFeature()
                    .withRotateFeature()
                    .withCropFeature()
                    .withBrightnessFeature()
                    .withSaturationFeature()
                    .withBeautyFeature()
                    .withStickerFeature()
                    .withEditorTitle("Photo Editor")
                    .forcePortrait(false)
                    .withoutActionBar()
                    .setSupportActionBarVisibility(false)
                    .build();

            EditImageActivity.start(editResultLauncher, intent, this);
        } catch (Exception e) {
            Toast.makeText(this, iamutkarshtiwari.github.io.ananas.R.string.iamutkarshtiwari_github_io_ananas_not_selected, Toast.LENGTH_SHORT).show();
            Log.e("Demo App", e.getMessage());
        }
    }

    private void selectFromAlbum() {
        openAlbum();
    }

    private void openAlbum() {
        pickVisualMedia.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void copyImageToCache(Uri imageUri) {
        // Use an ExecutorService for background work
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 1. Get an InputStream from the URI
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    return;
                }

                // 2. Create the destination file in cachesDir
                File cacheDir = getCacheDir();
                File destFile = new File(cacheDir, "picked_image_" + System.currentTimeMillis() + ".jpg");

                // 3. Copy the data from the input stream to the output stream
                OutputStream outputStream = new FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }

                // 4. Close the streams
                inputStream.close();
                outputStream.close();

                // At this point, destFile contains a copy of the image.
                // You can now use destFile.getAbsolutePath() or similar.
                // Run UI updates on the main thread
                runOnUiThread(() -> {
                    Toast.makeText(this, "Image copied to cache: " + destFile.getName(), Toast.LENGTH_LONG).show();
                    // Load Image and set path for Edit Image button
                    path = destFile.getAbsolutePath();
                    loadImage(path);
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleEditorImage(Intent data) {
        String newFilePath = data.getStringExtra(ImageEditorIntentBuilder.OUTPUT_PATH);
        boolean isImageEdit = data.getBooleanExtra(EditImageActivity.IS_IMAGE_EDITED, false);

        if (isImageEdit) {
            Toast.makeText(this, getString(R.string.ananas_image_editor_save_path, newFilePath), Toast.LENGTH_LONG).show();
        } else {
            newFilePath = data.getStringExtra(ImageEditorIntentBuilder.SOURCE_PATH);
        }

        loadImage(newFilePath);
    }

    private void loadImage(String imagePath) {
        Disposable applyRotationDisposable = loadBitmapFromFile(imagePath)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(subscriber -> loadingDialog.show())
                .doFinally(() -> loadingDialog.dismiss())
                .subscribe(
                        this::setMainBitmap,
                        e -> { e.printStackTrace();
                            Toast.makeText(
                                this, iamutkarshtiwari.github.io.ananas.R.string.iamutkarshtiwari_github_io_ananas_load_error, Toast.LENGTH_SHORT).show();}
                );

        compositeDisposable.add(applyRotationDisposable);
    }

    private void setMainBitmap(Bitmap sourceBitmap) {
        if (mainBitmap != null) {
            mainBitmap.recycle();
            mainBitmap = null;
            System.gc();
        }
        mainBitmap = sourceBitmap;
        imgView.setImageBitmap(mainBitmap);
    }

    private Single<Bitmap> loadBitmapFromFile(String filePath) {
        return Single.fromCallable(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            return BitmapFactory.decodeFile(filePath, options);
        }
        );
    }
}
