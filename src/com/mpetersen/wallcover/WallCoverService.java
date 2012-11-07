package com.mpetersen.wallcover;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

public class WallCoverService extends WallpaperService {

	@Override
	public Engine onCreateEngine() {
		return new WallCoverEngine();
	}

	private class WallCoverEngine extends Engine implements
			SharedPreferences.OnSharedPreferenceChangeListener {
		public static final String DEBUG_NAME = "WallCover";

		public static final String SHARED_PREFS_NAME = "preferences";
		private SharedPreferences prefs;

		private final Handler handler = new Handler();
		private final Runnable drawRunner = new Runnable() {
			public void run() {
				draw();
			}
		};

		private final Runnable updateRunner = new Runnable() {
			public void run() {
				update();
			}
		};

		private final FilenameFilter textFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				String lowercaseName = name.toLowerCase();
				if (lowercaseName.endsWith(".jpg")
						|| lowercaseName.endsWith(".jpeg")) {
					return true;
				} else {
					return false;
				}
			}
		};

		private boolean visible;
		private Paint paint = new Paint();
		private Paint paintBlack = new Paint();
		
		/* Engine State */
		private boolean isReady = false;
		private boolean hasImage = false;
		private Bitmap currentImage;

		/* Related to drawing cache */
		private float offset = 0;
		private int moveLength = 0;
		private int width;
		private int height;
		private int cacheWidth = 0;
		private int cacheHeight = 0;

		/* Loading multiple images. */
		private int currentImageIndex = 0;
		private int imageCount = 0;
		private int screenCount = 5;
		private List<String> images = new ArrayList<String>();
		private boolean isMulti;
		private boolean isPerScreen;
		private boolean cached;
		private int currentUpdateScreen = 0;

		public WallCoverEngine() {

			prefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
			prefs.registerOnSharedPreferenceChangeListener(this);
			isMulti = prefs.getBoolean("enable_multi", true);
			isPerScreen = prefs.getBoolean("enable_per_screen", true);

			paint.setAntiAlias(true);
			paint.setColor(Color.WHITE);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeJoin(Paint.Join.ROUND);
			paint.setStrokeWidth(10f);

			paintBlack.setAntiAlias(true);
			paintBlack.setColor(Color.BLACK);
			paintBlack.setStyle(Paint.Style.FILL);
		}

		public void onSharedPreferenceChanged(SharedPreferences prefs,
				String key) {
			Log.d(DEBUG_NAME, "Key: " + key);
			boolean updateEngine = false;
			if (key.equals("enable_multi")) {
				isMulti = prefs.getBoolean("enable_multi", true);
				updateEngine = true;
			}

			if (key.equals("enable_per_screen")) {

				isPerScreen = prefs.getBoolean("enable_per_screen", true);
				updateEngine = true;
			}

			if (key.equals("image_filepath")) {
				updateEngine = true;
			}

			if (updateEngine) {
				Toast.makeText(getBaseContext(), "Search Backgrounds",
						Toast.LENGTH_LONG).show();
				String filepath = prefs.getString("image_filepath", "");
				search(filepath);
				update();
			}
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			this.visible = visible;
			if (visible) {
				handler.post(drawRunner);
			} else {
				handler.removeCallbacks(drawRunner);
			}
		}

		@Override
		public void onSurfaceDestroyed(SurfaceHolder holder) {
			Log.d(DEBUG_NAME, "Destroy Surface");
			super.onSurfaceDestroyed(holder);
			this.visible = false;
			handler.removeCallbacks(drawRunner);
			handler.removeCallbacks(updateRunner);
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {
			this.width = width;
			this.height = height;
			super.onSurfaceChanged(holder, format, width, height);
			Log.d(DEBUG_NAME, "onSurfaceChange");
			if (!isReady) {
				Log.d(DEBUG_NAME, "isReady");
				search();
				isReady = true;
			}
			onChangeSize();
			handler.post(drawRunner);
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset,
				float xOffsetStep, float yOffsetStep, int xPixelOffset,
				int yPixelOffset) {
			offset = xOffset;
			if (this.visible) {
				this.draw();
			}
		}

		private void search() {
			String filepath = prefs.getString("image_filepath", "");
			search(filepath);
		}

		private void search(String filepath) {
			/*
			 * Search for images according to settings.
			 */
			images.clear();
			imageCount = 0;
			currentImageIndex = 0;
			cached = false;
			handler.removeCallbacks(updateRunner);
			if (isMulti) {
				File[] files = (new File(filepath)).getParentFile().listFiles(
						textFilter);
				for (File file : files) {
					Log.d("WallCover", "File added: " + file.getPath());
					images.add(file.getPath());
				}
				imageCount = images.size();
			} else {
				images.add(filepath);
				imageCount = 1;
			}
			Toast.makeText(getBaseContext(), "Found Images - " + imageCount,
					Toast.LENGTH_LONG).show();
			hasImage = false;
			update();
		}

		private void update() {
			// Maintain support for different sort orders here?
			Toast.makeText(getBaseContext(),
					"Update Background - " + currentUpdateScreen,
					Toast.LENGTH_SHORT).show();
			Log.d(DEBUG_NAME, "Cached - " + cached);
			if (isPerScreen) {
				if (cached) {
					createCache(getNextUpdateScreen());
				} else {
					cached = true;
					createCache();
				}
			} else {
				currentImageIndex++;
				if (currentImageIndex >= imageCount) {
					currentImageIndex = 0;
				}
				String imageFilePath = images.get(currentImageIndex);
				loadImage(imageFilePath);
			}
			if (visible){
				draw();				
			}
			if (isMulti) {
				handler.postDelayed(updateRunner, 1000 * getRotationTime());
			}
		}

		private int getNextUpdateScreen() {
			currentUpdateScreen++;
			if (currentUpdateScreen > screenCount) {
				currentUpdateScreen = 0;
			}
			return currentUpdateScreen;
		}

		private void createCache() {
			createCache(-1);
		}

		private Canvas initCache(int w, int h) {
			Log.d(DEBUG_NAME, "Init Cache - " + w + "x" + h);
			cacheWidth = w;
			cacheHeight = h;
			currentImage = Bitmap.createBitmap(cacheWidth, cacheHeight,
					Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(currentImage);
			canvas.drawColor(Color.RED);
			return canvas;
		}

		private void createCache(int screenNumber) {
			// TODO Auto-generated method stub

			float screenRatio = this.width / (float) this.height;

			if (screenNumber < 0) {
				initCache(width * screenCount, height);
			}
			Canvas canvas = new Canvas(this.currentImage);
			// Rect srcRect = new Rect(0, 0, this.width, this.height);
			// Rect destRect = new Rect(0,0,this.width,this.height);
			Log.d(DEBUG_NAME, "Penty O'tool");
			if (screenNumber >= 0) {
				updateScreenCache(screenRatio, canvas, screenNumber);
			} else {
				for (int i = 0; i < screenCount; i++) {
					updateScreenCache(screenRatio, canvas, i);
				}
			}
			moveLength = (screenCount - 1) * this.width;
			hasImage = true;
		}

		private Bitmap updateScreenCache(float screenRatio, Canvas canvas,
				int screenNumber) {
			float imageRatio;
			float scale;
			int imageHeight;
			int imageWidth;
			Bitmap originalImage;
			scale = 1;
			currentImageIndex++;
			if (currentImageIndex >= imageCount) {
				currentImageIndex = 0;
			}
			String imageFilePath = images.get(currentImageIndex);
			Log.d(DEBUG_NAME, currentImageIndex + "/" + imageCount + " - "
					+ imageFilePath + "  " + this.width + "x" + this.height
					+ " " + (this.width * screenNumber));

			originalImage = this.loadBitmap(imageFilePath, this.width,
					this.height);
			imageHeight = originalImage.getHeight();
			imageWidth = originalImage.getWidth();

			imageRatio = imageWidth / (float) imageHeight;
			if (imageRatio > screenRatio) {
				if (imageHeight > this.height) {
					scale = (float) imageHeight / this.height;
				}
			} else {
				if (imageWidth > this.width) {
					scale = (float) imageWidth / this.width;
				}
			}
			int scaledWidthHalf = Math.round(this.width * scale / 2);
			int scaledHeightHalf = Math.round(this.height * scale / 2);

			int srcXCenter = imageWidth / 2;
			int srcYCenter = imageHeight / 2;
			Rect srcRect = new Rect(srcXCenter - scaledWidthHalf, srcYCenter
					- scaledHeightHalf, srcXCenter + scaledWidthHalf,
					srcYCenter + scaledHeightHalf);

			Rect destRect = new Rect(screenNumber * this.width, 0,
					(screenNumber + 1) * this.width, this.height);
			canvas.drawRect(destRect, paintBlack);
			canvas.drawBitmap(originalImage, srcRect, destRect, paint);
			return originalImage;
		}

		private int getBitmapScale(String filepath, int width, int height) {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(filepath, opts);

			int imageWidth = opts.outWidth;
			int imageHeight = opts.outHeight;

			float targetRatio = width / (float) height;
			int sampleScale = 1;

			if ((imageWidth / imageHeight) < targetRatio) {
				if (imageWidth > width) {
					sampleScale = Math.round(imageWidth / width) * sampleScale;
				}
			} else {
				if (imageHeight > height) {
					sampleScale = Math.round(imageHeight / height)
							* sampleScale;
				}
			}
			return sampleScale;
		}

		private Bitmap loadBitmap(String filepath, int width, int height) {
			int sampleScale = getBitmapScale(filepath, width, height);

			BitmapFactory.Options loadOpts = new BitmapFactory.Options();
			loadOpts.inSampleSize = sampleScale;
			Log.d(DEBUG_NAME, "Loading Bitmap: " + filepath + "   Scale: "
					+ sampleScale);
			Bitmap originalImage = BitmapFactory.decodeFile(filepath, loadOpts);
			return originalImage;

		}

		private void onChangeSize() {
			/**
			 * Updates for when either surface or cache sizes change.
			 */
			if (cacheWidth > width) {
				this.moveLength = cacheWidth - this.width;
			} else {
				moveLength = 0;
			}
		}

		private void loadImage(String filepath) {
			// String filepath = prefs.getString("image_filepath","");

			float screenRatio = this.width / (float) this.height;
			float imageRatio;

			float scale = 1;

			// Log.d(DEBUG_NAME, "Loading Bitmap: " + filepath);
			Bitmap originalImage = loadBitmap(filepath, this.width, this.height);
			int imageHeight = originalImage.getHeight();
			int imageWidth = originalImage.getWidth();

			imageRatio = imageWidth / (float) imageHeight;

			if (imageRatio > screenRatio) {
				// if (imageHeight > this.height) {
				scale = this.height / (float) imageHeight;
				// }
			} else {
				scale = this.width / (float) imageWidth;
			}
			int scaledWidth = Math.round(imageWidth * scale);
			int scaledHeight = Math.round(imageHeight * scale);
			Bitmap tmp = Bitmap.createScaledBitmap(originalImage, scaledWidth,
					scaledHeight, false);
			Canvas canvas = initCache(Math.max(width, scaledWidth),
					Math.max(height, scaledHeight));

			int srcy = 0;
			if (scaledHeight < height) {
				srcy = Math.round((height - scaledHeight) / 2);
			}
			canvas.drawBitmap(tmp, 0, srcy, paint);
			tmp.recycle();
			this.hasImage = true;
			onChangeSize();
		}

		private int getRotationTime() {
			return Integer
					.valueOf(prefs.getString("multi_rotation_time", "60"));
		};

		private void draw() {
			SurfaceHolder holder = getSurfaceHolder();
			Canvas canvas = null;
			try {
				canvas = holder.lockCanvas();
				if (canvas != null) {
					/*
					 * Draw something here.
					 */
					if (this.hasImage) {
						this.drawImage(canvas, this.currentImage);
					} else {
						canvas.drawColor(Color.BLACK);
						canvas.drawCircle(this.width / 2, this.height / 2,
								80.0f, paint);
					}
				}
			} finally {
				if (canvas != null)
					holder.unlockCanvasAndPost(canvas);
			}
			handler.removeCallbacks(drawRunner);
		}

		private void drawImage(Canvas canvas, Bitmap bitmap) {
			Rect srcRect = new Rect(Math.round(offset * moveLength), 0, width
					+ Math.round(offset * moveLength), height);
			Rect destRect = new Rect(0, 0, width, height);
			canvas.drawBitmap(bitmap, srcRect, destRect, paint);
		}
	}
}
