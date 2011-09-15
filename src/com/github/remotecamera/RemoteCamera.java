package com.github.remotecamera;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;

public class RemoteCamera extends Activity {

  private Camera camera = null;
  private Camera.PictureCallback pictureCallback = null;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);

      try {
        camera = Camera.open();
        pictureCallback = new Camera.PictureCallback() {
          public void onPictureTaken(byte[] imageTaken, Camera camera) {
            System.out.println("asljkfalsdjkfaslkdjf");
          }
        };
        camera.takePicture(null, null, pictureCallback);
      } catch ( Exception ex ) {
      }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (camera != null) {
      camera.release();
    }
  }

    
}
